package com.drivee.copilote.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.data.prefs.AppColors
import com.drivee.copilote.ui.common.Period
import com.drivee.copilote.ui.common.PeriodBar
import java.util.Locale

/** "Vos statistiques" : moyennes, qualité des courses, revenus par plateforme (courses prises). */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val superC = Color(AppColors.get(context, AppColors.Slot.SUPER))
    val moyenC = Color(AppColors.get(context, AppColors.Slot.MOYEN))
    val mauvaisC = Color(AppColors.get(context, AppColors.Slot.MAUVAIS))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Vos statistiques",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        PeriodBar(state.period, viewModel::setPeriod, viewModel::setCustomRange)

        Section("Vos moyennes")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Montant moyen", state.averageAmount?.let { String.format(Locale.FRANCE, "%.2f €", it) } ?: "—", Modifier.weight(1f))
            StatCard("Taux moyen", state.averagePerKm?.let { String.format(Locale.FRANCE, "%.2f €/km", it) } ?: "—", Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Distance totale", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    String.format(Locale.US, "%.1f", state.totalDistanceKm),
                    fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface
                )
                Text(" km", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("Qualité des courses")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    listOf(state.superPct to superC, state.moyenPct to moyenC, state.mauvaisPct to mauvaisC).forEach { (pct, c) ->
                        if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(c))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend("Super ${state.superPct}%", superC)
                    Legend("Moyen ${state.moyenPct}%", moyenC)
                    Legend("Mauvais ${state.mauvaisPct}%", mauvaisC)
                }
            }
        }

        Section(
            when (state.period) {
                Period.TODAY -> "Vos revenus par plateforme aujourd'hui"
                Period.WEEK -> "Vos revenus par plateforme cette semaine"
                Period.MONTH -> "Vos revenus par plateforme ce mois-ci"
                else -> "Vos revenus par plateforme"
            }
        )
        Card(Modifier.fillMaxWidth().border(1.dp, superC.copy(alpha = 0.6f), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.platforms.isEmpty()) {
                    Text("Aucune course prise sur cette période.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.platforms.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = if (i == 0) superC else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(16.dp))
                        Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("${p.rideCount} courses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            String.format(Locale.FRANCE, "%.0f €", p.revenue),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = if (i == 0) superC else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total CA", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("${state.rides.size} courses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(String.format(Locale.FRANCE, "%.0f €", state.totalRevenue), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.rides.isNotEmpty(),
            onClick = {
                WeeklySummary.buildShareIntent(context, state)?.let { intent ->
                    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Partager le récap")) }
                }
            }
        ) { Text("Partager le récap (texte + CSV)") }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
