package com.drivee.copilote.domain.model

/**
 * Paramètres de calcul saisis par le chauffeur (persistés en Room).
 * Valeurs par défaut calibrées pour le marché français.
 */
data class VehicleSettings(
    /** Coût de revient au kilomètre : carburant + usure + assurance + amortissement (€/km). */
    val costPerKmEuros: Double = 0.25,

    /** Commission de la plateforme (%). À appliquer uniquement si le prix affiché est BRUT. */
    val platformCommissionPercent: Double = 0.0,

    /** Cotisations sociales / charges d'entreprise (% du chiffre d'affaires encaissé). */
    val socialChargesPercent: Double = 22.0,

    /** Objectif de revenu horaire NET (€/h) — pilote le score couleur. */
    val hourlyNetTargetEuros: Double = 15.0,

    /**
     * Seuils de score sur le NET horaire (€/h), configurables :
     *  - >= [greenThresholdHourlyEuros]  -> VERT
     *  - >= [orangeThresholdHourlyEuros] -> ORANGE
     *  - sinon                           -> ROUGE
     * Par défaut : vert = objectif, orange = 70 % de l'objectif.
     */
    val greenThresholdHourlyEuros: Double = 15.0,
    val orangeThresholdHourlyEuros: Double = 10.5,

    /** Le prix affiché par la plateforme est-il déjà NET (commission déduite) ? */
    val priceIsNetOfCommission: Boolean = true
)
