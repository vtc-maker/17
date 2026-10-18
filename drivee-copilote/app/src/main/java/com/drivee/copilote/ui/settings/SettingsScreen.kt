package com.drivee.copilote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.domain.model.VehicleSettings
import kotlinx.coroutines.delay

/**
 * Écran "Paramètres de calcul" : coûts/km, commission, charges, objectif horaire,
 * seuils de couleur. Décimales à la virgule (France).
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var costPerKm by remember { mutableStateOf("") }
    var commission by remember { mutableStateOf("") }
    var charges by remember { mutableStateOf("") }
    var perKmThreshold by remember { mutableStateOf("") }
    var hourlyThreshold by remember { mutableStateOf("") }
    var mediumPercent by remember { mutableStateOf("") }
    var avgSpeed by remember { mutableStateOf("") }
    var soundOn by remember { mutableStateOf(true) }
    var priceIsNet by remember { mutableStateOf(true) }
    var savedAt by remember { mutableStateOf(0L) }

    // Message de confirmation éphémère (2 s) après l'enregistrement.
    LaunchedEffect(savedAt) {
        if (savedAt != 0L) { delay(2_000); savedAt = 0L }
    }

    // Initialise les champs quand les paramètres persistés sont chargés.
    LaunchedEffect(settings) {
        costPerKm = settings.costPerKmEuros.frText()
        commission = settings.platformCommissionPercent.frText()
        charges = settings.socialChargesPercent.frText()
        perKmThreshold = settings.perKmThresholdEuros.frText()
        hourlyThreshold = settings.hourlyThresholdEuros.frText()
        mediumPercent = settings.mediumThresholdPercent.frText()
        avgSpeed = settings.avgSpeedKmh.frText()
        soundOn = settings.soundEnabled
        priceIsNet = settings.priceIsNetOfCommission
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Paramètres de calcul",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "Seuil rentable de la pop-up",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Calculé sur le prix affiché, approche incluse. La note retenue est la plus mauvaise des deux : SUPER au-dessus du seuil, MOYEN dans la zone, MAUVAIS en dessous.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        NumberField("Seuil rentable (€/km)", perKmThreshold) { perKmThreshold = it }
        NumberField("Seuil rentable (€/h)", hourlyThreshold) { hourlyThreshold = it }
        NumberField("Zone MOYEN à partir de (% du seuil)", mediumPercent) { mediumPercent = it }
        NumberField("Vitesse moyenne estimée (km/h)", avgSpeed) { avgSpeed = it }
        Text(
            "Sert à estimer la durée quand l'app ne l'affiche pas (Bolt) : la durée apparaît alors avec « ~ ».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Son du verdict", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Deux notes montantes = SUPER, une note = MOYEN, deux notes graves = MAUVAIS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = soundOn, onCheckedChange = { soundOn = it })
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Bénéfice net (historique & statistiques)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        NumberField("Coût au kilomètre (€/km)", costPerKm) { costPerKm = it }
        NumberField("Commission plateforme (%)", commission) { commission = it }
        NumberField("Charges sociales (%)", charges) { charges = it }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Le prix affiché est déjà net",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Désactiver si la plateforme affiche un montant BRUT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = priceIsNet, onCheckedChange = { priceIsNet = it })
        }

        Spacer(Modifier.height(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                viewModel.save(
                    VehicleSettings(
                        costPerKmEuros = costPerKm.frDouble(0.25),
                        platformCommissionPercent = commission.frDouble(0.0),
                        socialChargesPercent = charges.frDouble(22.0),
                        perKmThresholdEuros = perKmThreshold.frDouble(1.0),
                        hourlyThresholdEuros = hourlyThreshold.frDouble(30.0),
                        mediumThresholdPercent = mediumPercent.frDouble(80.0),
                        avgSpeedKmh = avgSpeed.frDouble(35.0),
                        soundEnabled = soundOn,
                        priceIsNetOfCommission = priceIsNet
                    )
                )
                savedAt = System.currentTimeMillis()
            }
        ) { Text("Enregistrer") }

        if (savedAt != 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                "✓ Paramètres enregistrés",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun Double.frText(): String = toString().replace('.', ',')
private fun String.frDouble(default: Double): Double =
    trim().replace(',', '.').toDoubleOrNull() ?: default
