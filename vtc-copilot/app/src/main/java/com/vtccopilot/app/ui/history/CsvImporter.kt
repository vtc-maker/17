package com.vtccopilot.app.ui.history

import com.vtccopilot.app.data.local.entity.RideEntity
import com.vtccopilot.app.data.local.entity.RideStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Import d'une sauvegarde CSV produite par [CsvExporter] (séparateur ';', décimales à la virgule,
 * BOM UTF-8 éventuel). Les colonnes sont retrouvées par leur NOM d'en-tête : un fichier retouché
 * dans Excel (colonnes déplacées) ou un ancien export (sans Statut → "Prise") reste importable.
 */
object CsvImporter {

    data class Result(val rides: List<RideEntity>, val skipped: Int)

    private val DATE_FORMATS = listOf("dd/MM/yyyy HH:mm", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy")

    fun parse(text: String): Result {
        val lines = text.removePrefix("\uFEFF").lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return Result(emptyList(), 0)

        val sep = if (lines.first().count { it == ';' } >= lines.first().count { it == ',' }) ';' else ','
        val header = split(lines.first(), sep).map { it.trim().lowercase(Locale.FRANCE) }
        fun col(vararg names: String): Int = header.indexOfFirst { h -> names.any { h == it.lowercase(Locale.FRANCE) } }

        val iDate = col("Date")
        val iPlatform = col("Plateforme")
        val iPrice = col("Prix brut (€)", "Prix")
        if (iDate < 0 || iPrice < 0) return Result(emptyList(), lines.size - 1)

        val iApKm = col("Distance approche (km)")
        val iApMin = col("Temps approche (min)")
        val iKm = col("Distance course (km)")
        val iMin = col("Temps course (min)")
        val iNetKm = col("€/km net")
        val iNetH = col("€/h net")
        val iProfit = col("Bénéfice net (€)")
        val iScore = col("Score")
        val iGrossKm = col("€/km brut")
        val iGrossH = col("€/h brut")
        val iRatio = col("Ratio seuil")
        val iStatus = col("Statut")

        var skipped = 0
        val rides = lines.drop(1).mapNotNull { line ->
            val c = split(line, sep)
            fun s(i: Int) = c.getOrNull(i)?.trim().orEmpty()
            fun d(i: Int) = if (i < 0) null else s(i).replace(" ", "").replace("\u00A0", "").replace(',', '.').toDoubleOrNull()

            val date = parseDate(s(iDate))
            val price = d(iPrice)
            if (date == null || price == null) { skipped++; return@mapNotNull null }

            val apKm = d(iApKm); val apMin = d(iApMin); val km = d(iKm); val min = d(iMin)
            val totalKm = (apKm ?: 0.0) + (km ?: 0.0)
            val totalMin = (apMin ?: 0.0) + (min ?: 0.0)
            RideEntity(
                sourcePackage = platformPackage(if (iPlatform >= 0) s(iPlatform) else ""),
                capturedAtEpochMs = date,
                grossPriceEuros = price,
                approachDistanceKm = apKm,
                approachTimeMin = apMin,
                rideDistanceKm = km,
                rideTimeMin = min,
                pickupAddress = null,
                destinationAddress = null,
                netHourlyRateEuros = d(iNetH),
                netPricePerKmEuros = d(iNetKm),
                grossPricePerKmEuros = d(iGrossKm) ?: if (totalKm > 0) price / totalKm else null,
                netProfitEuros = d(iProfit),
                score = scoreCode(if (iScore >= 0) s(iScore) else ""),
                status = statusCode(if (iStatus >= 0) s(iStatus) else ""),
                grossHourlyRateEuros = d(iGrossH) ?: if (totalMin > 0) price / totalMin * 60.0 else null,
                thresholdRatio = d(iRatio)
            )
        }
        return Result(rides, skipped)
    }

    /** Découpe une ligne CSV en gérant les champs entre guillemets. */
    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun parseDate(raw: String): Long? {
        for (f in DATE_FORMATS) {
            runCatching { SimpleDateFormat(f, Locale.FRANCE).apply { isLenient = false }.parse(raw) }
                .getOrNull()?.let { return it.time }
        }
        return null
    }

    private fun platformPackage(label: String): String = when (label.trim().lowercase(Locale.FRANCE)) {
        "uber" -> "com.ubercab.driver"
        "bolt" -> "ee.mtakso.driver"
        "heetch" -> "com.heetch.driver"
        "" -> "inconnu"
        else -> label.trim()
    }

    private fun scoreCode(label: String): String = when (label.trim().lowercase(Locale.FRANCE)) {
        "super", "vert", "green" -> "GREEN"
        "moyen", "orange" -> "ORANGE"
        "mauvais", "rouge", "red" -> "RED"
        else -> "UNKNOWN"
    }

    private fun statusCode(label: String): String = when (label.trim().lowercase(Locale.FRANCE)) {
        "pas prise", "pas pris", "rejected" -> RideStatus.REJECTED
        "à valider", "a valider", "pending" -> RideStatus.PENDING
        else -> RideStatus.ACCEPTED
    }
}
