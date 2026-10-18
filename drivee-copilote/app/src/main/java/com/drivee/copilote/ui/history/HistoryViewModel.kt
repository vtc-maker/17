package com.drivee.copilote.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/** Filtres temporels proposés dans l'historique. */
enum class HistoryFilter(val label: String) {
    TODAY("Aujourd'hui"),
    WEEK("7 jours"),
    ALL("Tout")
}

data class HistoryUiState(
    val rides: List<RideEntity> = emptyList(),
    val averageNetHourly: Double? = null,
    val filter: HistoryFilter = HistoryFilter.TODAY
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: RideRepository
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.TODAY)

    private val ridesFlow = filter.flatMapLatest { f ->
        val (from, to) = f.range()
        repository.observeBetween(from, to)
    }

    private val avgFlow = filter.flatMapLatest { f ->
        val (from, to) = f.range()
        repository.observeAverageNetHourly(from, to)
    }

    val uiState: StateFlow<HistoryUiState> =
        combine(ridesFlow, avgFlow, filter) { rides, avg, f ->
            HistoryUiState(rides = rides, averageNetHourly = avg, filter = f)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HistoryUiState()
        )

    fun setFilter(f: HistoryFilter) { filter.value = f }

    private fun HistoryFilter.range(): Pair<Long, Long> {
        // Borne haute ouverte : les courses enregistrées APRÈS le choix du filtre
        // doivent apparaître en direct (le Flow Room se met à jour tout seul).
        return when (this) {
            HistoryFilter.ALL -> 0L to Long.MAX_VALUE
            HistoryFilter.TODAY -> startOfToday() to Long.MAX_VALUE
            HistoryFilter.WEEK -> (System.currentTimeMillis() - 7L * 24 * 3600 * 1000) to Long.MAX_VALUE
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
