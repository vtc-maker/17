package com.drivee.copilote.service.overlay

import com.drivee.copilote.domain.model.Platforms
import com.drivee.copilote.domain.model.ProfitabilityMetrics
import com.drivee.copilote.domain.model.ProfitabilityScore
import com.drivee.copilote.domain.model.RideOffer

/** Données prêtes à afficher dans la pop-up. */
data class OfferDisplay(
    val platform: String,
    val perKm: Double?,
    val price: Double,
    val hourly: Double?,
    val minutes: Double?,
    val km: Double?,
    val score: ProfitabilityScore,
    val thresholdRatio: Double?,
    val timeEstimated: Boolean = false
) {
    companion object {
        fun from(offer: RideOffer, metrics: ProfitabilityMetrics) = OfferDisplay(
            platform = Platforms.label(offer.sourcePackage),
            perKm = metrics.grossPricePerKmEuros,
            price = offer.grossPriceEuros ?: 0.0,
            hourly = metrics.grossHourlyRateEuros,
            minutes = offer.totalTimeMin,
            km = offer.totalDistanceKm,
            score = metrics.score,
            thresholdRatio = metrics.thresholdRatio,
            timeEstimated = offer.timeEstimated
        )

        /** Offres de démonstration (bouton "Tester la pop-up"). */
        val demoOffers = listOf(
            RideOffer(Platforms.UBER, grossPriceEuros = 16.0, approachDistanceKm = 2.0, approachTimeMin = 4.0, rideDistanceKm = 7.0, rideTimeMin = 9.0),
            RideOffer(Platforms.HEETCH, grossPriceEuros = 14.0, approachDistanceKm = 3.0, approachTimeMin = 6.0, rideDistanceKm = 12.0, rideTimeMin = 20.0),
            RideOffer(Platforms.BOLT, grossPriceEuros = 10.0, approachDistanceKm = 4.0, approachTimeMin = 8.0, rideDistanceKm = 10.0, rideTimeMin = 16.0)
        )
    }
}
