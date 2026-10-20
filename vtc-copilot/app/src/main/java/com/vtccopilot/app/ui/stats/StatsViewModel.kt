package com.vtccopilot.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtccopilot.app.data.local.entity.RideEntity
import com.vtccopilot.app.data.local.entity.RideStatus
import com.vtccopilot.app.data.repository.RideRepository
import com.vtccopilot.app.ui.common.Period
import com.vtccopilot.app.ui.common.platformName
import com.vtccopilot.app.ui.common.totalKm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Revenus d'une plateforme sur la période. */
data class PlatformStat(val name: String, val rideCount: Int, val revenue: Double)

data class StatsUiState(
    val period: Period = Period.WEEK,
    val periodLabel: String = Period.WEEK.label,
    val rides: List<RideEntity> = emptyList(),      // courses PRISES de la période
    val averageAmount: Double? = null,              // montant moyen €
    val averagePerKm: Double? = null,               // taux moyen €/km (CA total / km total)
    val totalDistanceKm: Double = 0.0,
    val superPct: Int = 0,
    val moyenPct: Int = 0,
    val mauvaisPct: Int = 0,
    val platforms: List<PlatformStat> = emptyList(),
    val totalRevenue: Double = 0.0
)

private data class PeriodFilter(val period: Period, val from: Long? = null, val to: Long? = null)

/** "Vos statistiques" : moyennes, qualité des courses et revenus par plateforme (courses prises). */
@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: RideRepository
) : ViewModel() {

    private val filter = MutableStateFlow(PeriodFilter(Period.WEEK))

    val uiState: StateFlow<StatsUiState> =
        combine(repository.observeAll(), filter) { all, f ->
            val (from, to) = f.period.range(f.from, f.to)
            aggregate(f.period, all.filter { it.status == RideStatus.ACCEPTED && it.capturedAtEpochMs in from until to })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(p: Period) { filter.value = PeriodFilter(p) }

    fun setCustomRange(from: Long, to: Long) { filter.value = PeriodFilter(Period.CUSTOM, from, to) }

    private fun aggregate(period: Period, rides: List<RideEntity>): StatsUiState {
        val revenue = rides.sumOf { it.grossPriceEuros ?: 0.0 }
        val km = rides.sumOf { totalKm(it.approachDistanceKm, it.rideDistanceKm) }
        val scored = rides.filter { it.score != "UNKNOWN" }
        fun pct(score: String) =
            if (scored.isEmpty()) 0 else Math.round(scored.count { it.score == score } * 100f / scored.size)

        val platforms = rides.groupBy { platformName(it.sourcePackage) }
            .map { (name, list) -> PlatformStat(name, list.size, list.sumOf { it.grossPriceEuros ?: 0.0 }) }
            .sortedByDescending { it.revenue }

        return StatsUiState(
            period = period,
            periodLabel = period.label,
            rides = rides,
            averageAmount = if (rides.isEmpty()) null else revenue / rides.size,
            averagePerKm = if (km > 0) revenue / km else null,
            totalDistanceKm = km,
            superPct = pct("GREEN"),
            moyenPct = pct("ORANGE"),
            mauvaisPct = pct("RED"),
            platforms = platforms,
            totalRevenue = revenue
        )
    }
}
