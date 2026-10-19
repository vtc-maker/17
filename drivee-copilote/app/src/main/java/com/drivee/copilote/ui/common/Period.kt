package com.drivee.copilote.ui.common

import java.util.Calendar
import java.util.Locale

/** Période d'affichage commune à l'historique et aux statistiques. */
enum class Period(val label: String) {
    TODAY("Aujourd'hui"),
    WEEK("Semaine"),
    MONTH("Mois"),
    ALL("Tout"),
    CUSTOM("Dates");

    /** Intervalle [début, fin) en epoch ms. Borne haute ouverte pour voir les nouvelles courses en direct. */
    fun range(customFrom: Long? = null, customTo: Long? = null): Pair<Long, Long> = when (this) {
        TODAY -> startOfDay(System.currentTimeMillis()) to Long.MAX_VALUE
        WEEK -> startOfWeek() to Long.MAX_VALUE
        MONTH -> startOfMonth() to Long.MAX_VALUE
        ALL -> 0L to Long.MAX_VALUE
        CUSTOM -> (customFrom?.let { startOfDay(it) } ?: 0L) to
            (customTo?.let { startOfDay(it) + DAY_MS } ?: Long.MAX_VALUE)
    }

    companion object {
        private const val DAY_MS = 24L * 3600 * 1000

        fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun startOfWeek(): Long = Calendar.getInstance(Locale.FRANCE).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun startOfMonth(): Long = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

/** Nom affiché de la plateforme à partir du package Android. */
fun platformName(pkg: String): String = when (pkg) {
    "com.ubercab.driver" -> "Uber"
    "ee.mtakso.driver" -> "Bolt"
    "com.heetch.driver" -> "Heetch"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** Note 0-100 affichée dans le badge (même échelle que la jauge : seuil rentable = 60). */
fun rideScore100(thresholdRatio: Double?): Int? =
    thresholdRatio?.let { (it * 60.0).toInt().coerceIn(0, 99) }

/** Distance totale (approche + course) d'une course enregistrée. */
fun totalKm(approach: Double?, ride: Double?): Double = (approach ?: 0.0) + (ride ?: 0.0)
