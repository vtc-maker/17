package com.drivee.copilote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Paramètres de calcul de l'utilisateur (ligne unique, id = 0).
 * Valeurs par défaut calibrées France.
 */
@Entity(tableName = "vehicle_settings")
data class VehicleSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val costPerKmEuros: Double = 0.25,
    val platformCommissionPercent: Double = 0.0,
    val socialChargesPercent: Double = 22.0,
    val hourlyNetTargetEuros: Double = 15.0,
    val greenThresholdHourlyEuros: Double = 15.0,
    val orangeThresholdHourlyEuros: Double = 10.5,
    val priceIsNetOfCommission: Boolean = true
)
