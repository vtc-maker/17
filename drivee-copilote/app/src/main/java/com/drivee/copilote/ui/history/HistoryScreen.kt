package com.drivee.copilote.ui.history

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.data.local.entity.RideEntity
import com.drivee.copilote.data.local.entity.RideStatus
import com.drivee.copilote.data.prefs.AppColors
import com.drivee.copilote.ui.common.PeriodBar
import com.drivee.copilote.ui.common.platformName
import com.drivee.copilote.ui.common.rideScore100
import com.drivee.copilote.ui.common.totalKm
import com.drivee.copilote.ui.theme.ScoreUnknown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Votre historique" : courses à valider (Pris ✓ / Pas pris), onglets Courses prises / Pas prises,
 * filtre de période. Chaque offre scannée est sauvegardée avec son statut.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<RideEntity?>(null) }

    toDelete?.let { ride ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer cette course ?") },
            text = {
                Text(
                    "${platformName(ride.sourcePackage)} · " +
                        (ride.grossPriceEuros?.let { String.format(Locale.FRANCE, "%.2f €", it) } ?: "—") + " · " +
                        SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(ride.capturedAtEpochMs))
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(ride.id); toDelete = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }

    // Import d'une sauvegarde CSV (sélecteur de fichiers Android, aucune permission requise).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(context, "Fichier illisible", Toast.LENGTH_LONG).show()
        } else {
            viewModel.importCsv(text) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes les courses enregistrées seront supprimées.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Votre historique",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "${state.takenCount} course(s) prise(s) · ${state.pending.size} en attente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    enabled = state.rides.isNotEmpty(),
                    onClick = {
                        CsvExporter.createShareIntent(context, state.rides)?.let { intent ->
                            runCatching { context.startActivity(Intent.createChooser(intent, "Exporter (CSV)")) }
                        }
                    }
                ) { Text("CSV") }
                TextButton(onClick = {
                    importLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream"))
                }) { Text("Importer") }
                TextButton(onClick = { confirmClear = true }) { Text("Tout effacer") }
            }
        }

        // ---- À valider ----
        if (state.pending.isNotEmpty()) {
            item { SectionTitle("À valider", state.pending.size, Color(0xFFF5A524)) }
            items(state.pending, key = { "p${it.id}" }) { ride ->
                RideCard(ride, highlight = null, onDelete = { toDelete = ride }) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setStatus(ride.id, RideStatus.REJECTED) }
                        ) { Text("Pas pris") }
                        Button(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(AppColors.get(context, AppColors.Slot.SUPER)),
                                contentColor = Color.Black
                            ),
                            onClick = { viewModel.setStatus(ride.id, RideStatus.ACCEPTED) }
                        ) { Text("Pris ✓", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // ---- Onglets + période ----
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryTab.entries.forEach { tab ->
                    val selected = state.tab == tab
                    val accent = if (tab == HistoryTab.TAKEN) Color(AppColors.get(context, AppColors.Slot.SUPER))
                    else Color(AppColors.get(context, AppColors.Slot.MAUVAIS))
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
                            contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = { viewModel.setTab(tab) }
                    ) { Text(tab.label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            PeriodBar(state.period, viewModel::setPeriod, viewModel::setCustomRange)
        }

        item {
            val taken = state.tab == HistoryTab.TAKEN
            SectionTitle(
                if (taken) "Courses prises" else "Pas prises",
                if (taken) state.takenCount else state.notTakenCount,
                Color(AppColors.get(context, if (taken) AppColors.Slot.SUPER else AppColors.Slot.MAUVAIS))
            )
        }

        if (state.rides.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Aucune course sur cette période.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.rides, key = { it.id }) { ride ->
                RideCard(
                    ride,
                    highlight = if (state.tab == HistoryTab.NOT_TAKEN) "Pas pris" else null,
                    onDelete = { toDelete = ride }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.width(3.dp).height(20.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Carte d'une course : badge note, verdict (ou "Pas pris"), plateforme, km · €/km, prix, heure. */
@Composable
private fun RideCard(
    ride: RideEntity,
    highlight: String?,
    onDelete: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }
    val color = scoreColor(context, ride.score)
    val title = highlight ?: verdictLabel(ride.score)
    val km = totalKm(ride.approachDistanceKm, ride.rideDistanceKm)

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(rideScore100(ride.thresholdRatio)?.toString() ?: "—", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            platformName(ride.sourcePackage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        buildString {
                            append(String.format(Locale.US, "%.1f km", km))
                            ride.grossPricePerKmEuros?.let { append("  ·  ").append(String.format(Locale.FRANCE, "%.2f €/km", it)) }
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ride.grossPriceEuros?.let { String.format(Locale.FRANCE, "%.2f €", it) } ?: "—",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        timeFmt.format(Date(ride.capturedAtEpochMs)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Supprimer cette course",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            extra?.invoke()
        }
    }
}

private fun verdictLabel(score: String) = when (score) {
    "GREEN" -> "Super"
    "ORANGE" -> "Moyen"
    "RED" -> "Mauvais"
    else -> "—"
}

private fun scoreColor(context: Context, score: String): Color = when (score) {
    "GREEN" -> Color(AppColors.get(context, AppColors.Slot.SUPER))
    "ORANGE" -> Color(AppColors.get(context, AppColors.Slot.MOYEN))
    "RED" -> Color(AppColors.get(context, AppColors.Slot.MAUVAIS))
    else -> ScoreUnknown
}
