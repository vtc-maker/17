package com.vtccopilot.app.data.local.entity

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
    val perKmThresholdEuros: Double = 1.0,
    val hourlyThresholdEuros: Double = 30.0,
    val mediumThresholdPercent: Double = 80.0,
    val avgSpeedKmh: Double = 35.0,
    val soundEnabled: Boolean = true,
    val priceIsNetOfCommission: Boolean = true
)
