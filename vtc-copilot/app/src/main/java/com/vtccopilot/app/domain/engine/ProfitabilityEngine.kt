package com.vtccopilot.app.domain.engine

import com.vtccopilot.app.domain.model.ProfitabilityMetrics
import com.vtccopilot.app.domain.model.ProfitabilityScore
import com.vtccopilot.app.domain.model.RideOffer
import com.vtccopilot.app.domain.model.VehicleSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⭐ MOTEUR DE RENTABILITÉ — logique métier PURE (aucune dépendance Android).
 *
 * Affichage pop-up (sur le prix affiché, approche + course incluses) :
 *   €/km = prix / distanceTotaleKm
 *   €/h  = prix / (dureeTotaleMin / 60)
 *
 * Verdict : ratio = min(€/km ÷ seuil €/km , €/h ÷ seuil €/h)
 *   ratio ≥ 1                → SUPER   (GREEN)
 *   ratio ≥ zone MOYEN (80%) → MOYEN   (ORANGE)
 *   sinon                    → MAUVAIS (RED)
 *
 * Historique (bénéfice réel) :
 *   encaissé = prix × (1 − commission%) ; charges = encaissé × charges% ;
 *   coût véhicule = km × coût/km ; net = encaissé − charges − coût véhicule.
 */
@Singleton
class ProfitabilityEngine @Inject constructor() {

    fun compute(offer: RideOffer, settings: VehicleSettings): ProfitabilityMetrics {
        val gross = offer.grossPriceEuros
        val totalKm = offer.totalDistanceKm
        val totalMin = offer.totalTimeMin

        if (gross == null || gross <= 0.0) {
            return ProfitabilityMetrics(
                grossHourlyRateEuros = null,
                netHourlyRateEuros = null,
                grossPricePerKmEuros = null,
                netPricePerKmEuros = null,
                netProfitEuros = null,
                totalDistanceKm = totalKm,
                totalTimeMin = totalMin,
                score = ProfitabilityScore.UNKNOWN
            )
        }

        val commissionFactor =
            if (settings.priceIsNetOfCommission) 1.0
            else (1.0 - settings.platformCommissionPercent.coerceIn(0.0, 100.0) / 100.0)
        val collectedRevenue = gross * commissionFactor
        val socialCharges = collectedRevenue * settings.socialChargesPercent.coerceAtLeast(0.0) / 100.0
        val vehicleCost = (totalKm ?: 0.0) * settings.costPerKmEuros.coerceAtLeast(0.0)
        val netProfit = collectedRevenue - socialCharges - vehicleCost

        val hours = totalMin?.takeIf { it > 0.0 }?.div(60.0)
        val grossHourly = hours?.let { gross / it }
        val netHourly = hours?.let { netProfit / it }

        val km = totalKm?.takeIf { it > 0.0 }
        val grossPerKm = km?.let { gross / it }
        val netPerKm = km?.let { netProfit / it }

        val ratio = thresholdRatio(grossPerKm, grossHourly, settings)

        return ProfitabilityMetrics(
            grossHourlyRateEuros = grossHourly?.round2(),
            netHourlyRateEuros = netHourly?.round2(),
            grossPricePerKmEuros = grossPerKm?.round2(),
            netPricePerKmEuros = netPerKm?.round2(),
            netProfitEuros = netProfit.round2(),
            totalDistanceKm = totalKm?.round2(),
            totalTimeMin = totalMin?.round2(),
            score = scoreFrom(ratio, settings),
            thresholdRatio = ratio
        )
    }

    /** Le plus mauvais des deux ratios disponibles (€/km et €/h). */
    private fun thresholdRatio(perKm: Double?, hourly: Double?, s: VehicleSettings): Double? {
        val kmRatio = perKm?.let { v -> s.perKmThresholdEuros.takeIf { it > 0.0 }?.let { v / it } }
        val hRatio = hourly?.let { v -> s.hourlyThresholdEuros.takeIf { it > 0.0 }?.let { v / it } }
        return listOfNotNull(kmRatio, hRatio).minOrNull()
    }

    private fun scoreFrom(ratio: Double?, s: VehicleSettings): ProfitabilityScore {
        if (ratio == null) return ProfitabilityScore.UNKNOWN
        val medium = s.mediumThresholdPercent.coerceIn(0.0, 100.0) / 100.0
        return when {
            ratio >= 1.0 -> ProfitabilityScore.GREEN
            ratio >= medium -> ProfitabilityScore.ORANGE
            else -> ProfitabilityScore.RED
        }
    }

    private fun Double.round2(): Double = Math.round(this * 100.0) / 100.0
}
