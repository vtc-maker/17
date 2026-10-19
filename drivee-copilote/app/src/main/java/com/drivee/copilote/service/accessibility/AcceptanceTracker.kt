package com.drivee.copilote.service.accessibility

/**
 * Détermine si une offre affichée a été ACCEPTÉE ou non, pour n'enregistrer dans
 * l'historique que les courses réellement prises.
 *
 * Signaux utilisés (du plus fiable au moins fiable) :
 *  1. clic sur "Accepter" / "Refuser" (événement d'accessibilité, si l'app l'expose) ;
 *  2. après la disparition de l'offre, l'écran suivant :
 *     - écran de prise en charge ("Récupérer", "Je suis arrivé", "Commencer la course"…) → ACCEPTÉE
 *     - retour à l'attente ("Vous êtes en ligne", "Passer hors ligne"…) → REFUSÉE
 *  3. aucune preuve après [timeoutMs], ou nouvelle offre entre-temps → À VALIDER (le chauffeur tranche).
 *
 * Même offre ré-affichée (lecture momentanément ratée) = pas une nouvelle offre.
 * Une offre déjà décidée n'est jamais comptée deux fois.
 */
class AcceptanceTracker<T>(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 30_000L,
    private val idleGraceMs: Long = 2_500L
) {
    enum class Decision { ACCEPTED, REJECTED, PENDING }

    data class Result<T>(val item: T, val decision: Decision)

    private class Slot<T>(val key: String, val item: T, var goneAt: Long = 0L)

    private var current: Slot<T>? = null
    private var awaiting: Slot<T>? = null
    private val decidedKeys = ArrayDeque<String>()

    companion object {
        private val ACCEPT_CLICK = Regex("(?i)\\b(accepter|accept|répondre|repondre)\\b")
        private val REFUSE_CLICK = Regex("(?i)\\b(refuser|decline|ignorer|rejeter)\\b")

        /** Écran affiché UNIQUEMENT après avoir accepté (trajet vers le client / course). */
        private val ACCEPTED_SCREEN = Regex(
            "(?i)récupérer|recuperer|en route vers|je suis arrivé|vous êtes arrivé|arrivé au point|" +
                "commencer la course|démarrer la course|commencer le trajet|terminer la course|" +
                "annuler la course|appeler le passager|appeler le client|contacter le passager|" +
                "message au passager|envoyer un message|pick up|picking up|arrived|start trip|swipe to start"
        )

        /** Écran d'attente (aucune course en cours). */
        private val IDLE_SCREEN = Regex(
            "(?i)vous êtes en ligne|passer hors ligne|recherche de courses|recherche de clients|" +
                "en attente de courses|offre expirée|demande expirée|vous avez refusé|you're online|finding trips"
        )
    }

    /**
     * Une offre est visible à l'écran. L'offre précédente déjà disparue sans preuve → À VALIDER.
     * Une offre encore "visible" remplacée par une autre lecture = mauvaise lecture → ignorée.
     */
    @Synchronized
    fun offerVisible(key: String, item: T): Result<T>? {
        if (key in decidedKeys) return null
        if (current?.key == key) return null
        val aw = awaiting
        if (aw != null && aw.key == key) { // même offre, lecture momentanément ratée
            current = aw
            awaiting = null
            return null
        }
        current?.let { decide(it, Decision.REJECTED) } // ancienne lecture remplacée : ignorée
        awaiting = null
        current = Slot(key, item)
        return aw?.let { decide(it, Decision.PENDING) }
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

    private fun decide(slot: Slot<T>, decision: Decision): Result<T> {
        decidedKeys.addLast(slot.key)
        while (decidedKeys.size > 20) decidedKeys.removeFirst()
        return Result(slot.item, decision)
    }
}
