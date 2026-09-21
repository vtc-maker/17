package com.drivee.copilote.domain.engine

import com.drivee.copilote.domain.model.ProfitabilityMetrics
import com.drivee.copilote.domain.model.ProfitabilityScore
import com.drivee.copilote.domain.model.RideOffer
import com.drivee.copilote.domain.model.VehicleSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⭐ MOTEUR DE RENTABILITÉ — logique métier PURE (aucune dépendance Android).
 *
 * Prend en entrée les [VehicleSettings] du chauffeur et une [RideOffer] extraite
 * de l'écran, renvoie un objet [ProfitabilityMetrics] prêt à afficher.
 *
 * Modèle de calcul :
 *   revenuEncaisse   = prixBrut * (1 - commission%)      (si le prix est brut)
 *   charges          = revenuEncaisse * chargesSociales%
 *   coutVehicule     = distanceTotaleKm * coutParKm
 *   beneficeNet      = revenuEncaisse - charges - coutVehicule
 *
 *   €/h brut = prixBrut       / (dureeTotaleMin / 60)
 *   €/h net  = beneficeNet    / (dureeTotaleMin / 60)
 *   €/km     = montant        / distanceTotaleKm   (approche + course incluses)
 *
 * Toutes les divisions sont protégées contre le zéro / null.
 */
@Singleton
class ProfitabilityEngine @Inject constructor() {

    fun compute(offer: RideOffer, settings: VehicleSettings): ProfitabilityMetrics {
        val gross = offer.grossPriceEuros
        val totalKm = offer.totalDistanceKm
        val totalMin = offer.totalTimeMin

        // Sans prix, aucune métrique n'est calculable.
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

        // 1) Revenu réellement encaissé (déduction commission si prix brut).
        val commissionFactor =
            if (settings.priceIsNetOfCommission) 1.0
            else (1.0 - settings.platformCommissionPercent.coerceIn(0.0, 100.0) / 100.0)
        val collectedRevenue = gross * commissionFactor

        // 2) Charges sociales sur le revenu encaissé.
        val socialCharges = collectedRevenue * settings.socialChargesPercent.coerceAtLeast(0.0) / 100.0

        // 3) Coût véhicule sur la distance totale (approche + course).
        val vehicleCost = (totalKm ?: 0.0) * settings.costPerKmEuros.coerceAtLeast(0.0)

        // 4) Bénéfice net.
        val netProfit = collectedRevenue - socialCharges - vehicleCost

        // 5) Taux horaires (protégés).
        val hours = totalMin?.takeIf { it > 0.0 }?.div(60.0)
        val grossHourly = hours?.let { gross / it }
        val netHourly = hours?.let { netProfit / it }

        // 6) Tarifs au kilomètre (protégés).
        val km = totalKm?.takeIf { it > 0.0 }
        val grossPerKm = km?.let { gross / it }
        val netPerKm = km?.let { netProfit / it }

        // 7) Score couleur, basé sur le NET horaire vs seuils configurés.
        val score = scoreFrom(netHourly, settings)

        return ProfitabilityMetrics(
            grossHourlyRateEuros = grossHourly?.round2(),
            netHourlyRateEuros = netHourly?.round2(),
            grossPricePerKmEuros = grossPerKm?.round2(),
            netPricePerKmEuros = netPerKm?.round2(),
            netProfitEuros = netProfit.round2(),
            totalDistanceKm = totalKm?.round2(),
            totalTimeMin = totalMin?.round2(),
            score = score
        )
    }

    private fun scoreFrom(netHourly: Double?, s: VehicleSettings): ProfitabilityScore {
        if (netHourly == null) return ProfitabilityScore.UNKNOWN
        return when {
            netHourly >= s.greenThresholdHourlyEuros -> ProfitabilityScore.GREEN
            netHourly >= s.orangeThresholdHourlyEuros -> ProfitabilityScore.ORANGE
            else -> ProfitabilityScore.RED
        }
    }

    private fun Double.round2(): Double = Math.round(this * 100.0) / 100.0
}
