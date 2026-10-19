package com.drivee.copilote.data.repository

import com.drivee.copilote.data.local.dao.RideDao
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.local.entity.RideStatus
import com.drivee.copilote.domain.model.ProfitabilityMetrics
import com.drivee.copilote.domain.model.RideOffer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Accès à l'historique local des courses scannées. */
@Singleton
class RideRepository @Inject constructor(
    private val rideDao: RideDao
) {
    suspend fun save(offer: RideOffer, metrics: ProfitabilityMetrics, status: String = RideStatus.ACCEPTED): Long {
        val entity = RideEntity(
            sourcePackage = offer.sourcePackage,
            capturedAtEpochMs = offer.capturedAtEpochMs,
            grossPriceEuros = offer.grossPriceEuros,
            approachDistanceKm = offer.approachDistanceKm,
            approachTimeMin = offer.approachTimeMin,
            rideDistanceKm = offer.rideDistanceKm,
            rideTimeMin = offer.rideTimeMin,
            pickupAddress = offer.pickupAddress,
            destinationAddress = offer.destinationAddress,
            netHourlyRateEuros = metrics.netHourlyRateEuros,
            netPricePerKmEuros = metrics.netPricePerKmEuros,
            grossPricePerKmEuros = metrics.grossPricePerKmEuros,
            netProfitEuros = metrics.netProfitEuros,
            score = metrics.score.name,
            status = status,
            grossHourlyRateEuros = metrics.grossHourlyRateEuros,
            thresholdRatio = metrics.thresholdRatio
        )
        return rideDao.insert(entity)
    }

    suspend fun clearAll() = rideDao.deleteAll()

    suspend fun updateStatus(id: Long, status: String) = rideDao.updateStatus(id, status)

    fun observeAll(): Flow<List<RideEntity>> = rideDao.observeAll()

    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<RideEntity>> =
        rideDao.observeBetween(fromMs, toMs)

    fun observeAverageNetHourly(fromMs: Long, toMs: Long): Flow<Double?> =
        rideDao.observeAverageNetHourly(fromMs, toMs)
}
