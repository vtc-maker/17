package com.vtccopilot.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtccopilot.app.data.prefs.AppColors
import com.vtccopilot.app.data.prefs.PopupLayout
import com.vtccopilot.app.data.prefs.PopupPrefs
import com.vtccopilot.app.data.prefs.PopupTheme
import com.vtccopilot.app.domain.model.ProfitabilityScore
import com.vtccopilot.app.domain.model.VerdictLabel
import com.vtccopilot.app.service.accessibility.RideAccessibilityService

private val VERDICTS = listOf(ProfitabilityScore.GREEN, ProfitabilityScore.ORANGE, ProfitabilityScore.RED)

/**
 * Réglages de la pop-up : textes des verdicts (12 caractères, 2 emojis max),
 * mise en page (Complet / Compact / Verdict XXL) et style (Actuel / Classe / Néon / Rétro).
 * Appliqué immédiatement, à la prochaine offre affichée.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PopupSection() {
    val context = LocalContext.current
    val texts = remember {
        VERDICTS.associateWith { mutableStateOf(PopupPrefs.customLabel(context, it)) }
    }
    var layout by remember { mutableStateOf(PopupPrefs.layout(context)) }
    var theme by remember { mutableStateOf(PopupPrefs.theme(context)) }

    Text("Pop-up", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        "Textes des verdicts : ${VerdictLabel.MAX_CHARS} caractères max, dont ${VerdictLabel.MAX_EMOJIS} emojis. Vide = texte par défaut.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))

    VERDICTS.forEach { score ->
        val state = texts.getValue(score)
        val color = Color(AppColors.forScore(context, score))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            value = state.value,
            onValueChange = { v ->
                state.value = VerdictLabel.sanitize(v)
                PopupPrefs.setLabel(context, score, state.value)
            },
            label = { Text("Texte ${PopupPrefs.defaultLabel(score)}") },
            placeholder = { Text(PopupPrefs.defaultLabel(score)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold),
            supportingText = {
                Text("${VerdictLabel.length(state.value)}/${VerdictLabel.MAX_CHARS} · ${VerdictLabel.emojiCount(state.value)}/${VerdictLabel.MAX_EMOJIS} emoji")
            }
        )
    }
    TextButton(onClick = {
        PopupPrefs.resetLabels(context)
        texts.values.forEach { it.value = "" }
    }) { Text("Textes par défaut") }

    Spacer(Modifier.height(8.dp))
    Text("Mise en page", color = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PopupLayout.entries.forEach { l ->
            FilterChip(
                selected = l == layout,
                onClick = { layout = l; PopupPrefs.setLayout(context, l) },
                label = { Text(l.label) }
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("Style", color = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PopupTheme.entries.forEach { t ->
            FilterChip(
                selected = t == theme,
                onClick = { theme = t; PopupPrefs.setTheme(context, t) },
                label = { Text(t.label) }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    PopupPreview(theme, VERDICTS.map { s -> texts.getValue(s).value.ifBlank { PopupPrefs.defaultLabel(s) } to AppColors.forScore(context, s) })

    OutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        onClick = {
            if (!RideAccessibilityService.showDemo()) {
                Toast.makeText(context, "Activez d'abord le service d'accessibilité", Toast.LENGTH_LONG).show()
            }
        }
    ) { Text("Tester la pop-up") }
}

/** Aperçu des 3 verdicts dans les couleurs / police du style choisi. */
@Composable
private fun PopupPreview(theme: PopupTheme, labels: List<Pair<String, Int>>) {
    val shape = RoundedCornerShape(theme.radiusDp.coerceAtMost(20f).dp)
    val family = when (theme) {
        PopupTheme.CLASSE -> FontFamily.Serif
        else -> FontFamily.Monospace
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Color(theme.bg), shape)
            .border(theme.strokeDp.dp, Color(if (theme.strokeIsVerdict) labels.first().second else theme.stroke), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { (text, color) ->
            Text(
                text,
                modifier = Modifier.fillMaxWidth(),
                color = Color(theme.verdictColor(color)),
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
