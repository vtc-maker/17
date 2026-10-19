package com.drivee.copilote.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Puces Aujourd'hui / Semaine / Mois / Tout / 📅 Dates (sélecteur de période personnalisée). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodBar(
    selected: Period,
    onSelect: (Period) -> Unit,
    onCustomRange: (Long, Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Period.entries.forEach { p ->
            FilterChip(
                selected = p == selected,
                onClick = { if (p == Period.CUSTOM) showPicker = true else onSelect(p) },
                label = { Text(if (p == Period.CUSTOM) "📅 ${p.label}" else p.label) }
            )
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val from = state.selectedStartDateMillis
                        if (from != null) onCustomRange(from, state.selectedEndDateMillis ?: from)
                        showPicker = false
                    }
                ) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } }
        ) {
            DateRangePicker(state = state, title = { Text("Choisir la période", modifier = Modifier) })
        }
    }
}
