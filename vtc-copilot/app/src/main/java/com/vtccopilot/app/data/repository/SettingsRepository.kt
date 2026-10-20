package com.vtccopilot.app.data.repository

import com.vtccopilot.app.data.config.DefaultParserConfig
import com.vtccopilot.app.data.config.ParserConfig
import com.vtccopilot.app.data.local.dao.SettingsDao
import com.vtccopilot.app.data.local.entity.VehicleSettingsEntity
import com.vtccopilot.app.domain.model.VehicleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès aux paramètres de calcul + à la configuration du parser.
 *
 * La config du parser est ici renvoyée depuis l'embarqué [DefaultParserConfig].
 * Pour des mises à jour à distance, remplacer [getParserConfig] par un chargement
 * réseau (avec cache local) sans toucher au reste du code.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    fun observeSettings(): Flow<VehicleSettings> =
        settingsDao.observe().map { it?.toDomain() ?: VehicleSettings() }

    suspend fun getSettings(): VehicleSettings =
        settingsDao.getOnce()?.toDomain() ?: VehicleSettings()

    suspend fun save(settings: VehicleSettings) =
        settingsDao.upsert(settings.toEntity())

    fun getParserConfig(): ParserConfig = DefaultParserConfig.config

    private fun VehicleSettingsEntity.toDomain() = VehicleSettings(
        costPerKmEuros = costPerKmEuros,
        platformCommissionPercent = platformCommissionPercent,
        socialChargesPercent = socialChargesPercent,
        perKmThresholdEuros = perKmThresholdEuros,
        hourlyThresholdEuros = hourlyThresholdEuros,
        mediumThresholdPercent = mediumThresholdPercent,
        avgSpeedKmh = avgSpeedKmh,
        soundEnabled = soundEnabled,
        priceIsNetOfCommission = priceIsNetOfCommission
    )

    private fun VehicleSettings.toEntity() = VehicleSettingsEntity(
        id = 0,
        costPerKmEuros = costPerKmEuros,
        platformCommissionPercent = platformCommissionPercent,
        socialChargesPercent = socialChargesPercent,
        perKmThresholdEuros = perKmThresholdEuros,
        hourlyThresholdEuros = hourlyThresholdEuros,
        mediumThresholdPercent = mediumThresholdPercent,
        avgSpeedKmh = avgSpeedKmh,
        soundEnabled = soundEnabled,
        priceIsNetOfCommission = priceIsNetOfCommission
    )
}
