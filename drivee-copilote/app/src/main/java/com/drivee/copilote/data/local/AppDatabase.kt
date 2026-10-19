package com.drivee.copilote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drivee.copilote.data.local.dao.RideDao
import com.drivee.copilote.data.local.dao.SettingsDao
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.local.entity.VehicleSettingsEntity

@Database(
    entities = [RideEntity::class, VehicleSettingsEntity::class],
    version = 4, // v4 : statut prise / pas prise / à valider (fallbackToDestructiveMigration)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun settingsDao(): SettingsDao
}
