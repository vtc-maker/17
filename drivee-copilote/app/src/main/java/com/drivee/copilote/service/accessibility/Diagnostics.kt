package com.drivee.copilote.service.accessibility

import com.drivee.copilote.domain.model.Platforms
import com.drivee.copilote.domain.model.RideOffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de diagnostic en mémoire (rien n'est envoyé en ligne) :
 * événements reçus, dernière lecture par les NŒUDS d'accessibilité et par l'OCR,
 * textes lus et résultat. Partageable depuis le tableau de bord.
 */
object Diagnostics {

    class Capture(val at: Long, val pkg: String?, val windows: Int, val texts: List<String>, val result: String)

    private val eventCounts = mutableMapOf<String, Int>()
    private val anchor = Regex("(?i)accepter|refuser|\\baccept")

    @Volatile var lastNodes: Capture? = null; private set
    @Volatile var lastOcr: Capture? = null; private set
    /** Dernière capture (nœuds ou OCR) où un bouton Accepter/Refuser était visible. */
    @Volatile var lastOfferLike: Capture? = null; private set
    @Volatile var lastDetected: String? = null; private set
    @Volatile var lastDecision: String? = null; private set

    fun onDecision(offer: RideOffer, decision: String) {
        val label = when (decision) {
            "ACCEPTED" -> "PRISE"
            "PENDING" -> "À VALIDER"
            else -> "PAS PRISE"
        }
        lastDecision = "${time(System.currentTimeMillis())} ${offer.grossPriceEuros} € → $label"
        log("Décision : ${offer.grossPriceEuros} € → $label")
    }

    /** Journal chronologique (200 dernières lignes), inclus dans le fichier partagé. */
    private val journal = ArrayDeque<String>()

    @Synchronized
    fun log(message: String) {
        journal.addLast("${time(System.currentTimeMillis())} $message")
        while (journal.size > 200) journal.removeFirst()
    }

    @Synchronized
    private fun journalLines(): List<String> = journal.toList()

    /**
     * Résumé affiché à l'écran : uniquement des compteurs et heures, AUCUN prix / km / "Accepter",
     * pour que l'OCR ne puisse jamais prendre l'écran de VTC Copilot pour une offre.
     */
    fun shortSummary(serviceRunning: Boolean, ocrSupported: Boolean): String =
        "Service : ${if (serviceRunning) "actif" else "inactif"} · OCR : ${if (ocrSupported) "oui" else "non (Android < 11)"}\n" +
            "Événements reçus : ${eventSummary()}\n" +
            "Dernière analyse : ${(lastOcr ?: lastNodes)?.let { time(it.at) } ?: "—"}\n" +
            "Lignes dans le journal : ${journalLines().size}"

    @Synchronized
    fun onEvent(pkg: String) {
        eventCounts[pkg] = (eventCounts[pkg] ?: 0) + 1
    }

    @Synchronized
    fun eventSummary(): String =
        if (eventCounts.isEmpty()) "aucun"
        else eventCounts.entries.joinToString(" · ") { "${Platforms.label(it.key)} ${it.value}" }

    fun onAnalysis(source: String, pkg: String?, windows: Int, texts: List<String>, offer: RideOffer?) {
        val result = when {
            pkg == null -> "aucune fenêtre lisible"
            offer == null -> "pas d'offre (${texts.size} textes)"
            else -> "OFFRE ${offer.grossPriceEuros} € · ${offer.totalDistanceKm ?: "?"} km · ${offer.totalTimeMin ?: "?"} min"
        }
        val capture = Capture(System.currentTimeMillis(), pkg, windows, texts, "$source : $result")
        if (source == "OCR") lastOcr = capture else lastNodes = capture
        if (texts.any { anchor.containsMatchIn(it) }) lastOfferLike = capture
        if (offer != null && lastDetected != "${time(capture.at)} ${capture.result}") {
            lastDetected = "${time(capture.at)} ${capture.result}"
            log("Détection ${capture.result}")
        }
    }

    fun summary(serviceRunning: Boolean, ocrSupported: Boolean): String =
        "Service : ${if (serviceRunning) "actif" else "inactif"} · OCR : ${if (ocrSupported) "oui" else "non (Android < 11)"}\n" +
            "Événements reçus : ${eventSummary()}\n" +
            "Nœuds : ${lastNodes?.result ?: "—"}\n" +
            "OCR : ${lastOcr?.result ?: "—"}\n" +
            "Dernière offre détectée : ${lastDetected ?: "aucune"}\n" +
            "Dernière décision : ${lastDecision ?: "—"}"

    fun report(serviceRunning: Boolean, ocrSupported: Boolean): String = buildString {
        appendLine("VTC Copilot — diagnostic")
        appendLine(summary(serviceRunning, ocrSupported))
        listOf("OFFRE (Accepter visible)" to lastOfferLike, "NŒUDS" to lastNodes, "OCR" to lastOcr).forEach { (title, c) ->
            appendLine()
            appendLine("--- $title ${c?.let { "${time(it.at)} · ${it.windows} fenêtre(s) · ${it.result}" } ?: "—"} ---")
            c?.texts?.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("--- JOURNAL ---")
        journalLines().forEach { appendLine(it) }
    }

    private fun time(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(ms))
}
