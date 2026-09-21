package com.drivee.copilote.data.repository

import com.drivee.copilote.data.config.DefaultParserConfig
import com.drivee.copilote.data.config.ParserConfig
import com.drivee.copilote.data.local.dao.SettingsDao
import com.drivee.copilote.data.local.entity.VehicleSettingsEntity
import com.drivee.copilote.domain.model.VehicleSettings
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
        hourlyNetTargetEuros = hourlyNetTargetEuros,
        greenThresholdHourlyEuros = greenThresholdHourlyEuros,
        orangeThresholdHourlyEuros = orangeThresholdHourlyEuros,
        priceIsNetOfCommission = priceIsNetOfCommission
    )

    private fun VehicleSettings.toEntity() = VehicleSettingsEntity(
        id = 0,
        costPerKmEuros = costPerKmEuros,
        platformCommissionPercent = platformCommissionPercent,
        socialChargesPercent = socialChargesPercent,
        hourlyNetTargetEuros = hourlyNetTargetEuros,
        greenThresholdHourlyEuros = greenThresholdHourlyEuros,
        orangeThresholdHourlyEuros = orangeThresholdHourlyEuros,
        priceIsNetOfCommission = priceIsNetOfCommission
    )
}
