package com.drivee.copilote.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivee.copilote.data.local.entity.RideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Query("DELETE FROM rides")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(rides: List<RideEntity>)

    @Query("SELECT * FROM rides")
    suspend fun getAll(): List<RideEntity>

    @Query("UPDATE rides SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** Historique complet, du plus récent au plus ancien. */
    @Query("SELECT * FROM rides ORDER BY capturedAtEpochMs DESC")
    fun observeAll(): Flow<List<RideEntity>>

    /** Historique filtré par intervalle de dates (epoch ms). */
    @Query(
        "SELECT * FROM rides WHERE capturedAtEpochMs BETWEEN :fromMs AND :toMs " +
            "ORDER BY capturedAtEpochMs DESC"
    )
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<RideEntity>>

    /** Rentabilité horaire nette moyenne sur une période. */
    @Query(
        "SELECT AVG(netHourlyRateEuros) FROM rides " +
            "WHERE capturedAtEpochMs BETWEEN :fromMs AND :toMs AND netHourlyRateEuros IS NOT NULL"
    )
    fun observeAverageNetHourly(fromMs: Long, toMs: Long): Flow<Double?>
}
