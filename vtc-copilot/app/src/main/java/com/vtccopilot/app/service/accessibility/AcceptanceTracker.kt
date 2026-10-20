package com.vtccopilot.app.service.accessibility

/**
 * Détermine si une offre affichée a été ACCEPTÉE ou non, et garantit qu'UNE offre réelle
 * ne donne qu'UNE seule ligne d'historique.
 *
 * Signaux utilisés (du plus fiable au moins fiable) :
 *  1. clic sur "Accepter" / "Refuser" (événement d'accessibilité, si l'app l'expose) ;
 *  2. après la disparition de l'offre, l'écran suivant :
 *     - écran de prise en charge ("Récupérer", "Je suis arrivé", "Commencer la course"…) → ACCEPTÉE
 *     - retour à l'attente ("Vous êtes en ligne", "Passer hors ligne"…) → REFUSÉE
 *  3. aucune preuve après [timeoutMs], ou nouvelle offre entre-temps → À VALIDER (le chauffeur tranche).
 *
 * Lectures multiples d'une même offre : l'OCR peut lire le prix différemment d'une image à
 * l'autre (10,03 / 10,55 / 96,00…). [sameOffer] regroupe ces lectures (même distance) et la
 * lecture retenue est celle vue le PLUS SOUVENT (vote), pas la dernière.
 */
class AcceptanceTracker<T>(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 30_000L,
    private val idleGraceMs: Long = 2_500L,
    /** Deux lectures correspondent-elles à la même offre réelle ? */
    private val sameOffer: (T, T) -> Boolean = { a, b -> a == b },
    /** Une offre déjà décidée relue dans cet intervalle est ignorée (pas de doublon). */
    private val decidedMemoryMs: Long = 180_000L,
    /** Offre plus relue depuis ce délai (l'app VTC n'envoie plus d'événements) = disparue. */
    private val staleMs: Long = 12_000L
) {
    enum class Decision { ACCEPTED, REJECTED, PENDING }

    data class Result<T>(val item: T, val decision: Decision)

    private class Slot<T>(key: String, first: T, var lastSeen: Long) {
        var goneAt: Long = 0L
        private val votes = LinkedHashMap<String, Pair<T, Int>>().apply { put(key, first to 1) }
        fun vote(key: String, item: T) {
            val prev = votes[key]
            votes[key] = item to ((prev?.second ?: 0) + 1)
        }
        /** Lecture majoritaire (à égalité : la première vue). */
        val item: T get() = votes.values.maxByOrNull { it.second }!!.first
        val sample: T get() = votes.values.first().first
    }

    private var current: Slot<T>? = null
    private var awaiting: Slot<T>? = null
    private val decided = ArrayDeque<Pair<T, Long>>()

    companion object {
        private val ACCEPT_CLICK = Regex("(?i)\\b(accepter|accept|répondre|repondre)\\b")
        private val REFUSE_CLICK = Regex("(?i)\\b(refuser|decline|ignorer|rejeter)\\b")

        /** Écran affiché UNIQUEMENT après avoir accepté (trajet vers le client / course). */
        private val ACCEPTED_SCREEN = Regex(
            "(?i)récupérer|recuperer|en route vers|je suis arrivé|vous êtes arrivé|arrivé au point|" +
                "commencer la course|démarrer la course|commencer le trajet|terminer la course|" +
                "annuler la course|appeler le passager|appeler le client|contacter le passager|" +
                "message au passager|envoyer un message|pick up|picking up|arrived|start trip|swipe to start|" +
                "prise en charge du passager|glisser pour (arriver|commencer|démarrer)|faites glisser pour|" +
                "je suis là|naviguer vers le passager"
        )

        /** Écran d'attente (aucune course en cours). */
        private val IDLE_SCREEN = Regex(
            "(?i)vous êtes en ligne|passer hors ligne|recherche de courses|recherche de clients|" +
                "en attente de courses|offre expirée|demande expirée|vous avez refusé|you're online|finding trips|" +
                "commande expirée|course expirée|demande annulée|commande annulée|hors ligne|" +
                "en attente de commandes|recherche de commandes|go offline"
        )
    }

    /** Vrai si cette lecture appartient à une offre déjà suivie ou déjà décidée (pas de nouveau son). */
    @Synchronized
    fun isKnown(item: T): Boolean =
        current?.let { sameOffer(it.sample, item) } == true ||
            awaiting?.let { sameOffer(it.sample, item) } == true ||
            recentlyDecided(item)

    /** Meilleure lecture (majoritaire) de l'offre actuellement à l'écran. */
    @Synchronized
    fun currentItem(): T? = current?.item

    /**
     * Une offre est lue à l'écran. Renvoie les décisions éventuelles des offres précédentes
     * (une offre différente arrive → la précédente non tranchée passe À VALIDER).
     */
    @Synchronized
    fun offerVisible(key: String, item: T): List<Result<T>> {
        current?.let { c -> if (sameOffer(c.sample, item)) { c.vote(key, item); c.lastSeen = clock(); return emptyList() } }
        awaiting?.let { aw ->
            if (sameOffer(aw.sample, item)) { // même offre, lecture momentanément ratée
                aw.vote(key, item)
                aw.lastSeen = clock()
                current = aw
                awaiting = null
                return emptyList()
            }
        }
        if (recentlyDecided(item)) return emptyList()
        val results = listOfNotNull(current, awaiting).map { decide(it, Decision.PENDING) }
        awaiting = null
        current = Slot(key, item, clock())
        return results
    }

    /**
     * Minuteur appelé régulièrement, MÊME sans événement de l'app VTC (ex : Bolt reste muet
     * après expiration de l'offre, ou le chauffeur ouvre VTC Copilot). Sans lui, l'offre restait
     * bloquée en mémoire et n'était jamais enregistrée.
     */
    @Synchronized
    fun tick(): Result<T>? {
        val now = clock()
        current?.let { c ->
            if (now - c.lastSeen >= staleMs) {
                c.goneAt = c.lastSeen
                awaiting = c
                current = null
            }
        }
        val aw = awaiting ?: return null
        if (now - aw.goneAt < timeoutMs) return null
        awaiting = null
        return decide(aw, Decision.PENDING)
    }

    /** L'offre n'est plus lue à l'écran : on attend l'écran suivant pour décider. */
    @Synchronized
    fun offerGone() {
        val c = current ?: return
        c.goneAt = clock()
        awaiting = c
        current = null
    }

    /** Clic détecté dans l'app VTC (texte du bouton). */
    @Synchronized
    fun onClick(text: String): Result<T>? {
        val target = current ?: awaiting ?: return null
        val decision = when {
            ACCEPT_CLICK.containsMatchIn(text) -> Decision.ACCEPTED
            REFUSE_CLICK.containsMatchIn(text) -> Decision.REJECTED
            else -> return null
        }
        if (target === current) current = null else awaiting = null
        return decide(target, decision)
    }

    /** Textes de l'écran lus après la disparition de l'offre. */
    @Synchronized
    fun onScreen(texts: List<String>): Result<T>? {
        val aw = awaiting ?: return null
        val elapsed = clock() - aw.goneAt
        val joined = texts.joinToString("\n")
        val decision = when {
            ACCEPTED_SCREEN.containsMatchIn(joined) -> Decision.ACCEPTED
            elapsed >= idleGraceMs && IDLE_SCREEN.containsMatchIn(joined) -> Decision.REJECTED
            elapsed >= timeoutMs -> Decision.PENDING
            else -> return null
        }
        awaiting = null
        return decide(aw, decision)
    }

    private fun recentlyDecided(item: T): Boolean {
        val now = clock()
        while (decided.isNotEmpty() && now - decided.first().second > decidedMemoryMs) decided.removeFirst()
        return decided.any { sameOffer(it.first, item) }
    }

    private fun decide(slot: Slot<T>, decision: Decision): Result<T> {
        decided.addLast(slot.sample to clock())
        while (decided.size > 20) decided.removeFirst()
        return Result(slot.item, decision)
    }
}
