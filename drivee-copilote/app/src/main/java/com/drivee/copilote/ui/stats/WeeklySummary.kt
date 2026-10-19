package com.drivee.copilote.ui.stats

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.drivee.copilote.ui.history.CsvExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Génère un récapitulatif hebdomadaire prêt à envoyer (au comptable) en un tap.
 * Produit un texte lisible + joint le CSV de la semaine.
 */
object WeeklySummary {

    private val FILE_FMT = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)

    fun buildText(state: StatsUiState): String = buildString {
        appendLine("Récap VTC Copilot — semaine ${state.weekRangeLabel}")
        appendLine("──────────────────────────")
        appendLine("Courses : ${state.weekRideCount}")
        appendLine("Revenu net : ${euro(state.weekTotalNet)}")
        appendLine("Moyenne : ${state.weekAvgHourly?.let { perHour(it) } ?: "—"}")
        appendLine("Distance totale : ${String.format(Locale.FRANCE, "%.0f km", state.weekDistanceKm)}")
        appendLine()
        appendLine("Détail par jour :")
        state.currentWeekDays.forEach { d ->
            appendLine("• ${d.label} : ${euro(d.netRevenue)} (${d.rideCount} course(s))")
        }
        appendLine()
        appendLine("Généré par VTC Copilot.")
    }

    /**
     * Intent de partage : texte du récap + pièce jointe CSV de la semaine.
     * Renvoie null si la semaine ne contient aucune course.
     */
    fun buildShareIntent(context: Context, state: StatsUiState): Intent? {
        if (state.currentWeekRides.isEmpty()) return null

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "vtc_copilot_semaine_${FILE_FMT.format(Date())}.csv")
        file.writeText(CsvExporter.buildCsv(state.currentWeekRides), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Récap VTC Copilot — semaine ${state.weekRangeLabel}")
            putExtra(Intent.EXTRA_TEXT, buildText(state))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun euro(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)
    private fun perHour(v: Double) = String.format(Locale.FRANCE, "%.1f €/h", v)
}
