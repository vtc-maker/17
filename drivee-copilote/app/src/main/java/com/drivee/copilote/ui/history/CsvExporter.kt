package com.drivee.copilote.ui.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.drivee.copilote.data.local.entity.RideEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export CSV de l'historique des courses (compta / suivi hebdomadaire).
 *
 * Format pensé pour Excel FR :
 *  - séparateur ';'
 *  - décimales à la virgule
 *  - BOM UTF-8 pour l'accentuation correcte à l'ouverture dans Excel
 *
 * Le fichier est écrit dans le cache puis partagé via FileProvider (aucune
 * permission de stockage requise).
 */
object CsvExporter {

    private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val FILE_FMT = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE)

    private val HEADER = listOf(
        "Date",
        "Plateforme",
        "Prix brut (€)",
        "Distance approche (km)",
        "Temps approche (min)",
        "Distance course (km)",
        "Temps course (min)",
        "€/km net",
        "€/h net",
        "Bénéfice net (€)",
        "Score"
    )

    /** Construit le contenu CSV pour une liste de courses. */
    fun buildCsv(rides: List<RideEntity>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM UTF-8 pour Excel
        sb.append(HEADER.joinToString(";")).append("\r\n")
        rides.forEach { r ->
            val row = listOf(
                DATE_FMT.format(Date(r.capturedAtEpochMs)),
                platformLabel(r.sourcePackage),
                num(r.grossPriceEuros),
                num(r.approachDistanceKm),
                num(r.approachTimeMin),
                num(r.rideDistanceKm),
                num(r.rideTimeMin),
                num(r.netPricePerKmEuros),
                num(r.netHourlyRateEuros),
                num(r.netProfitEuros),
                scoreLabel(r.score)
            )
            sb.append(row.joinToString(";")).append("\r\n")
        }
        return sb.toString()
    }

    /**
     * Écrit le CSV dans le cache et renvoie un Intent de partage prêt à lancer.
     * Renvoie null si la liste est vide.
     */
    fun createShareIntent(context: Context, rides: List<RideEntity>): Intent? {
        if (rides.isEmpty()) return null

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "vtc_copilot_courses_${FILE_FMT.format(Date())}.csv")
        file.writeText(buildCsv(rides), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Export des courses VTC Copilot")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun num(value: Double?): String =
        value?.let { String.format(Locale.FRANCE, "%.2f", it) } ?: ""

    private fun platformLabel(pkg: String): String = when (pkg) {
        "com.ubercab.driver" -> "Uber"
        "ee.mtakso.driver" -> "Bolt"
        "com.heetch.driver" -> "Heetch"
        else -> pkg
    }

    private fun scoreLabel(score: String): String = when (score) {
        "GREEN" -> "Super"
        "ORANGE" -> "Moyen"
        "RED" -> "Mauvais"
        else -> "Inconnu"
    }
}
