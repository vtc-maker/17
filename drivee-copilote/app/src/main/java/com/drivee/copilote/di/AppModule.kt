package com.drivee.copilote.di

import android.content.Context
import androidx.room.Room
import com.drivee.copilote.data.local.AppDatabase
import com.drivee.copilote.data.local.dao.RideDao
import com.drivee.copilote.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "drivee.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRideDao(db: AppDatabase): RideDao = db.rideDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
