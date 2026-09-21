package com.drivee.copilote.domain.model

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
    val capturedAtEpochMs: Long = System.currentTimeMillis()
) {
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
