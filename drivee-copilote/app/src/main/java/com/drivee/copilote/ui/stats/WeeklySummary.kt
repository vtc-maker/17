package com.drivee.copilote.ui.stats

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.drivee.copilote.ui.history.CsvExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Récapitulatif de la période (texte + CSV des courses prises) prêt à envoyer en un tap. */
object WeeklySummary {

    private val FILE_FMT = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)

    fun buildText(state: StatsUiState): String = buildString {
        appendLine("Récap VTC Copilot — ${state.periodLabel}")
        appendLine("──────────────────────────")
        appendLine("Courses prises : ${state.rides.size}")
        appendLine("Chiffre d'affaires : ${euro(state.totalRevenue)}")
        appendLine("Montant moyen : ${state.averageAmount?.let { euro(it) } ?: "—"}")
        appendLine("Taux moyen : ${state.averagePerKm?.let { String.format(Locale.FRANCE, "%.2f €/km", it) } ?: "—"}")
        appendLine("Distance totale : ${String.format(Locale.FRANCE, "%.1f km", state.totalDistanceKm)}")
        appendLine()
        appendLine("Par plateforme :")
        state.platforms.forEach { p -> appendLine("• ${p.name} : ${euro(p.revenue)} (${p.rideCount} course(s))") }
        appendLine()
        appendLine("Généré par VTC Copilot.")
    }

    /** Intent de partage : texte + pièce jointe CSV. Null si aucune course. */
    fun buildShareIntent(context: Context, state: StatsUiState): Intent? {
        if (state.rides.isEmpty()) return null

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "vtc_copilot_recap_${FILE_FMT.format(Date())}.csv")
        file.writeText(CsvExporter.buildCsv(state.rides), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Récap VTC Copilot — ${state.periodLabel}")
            putExtra(Intent.EXTRA_TEXT, buildText(state))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun euro(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)
}
