package com.drivee.copilote.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
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
import com.drivee.copilote.service.accessibility.ActiveNotification
import com.drivee.copilote.service.accessibility.Diagnostics
import com.drivee.copilote.service.accessibility.RideAccessibilityService
import com.drivee.copilote.ui.onboarding.DisclosureBody
import com.drivee.copilote.ui.onboarding.DisclosureText

/**
 * Écran "Tableau de bord" : état du service + permissions + test de la pop-up.
 * L'état est ré-évalué à chaque retour au premier plan (ON_RESUME).
 */
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var a11yEnabled by remember { mutableStateOf(PermissionChecker.isAccessibilityEnabled(context)) }
    var serviceRunning by remember { mutableStateOf(RideAccessibilityService.isRunning) }
    var notifOk by remember { mutableStateOf(PermissionChecker.areNotificationsEnabled(context)) }
    var batteryOk by remember { mutableStateOf(PermissionChecker.isIgnoringBatteryOptimizations(context)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var notifAskedOnce by remember { mutableStateOf(false) }
    var diagSummary by remember { mutableStateOf(diagnosticSummary()) }

    fun refresh() {
        diagSummary = diagnosticSummary()
        a11yEnabled = PermissionChecker.isAccessibilityEnabled(context)
        serviceRunning = RideAccessibilityService.isRunning
        notifOk = PermissionChecker.areNotificationsEnabled(context)
        batteryOk = PermissionChecker.isIgnoringBatteryOptimizations(context)
        if (serviceRunning && notifOk) ActiveNotification.show(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = a11yEnabled && serviceRunning

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
            text = if (ready) "● Actif · analyse des offres Uber, Bolt, Heetch"
            else "Configuration requise pour démarrer",
            color = if (ready) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        PermissionCard(
            title = "Service d'accessibilité",
            description = when {
                ready -> "Le service tourne en arrière-plan et lit les offres pour calculer leur rentabilité. Aucune donnée n'est envoyée en ligne."
                a11yEnabled -> "Le service est coché mais ne tourne pas. Désactivez-le puis réactivez-le dans les réglages d'accessibilité."
                else -> "Autorise l'app à lire les offres affichées par Uber / Bolt / Heetch afin de calculer la rentabilité. Aucune donnée n'est envoyée en ligne."
            },
            granted = ready,
            actionLabel = if (ready) "Activé" else "Activer",
            onClick = { if (a11yEnabled) openAccessibilitySettings(context) else showDisclosure = true }
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Notifications",
            description = "Affiche la notification « Drivee actif » pour savoir d'un coup d'œil que l'analyse tourne.",
            granted = notifOk,
            actionLabel = if (notifOk) "Autorisé" else "Autoriser",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifAskedOnce) {
                    notifAskedOnce = true
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Optimisation de la batterie",
            description = "Recommandé : exclure Drivee de l'optimisation pour que l'analyse reste active en arrière-plan.",
            granted = batteryOk,
            actionLabel = if (batteryOk) "Exclu" else "Configurer",
            onClick = { openBatterySettings(context) }
        )

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Tester la pop-up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Affiche une offre d'exemple (Uber, Heetch, Bolt à chaque appui) avec vos seuils. Appuyez sur la pop-up pour la fermer, glissez-la pour la déplacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (!RideAccessibilityService.showDemo()) {
                        Toast.makeText(context, "Activez d'abord le service d'accessibilité", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Afficher un exemple") }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Diagnostic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si la pop-up n'apparaît pas sur une vraie offre : attendez une offre, revenez ici, puis partagez le diagnostic (textes lus à l'écran, rien n'est envoyé automatiquement).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    diagSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh() }) { Text("Rafraîchir") }
                    Button(onClick = { shareDiagnostic(context) }) { Text("Partager") }
                }
            }
        }
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

private fun diagnosticSummary(): String =
    "Service : ${if (RideAccessibilityService.isRunning) "actif" else "inactif"}\n" +
        "Événements reçus : ${Diagnostics.eventSummary()}\n" +
        "Dernier résultat : ${Diagnostics.lastResult}"

private fun shareDiagnostic(context: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Diagnostic Drivee Copilote")
        .putExtra(Intent.EXTRA_TEXT, Diagnostics.report(RideAccessibilityService.isRunning))
    runCatching { context.startActivity(Intent.createChooser(send, "Partager le diagnostic")) }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
