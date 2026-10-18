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

    /**
     * Seuils "rentables" de la pop-up (calculés sur le PRIX AFFICHÉ, approche incluse) :
     *  - €/km = prix ÷ km totaux
     *  - €/h  = prix ÷ minutes totales × 60
     * La note retenue est la PLUS MAUVAISE des deux.
     */
    val perKmThresholdEuros: Double = 1.0,
    val hourlyThresholdEuros: Double = 30.0,

    /** Zone MOYEN : à partir de ce pourcentage du seuil (en dessous = MAUVAIS). */
    val mediumThresholdPercent: Double = 80.0,

    /** Le prix affiché par la plateforme est-il déjà NET (commission déduite) ? */
    val priceIsNetOfCommission: Boolean = true
)
