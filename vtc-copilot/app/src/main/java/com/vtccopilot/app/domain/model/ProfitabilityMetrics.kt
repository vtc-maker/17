package com.vtccopilot.app.domain.model

/** Verdict affiché dans la pop-up. Le nom (GREEN…) est stocké en base, le label est affiché. */
enum class ProfitabilityScore(val label: String) {
    GREEN("SUPER"), ORANGE("MOYEN"), RED("MAUVAIS"), UNKNOWN("—")
}

/**
 * Résultat des calculs de rentabilité pour une [RideOffer] donnée.
 * Tous les montants sont en euros. Les champs "nullable" indiquent une
 * donnée non calculable (ex: distance manquante -> €/km inconnu).
 */
data class ProfitabilityMetrics(
    val grossHourlyRateEuros: Double?,   // €/h affiché (prix ÷ durée totale)
    val netHourlyRateEuros: Double?,     // €/h net (après commission, charges, coûts véhicule)
    val grossPricePerKmEuros: Double?,   // €/km affiché (prix ÷ distance totale)
    val netPricePerKmEuros: Double?,     // €/km net
    val netProfitEuros: Double?,         // bénéfice net absolu de la course (€)
    val totalDistanceKm: Double?,
    val totalTimeMin: Double?,
    val score: ProfitabilityScore,
    /** Position par rapport au seuil rentable : 1.0 = pile au seuil (min des ratios €/km et €/h). */
    val thresholdRatio: Double? = null
)
