package com.vtccopilot.app.domain.model

/**
 * Offre de course brute, telle qu'extraite de l'écran de l'application VTC.
 *
 * Toutes les valeurs numériques sont facultatives : le parsing peut échouer
 * partiellement (l'UI de l'app VTC évolue). Le moteur de rentabilité gère
 * les valeurs manquantes de façon défensive.
 *
 * Unités :
 *  - [grossPriceEuros]        : euros (€)
 *  - [approachDistanceKm]     : kilomètres jusqu'au client
 *  - [approachTimeMin]        : minutes jusqu'au client
 *  - [rideDistanceKm]         : kilomètres de la course (client -> destination)
 *  - [rideTimeMin]            : minutes de la course
 */
data class RideOffer(
    val sourcePackage: String,
    val grossPriceEuros: Double? = null,
    val approachDistanceKm: Double? = null,
    val approachTimeMin: Double? = null,
    val rideDistanceKm: Double? = null,
    val rideTimeMin: Double? = null,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null,
    val rawTexts: List<String> = emptyList(),
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    /** true si une durée manquante a été estimée à partir de la distance (ex : Bolt n'affiche pas la durée de course). */
    val timeEstimated: Boolean = false
) {
    /**
     * Complète les durées absentes à partir des distances et d'une vitesse moyenne.
     * Indispensable pour Bolt, qui n'affiche que la distance de la course.
     */
    fun withEstimatedTimes(avgSpeedKmh: Double): RideOffer {
        if (avgSpeedKmh <= 0.0) return this
        val estRide = if (rideTimeMin == null && rideDistanceKm != null) rideDistanceKm / avgSpeedKmh * 60.0 else null
        val estApproach = if (approachTimeMin == null && approachDistanceKm != null) approachDistanceKm / avgSpeedKmh * 60.0 else null
        if (estRide == null && estApproach == null) return this
        return copy(
            rideTimeMin = rideTimeMin ?: estRide,
            approachTimeMin = approachTimeMin ?: estApproach,
            timeEstimated = true
        )
    }

    /** Distance totale = approche + course (km). */
    val totalDistanceKm: Double?
        get() = combine(approachDistanceKm, rideDistanceKm)

    /** Durée totale = approche + course (min). */
    val totalTimeMin: Double?
        get() = combine(approachTimeMin, rideTimeMin)

    /**
     * Une offre est considérée "valide" (donc affichable en overlay) si l'on
     * dispose au minimum du prix et d'une notion de distance ou de temps.
     */
    val isValid: Boolean
        get() = grossPriceEuros != null && grossPriceEuros > 0.0 &&
                (totalDistanceKm != null || totalTimeMin != null)

    private fun combine(a: Double?, b: Double?): Double? {
        if (a == null && b == null) return null
        return (a ?: 0.0) + (b ?: 0.0)
    }
}
