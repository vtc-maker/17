package com.drivee.copilote.ui.history

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.ui.theme.ScoreGreen
import com.drivee.copilote.ui.theme.ScoreOrange
import com.drivee.copilote.ui.theme.ScoreRed
import com.drivee.copilote.ui.theme.ScoreUnknown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran "Historique" : liste des courses scannées, filtre par date,
 * rentabilité horaire nette MOYENNE sur la période.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Historique",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedButton(
                enabled = state.rides.isNotEmpty(),
                onClick = {
                    CsvExporter.createShareIntent(context, state.rides)?.let { intent ->
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(intent, "Exporter les courses (CSV)")
                            )
                        }
                    }
                }
            ) { Text("Exporter CSV") }
        }
        Spacer(Modifier.height(12.dp))

        // Filtres.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Résumé moyenne.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Rentabilité horaire nette moyenne",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.averageNetHourly?.let {
                        String.format(Locale.FRANCE, "%.1f €/h", it)
                    } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${state.rides.size} course(s) sur la période",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.rides.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune course enregistrée.\nActivez l'analyse depuis le tableau de bord.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.rides, key = { it.id }) { ride -> RideRow(ride) }
            }
        }
    }
}

@Composable
private fun RideRow(ride: RideEntity) {
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pastille de score.
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(scoreColor(ride.score))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ride.grossPriceEuros?.let {
                        String.format(Locale.FRANCE, "%.2f €", it)
                    } ?: "—",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        ride.netPricePerKmEuros?.let {
                            append(String.format(Locale.FRANCE, "%.2f €/km net", it))
                        }
                        ride.totalLabel()?.let { append(" · ").append(it) }
                    }.ifBlank { "Détails indisponibles" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = ride.netHourlyRateEuros?.let {
                        String.format(Locale.FRANCE, "%.1f €/h", it)
                    } ?: "—",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFmt.format(Date(ride.capturedAtEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun RideEntity.totalLabel(): String? {
    val km = (approachDistanceKm ?: 0.0) + (rideDistanceKm ?: 0.0)
    val min = (approachTimeMin ?: 0.0) + (rideTimeMin ?: 0.0)
    if (km <= 0.0 && min <= 0.0) return null
    return String.format(Locale.FRANCE, "%.1f km · %.0f min", km, min)
}

private fun scoreColor(score: String): Color = when (score) {
    "GREEN" -> ScoreGreen
    "ORANGE" -> ScoreOrange
    "RED" -> ScoreRed
    else -> ScoreUnknown
}
