package com.drivee.copilote.ui.stats

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.ui.theme.AmberAccent
import com.drivee.copilote.ui.theme.ScoreGreen
import java.util.Locale

/**
 * Écran "Statistiques" : graphes de revenu net par jour (semaine en cours) et par
 * semaine (6 dernières), + récap hebdo partageable en un tap.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Statistiques",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Semaine ${state.weekRangeLabel}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // Résumé de la semaine.
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBox("Revenu net", String.format(Locale.FRANCE, "%.0f €", state.weekTotalNet))
                StatBox("Courses", state.weekRideCount.toString())
                StatBox(
                    "Moyenne",
                    state.weekAvgHourly?.let { String.format(Locale.FRANCE, "%.1f €/h", it) } ?: "—"
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Graphe : revenu net par jour (semaine en cours).
        Text(
            "Revenu net par jour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        BarChart(
            values = state.currentWeekDays.map { it.netRevenue.toFloat() },
            labels = state.currentWeekDays.map { it.label.take(3) },
            barColor = AmberAccent
        )

        Spacer(Modifier.height(24.dp))

        // Graphe : revenu net par semaine (6 dernières).
        Text(
            "Revenu net par semaine",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        BarChart(
            values = state.recentWeeks.map { it.netRevenue.toFloat() },
            labels = state.recentWeeks.map { it.label },
            barColor = ScoreGreen
        )

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.currentWeekRides.isNotEmpty(),
            onClick = {
                WeeklySummary.buildShareIntent(context, state)?.let { intent ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(intent, "Partager le récap hebdomadaire")
                        )
                    }
                }
            }
        ) { Text("Partager le récap hebdo") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Graphe à barres minimaliste dessiné au Canvas (aucune dépendance externe).
 * Étiquettes affichées sous le graphe via une Row alignée.
 */
@Composable
private fun BarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                drawBars(values, maxValue, barColor, gridColor)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { l ->
                    Box(Modifier.padding(horizontal = 2.dp)) {
                        Text(
                            l,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBars(
    values: List<Float>,
    maxValue: Float,
    barColor: Color,
    gridColor: Color
) {
    if (values.isEmpty()) return
    val count = values.size
    val slot = size.width / count
    val barWidth = slot * 0.55f
    val bottom = size.height

    // Ligne de base.
    drawLine(
        color = gridColor,
        start = Offset(0f, bottom),
        end = Offset(size.width, bottom),
        strokeWidth = 2f
    )

    values.forEachIndexed { i, v ->
        val h = (v / maxValue) * (size.height * 0.9f)
        val left = i * slot + (slot - barWidth) / 2f
        drawRoundRect(
            color = barColor,
            topLeft = Offset(left, bottom - h),
            size = androidx.compose.ui.geometry.Size(barWidth, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
    }
}
