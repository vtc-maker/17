package com.drivee.copilote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivee.copilote.data.prefs.AppColors
import com.drivee.copilote.service.accessibility.RideAccessibilityService

/**
 * Choix des couleurs : SUPER / MOYEN / MAUVAIS (pop-up, jauge, historique)
 * et anneau de la bulle flottante. Appliqué immédiatement (pas besoin d'« Enregistrer »).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorsSection() {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) } // force la recomposition après un choix

    Text(
        "Couleurs",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))

    AppColors.Slot.entries.forEach { slot ->
        val selected = remember(version) { AppColors.get(context, slot) }
        Column(Modifier.semantics { contentDescription = "Couleur ${slot.label}" }) {
            Spacer(Modifier.height(8.dp))
            Text(slot.label, color = Color(selected), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppColors.palette.forEach { c ->
                    val isSelected = c == selected
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                AppColors.set(context, slot, c)
                                RideAccessibilityService.refreshAppearance()
                                version++
                            }
                    )
                }
            }
        }
    }

    TextButton(onClick = {
        AppColors.reset(context)
        RideAccessibilityService.refreshAppearance()
        version++
    }) { Text("Couleurs par défaut") }
}
