package com.drivee.copilote.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Statistique d'une journée. */
data class DayStat(
    val label: String,       // "Lun 03/06"
    val netRevenue: Double,
    val rideCount: Int
)

/** Statistique d'une semaine. */
data class WeekStat(
    val label: String,       // "S03/06"
    val netRevenue: Double,
    val rideCount: Int,
    val avgHourly: Double?
)

data class StatsUiState(
    val currentWeekDays: List<DayStat> = emptyList(),
    val recentWeeks: List<WeekStat> = emptyList(),
    val weekTotalNet: Double = 0.0,
    val weekRideCount: Int = 0,
    val weekAvgHourly: Double? = null,
    val weekDistanceKm: Double = 0.0,
    val weekRangeLabel: String = "",
    val currentWeekRides: List<RideEntity> = emptyList()
)

/**
 * Agrège l'historique en statistiques journalières et hebdomadaires.
 * Toute l'agrégation est faite en Kotlin à partir des courses des ~6 dernières semaines.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: RideRepository
) : ViewModel() {

    private val weeksBack = 6

    val uiState: StateFlow<StatsUiState> =
        repository.observeBetween(startOfWeeksAgo(weeksBack - 1), now())
            .map { rides -> aggregate(rides) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun aggregate(rides: List<RideEntity>): StatsUiState {
        val dayFmt = SimpleDateFormat("EEE dd/MM", Locale.FRANCE)
        val weekFmt = SimpleDateFormat("dd/MM", Locale.FRANCE)

        // --- Semaine courante : 7 jours (Lundi -> Dimanche). ---
        val weekStart = startOfCurrentWeek()
        val days = (0 until 7).map { offset ->
            val dayStart = addDays(weekStart, offset)
            val dayEnd = addDays(dayStart, 1)
            val dayRides = rides.filter { it.capturedAtEpochMs in dayStart until dayEnd }
            DayStat(
                label = dayFmt.format(Date(dayStart)).replaceFirstChar { it.uppercase() },
                netRevenue = dayRides.sumOf { it.netProfitEuros ?: 0.0 },
                rideCount = dayRides.size
            )
        }

        val currentWeekRides = rides.filter {
            it.capturedAtEpochMs >= weekStart && it.capturedAtEpochMs < addDays(weekStart, 7)
        }
        val weekTotalNet = currentWeekRides.sumOf { it.netProfitEuros ?: 0.0 }
        val weekAvgHourly = currentWeekRides.mapNotNull { it.netHourlyRateEuros }
            .takeIf { it.isNotEmpty() }?.average()
        val weekDistance = currentWeekRides.sumOf {
            (it.approachDistanceKm ?: 0.0) + (it.rideDistanceKm ?: 0.0)
        }
        val weekRangeLabel =
            "${weekFmt.format(Date(weekStart))} → ${weekFmt.format(Date(addDays(weekStart, 6)))}"

        // --- 6 dernières semaines. ---
        val weeks = (weeksBack - 1 downTo 0).map { back ->
            val wStart = startOfWeeksAgo(back)
            val wEnd = addDays(wStart, 7)
            val wRides = rides.filter { it.capturedAtEpochMs in wStart until wEnd }
            WeekStat(
                label = weekFmt.format(Date(wStart)),
                netRevenue = wRides.sumOf { it.netProfitEuros ?: 0.0 },
                rideCount = wRides.size,
                avgHourly = wRides.mapNotNull { it.netHourlyRateEuros }
                    .takeIf { it.isNotEmpty() }?.average()
            )
        }

        return StatsUiState(
            currentWeekDays = days,
            recentWeeks = weeks,
            weekTotalNet = weekTotalNet,
            weekRideCount = currentWeekRides.size,
            weekAvgHourly = weekAvgHourly,
            weekDistanceKm = weekDistance,
            weekRangeLabel = weekRangeLabel,
            currentWeekRides = currentWeekRides
        )
    }

    // ------- helpers dates (semaine commençant le lundi, FR) -------
    private fun now() = System.currentTimeMillis()

    private fun startOfCurrentWeek(): Long = Calendar.getInstance(Locale.FRANCE).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeeksAgo(weeksBack: Int): Long =
        addDays(startOfCurrentWeek(), -7 * weeksBack)

    private fun addDays(base: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = base
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis
}
