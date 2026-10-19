package com.drivee.copilote.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.local.entity.RideStatus
import com.drivee.copilote.data.repository.RideRepository
import com.drivee.copilote.ui.common.Period
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Onglets de l'historique. */
enum class HistoryTab(val label: String) { TAKEN("Courses prises"), NOT_TAKEN("Pas prises") }

data class HistoryUiState(
    val pending: List<RideEntity> = emptyList(),
    val rides: List<RideEntity> = emptyList(),   // liste de l'onglet courant, sur la période
    val takenCount: Int = 0,
    val notTakenCount: Int = 0,
    val tab: HistoryTab = HistoryTab.TAKEN,
    val period: Period = Period.WEEK
)

private data class Filters(val period: Period, val from: Long?, val to: Long?, val tab: HistoryTab)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: RideRepository
) : ViewModel() {

    private val filters = MutableStateFlow(Filters(Period.WEEK, null, null, HistoryTab.TAKEN))

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.observeAll(), filters) { all, f ->
            val (from, to) = f.period.range(f.from, f.to)
            val inPeriod = all.filter { it.capturedAtEpochMs in from until to }
            val taken = inPeriod.filter { it.status == RideStatus.ACCEPTED }
            val notTaken = inPeriod.filter { it.status == RideStatus.REJECTED }
            HistoryUiState(
                // Les courses "à valider" restent visibles quelle que soit la période.
                pending = all.filter { it.status == RideStatus.PENDING },
                rides = if (f.tab == HistoryTab.TAKEN) taken else notTaken,
                takenCount = taken.size,
                notTakenCount = notTaken.size,
                tab = f.tab,
                period = f.period
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriod(p: Period) { filters.value = filters.value.copy(period = p) }

    fun setCustomRange(from: Long, to: Long) {
        filters.value = filters.value.copy(period = Period.CUSTOM, from = from, to = to)
    }

    fun setTab(t: HistoryTab) { filters.value = filters.value.copy(tab = t) }

    /** Validation manuelle d'une course "à valider" (Pris ✓ / Pas pris). */
    fun setStatus(id: Long, status: String) { viewModelScope.launch { repository.updateStatus(id, status) } }

    fun clearAll() { viewModelScope.launch { repository.clearAll() } }
}
