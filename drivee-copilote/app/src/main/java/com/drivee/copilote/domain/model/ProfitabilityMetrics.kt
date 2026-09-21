package com.drivee.copilote.domain.model

/** Pastille de rentabilité affichée dans l'overlay. */
enum class ProfitabilityScore { GREEN, ORANGE, RED, UNKNOWN }

/**
 * Résultat des calculs de rentabilité pour une [RideOffer] donnée.
 * Tous les montants sont en euros. Les champs "nullable" indiquent une
 * donnée non calculable (ex: distance manquante -> €/km inconnu).
 */
data class ProfitabilityMetrics(
    val grossHourlyRateEuros: Double?,   // €/h brut
    val netHourlyRateEuros: Double?,     // €/h net (après commission, charges, coûts véhicule)
    val grossPricePerKmEuros: Double?,   // €/km brut (sur distance totale approche+course)
    val netPricePerKmEuros: Double?,     // €/km net
    val netProfitEuros: Double?,         // bénéfice net absolu de la course (€)
    val totalDistanceKm: Double?,
    val totalTimeMin: Double?,
    val score: ProfitabilityScore
)
