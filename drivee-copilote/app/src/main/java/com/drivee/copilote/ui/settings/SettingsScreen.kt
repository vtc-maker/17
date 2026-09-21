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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivee.copilote.domain.model.VehicleSettings

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
    var target by remember { mutableStateOf("") }
    var green by remember { mutableStateOf("") }
    var orange by remember { mutableStateOf("") }
    var priceIsNet by remember { mutableStateOf(true) }

    // Initialise les champs quand les paramètres persistés sont chargés.
    LaunchedEffect(settings) {
        costPerKm = settings.costPerKmEuros.frText()
        commission = settings.platformCommissionPercent.frText()
        charges = settings.socialChargesPercent.frText()
        target = settings.hourlyNetTargetEuros.frText()
        green = settings.greenThresholdHourlyEuros.frText()
        orange = settings.orangeThresholdHourlyEuros.frText()
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

        NumberField("Coût au kilomètre (€/km)", costPerKm) { costPerKm = it }
        NumberField("Commission plateforme (%)", commission) { commission = it }
        NumberField("Charges sociales (%)", charges) { charges = it }
        NumberField("Objectif horaire net (€/h)", target) { target = it }

        Spacer(Modifier.height(8.dp))
        Text(
            "Seuils du score couleur (sur le net €/h)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        NumberField("Seuil VERT ≥ (€/h)", green) { green = it }
        NumberField("Seuil ORANGE ≥ (€/h)", orange) { orange = it }

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
                        hourlyNetTargetEuros = target.frDouble(15.0),
                        greenThresholdHourlyEuros = green.frDouble(15.0),
                        orangeThresholdHourlyEuros = orange.frDouble(10.5),
                        priceIsNetOfCommission = priceIsNet
                    )
                )
            }
        ) { Text("Enregistrer") }

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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun Double.frText(): String = toString().replace('.', ',')
private fun String.frDouble(default: Double): Double =
    trim().replace(',', '.').toDoubleOrNull() ?: default
