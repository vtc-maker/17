package com.drivee.copilote.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.drivee.copilote.data.local.entity.VehicleSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM vehicle_settings WHERE id = 0 LIMIT 1")
    fun observe(): Flow<VehicleSettingsEntity?>

    @Query("SELECT * FROM vehicle_settings WHERE id = 0 LIMIT 1")
    suspend fun getOnce(): VehicleSettingsEntity?

    @Upsert
    suspend fun upsert(settings: VehicleSettingsEntity)
}
