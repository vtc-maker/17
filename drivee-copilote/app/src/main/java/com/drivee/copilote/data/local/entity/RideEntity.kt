package com.drivee.copilote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Historique d'une course scannée + métriques calculées. */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val capturedAtEpochMs: Long,

    val grossPriceEuros: Double?,
    val approachDistanceKm: Double?,
    val approachTimeMin: Double?,
    val rideDistanceKm: Double?,
    val rideTimeMin: Double?,
    val pickupAddress: String?,
    val destinationAddress: String?,

    val netHourlyRateEuros: Double?,
    val netPricePerKmEuros: Double?,
    val grossPricePerKmEuros: Double?,
    val netProfitEuros: Double?,
    val score: String,
    /** ACCEPTED (prise) · REJECTED (pas prise) · PENDING (à valider par le chauffeur). */
    val status: String = RideStatus.ACCEPTED,
    val grossHourlyRateEuros: Double? = null,
    /** Position par rapport au seuil rentable (1.0 = pile au seuil). */
    val thresholdRatio: Double? = null
)

object RideStatus {
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val PENDING = "PENDING"
}
