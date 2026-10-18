package com.drivee.copilote.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.drivee.copilote.ui.onboarding.DisclosureBody
import com.drivee.copilote.ui.onboarding.DisclosureText

/**
 * Écran "Tableau de bord" : état des permissions critiques + boutons d'activation.
 * L'état est ré-évalué à chaque retour au premier plan (ON_RESUME).
 */
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var a11yEnabled by remember { mutableStateOf(PermissionChecker.isAccessibilityEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    var batteryOk by remember { mutableStateOf(PermissionChecker.isIgnoringBatteryOptimizations(context)) }
    var showDisclosure by remember { mutableStateOf(false) }

    // Rafraîchit l'état des permissions au retour dans l'app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = PermissionChecker.isAccessibilityEnabled(context)
                overlayEnabled = PermissionChecker.canDrawOverlays(context)
                batteryOk = PermissionChecker.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allReady = a11yEnabled && overlayEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Drivee Copilote",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (allReady) "Prêt · analyse des offres active"
            else "Configuration requise pour démarrer",
            color = if (allReady) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        PermissionCard(
            title = "Service d'accessibilité",
            description = "Autorise l'app à lire les offres affichées par Uber / Bolt afin de calculer la rentabilité. Aucune donnée n'est envoyée en ligne.",
            granted = a11yEnabled,
            actionLabel = if (a11yEnabled) "Activé" else "Activer",
            onClick = { showDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Affichage par-dessus les apps",
            description = "Nécessaire pour afficher la vignette flottante de rentabilité au-dessus de l'application VTC.",
            granted = overlayEnabled,
            actionLabel = if (overlayEnabled) "Accordé" else "Autoriser",
            onClick = { openOverlaySettings(context) }
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Optimisation de la batterie",
            description = "Recommandé : exclure Drivee de l'optimisation pour que l'analyse reste active en arrière-plan.",
            granted = batteryOk,
            actionLabel = if (batteryOk) "Exclu" else "Configurer",
            onClick = { openBatterySettings(context) }
        )
    }

    // Divulgation (Prominent Disclosure) affichée AVANT toute redirection vers
    // les réglages d'accessibilité — exigence Google Play.
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(DisclosureText.TITLE) },
            text = { DisclosureBody() },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    openAccessibilitySettings(context)
                }) { Text("J'accepte et j'active") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Refuser") }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (granted) "●  OK" else "●  À faire",
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, enabled = !granted) {
                Text(actionLabel)
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
