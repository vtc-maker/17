package com.drivee.copilote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivee.copilote.data.repository.SettingsRepository
import com.drivee.copilote.domain.model.VehicleSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<VehicleSettings> =
        repository.observeSettings().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VehicleSettings()
        )

    fun save(updated: VehicleSettings) {
        viewModelScope.launch { repository.save(updated) }
    }
}
