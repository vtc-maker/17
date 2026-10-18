package com.drivee.copilote.service.accessibility

import com.drivee.copilote.domain.model.Platforms
import com.drivee.copilote.domain.model.RideOffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de diagnostic en mémoire (rien n'est envoyé en ligne) :
 * événements reçus par application, dernière analyse, textes lus et résultat.
 * Affiché sur le tableau de bord pour comprendre pourquoi une offre n'est pas détectée.
 */
object Diagnostics {

    private val eventCounts = mutableMapOf<String, Int>()

    @Volatile var lastAnalysisAt: Long = 0L; private set
    @Volatile var lastPackage: String? = null; private set
    @Volatile var lastWindowCount: Int = 0; private set
    @Volatile var lastTexts: List<String> = emptyList(); private set
    @Volatile var lastResult: String = "Aucune analyse pour l'instant"; private set

    /** Dernière capture qui contenait un bouton Accepter/Refuser (la plus utile à partager). */
    @Volatile var lastOfferLikeTexts: List<String> = emptyList(); private set

    @Synchronized
    fun onEvent(pkg: String) {
        eventCounts[pkg] = (eventCounts[pkg] ?: 0) + 1
    }

    @Synchronized
    fun eventSummary(): String =
        if (eventCounts.isEmpty()) "aucun"
        else eventCounts.entries.joinToString(" · ") { "${Platforms.label(it.key)} ${it.value}" }

    fun onAnalysis(pkg: String?, windows: Int, texts: List<String>, offer: RideOffer?) {
        lastAnalysisAt = System.currentTimeMillis()
        lastPackage = pkg
        lastWindowCount = windows
        lastTexts = texts
        if (texts.any { Regex("(?i)accepter|refuser|\\baccept").containsMatchIn(it) }) {
            lastOfferLikeTexts = texts
        }
        lastResult = when {
            pkg == null -> "Aucune fenêtre Uber/Bolt/Heetch lisible"
            offer == null -> "${Platforms.label(pkg)} : pas d'offre reconnue (${texts.size} textes lus)"
            else -> "${Platforms.label(pkg)} : offre ${offer.grossPriceEuros} € · " +
                "${offer.totalDistanceKm ?: "?"} km · ${offer.totalTimeMin ?: "?"} min"
        }
    }

    /** Rapport texte complet, à partager. */
    fun report(serviceRunning: Boolean): String = buildString {
        val time = if (lastAnalysisAt == 0L) "—"
        else SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(lastAnalysisAt))
        appendLine("Drivee Copilote — diagnostic")
        appendLine("Service actif : ${if (serviceRunning) "oui" else "NON"}")
        appendLine("Événements reçus : ${eventSummary()}")
        appendLine("Dernière analyse : $time (${lastWindowCount} fenêtre(s))")
        appendLine("Résultat : $lastResult")
        appendLine()
        appendLine("--- Textes de la dernière offre (Accepter/Refuser visible) ---")
        lastOfferLikeTexts.forEach { appendLine(it) }
        appendLine()
        appendLine("--- Textes de la dernière analyse ---")
        lastTexts.forEach { appendLine(it) }
    }
}
