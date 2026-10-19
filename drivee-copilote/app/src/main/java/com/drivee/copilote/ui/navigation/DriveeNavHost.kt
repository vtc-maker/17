package com.drivee.copilote.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivee.copilote.data.prefs.OnboardingPrefs
import com.drivee.copilote.ui.dashboard.DashboardScreen
import com.drivee.copilote.ui.history.HistoryScreen
import com.drivee.copilote.ui.onboarding.OnboardingScreen
import com.drivee.copilote.ui.settings.SettingsScreen
import com.drivee.copilote.ui.stats.StatsScreen

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Dest("dashboard", "Accueil", Icons.Default.Home)
    data object Stats : Dest("stats", "Stats", Icons.Default.Assessment)
    data object History : Dest("history", "Historique", Icons.Default.DateRange)
    data object Settings : Dest("settings", "Réglages", Icons.Default.Settings)
}

private val tabs = listOf(Dest.Dashboard, Dest.Stats, Dest.History, Dest.Settings)

@Composable
fun DriveeNavHost() {
    val context = LocalContext.current

    // Écran d'accueil affiché tant que la divulgation n'a pas été acceptée (exigence Google).
    var disclosureAccepted by remember {
        mutableStateOf(OnboardingPrefs.isDisclosureAccepted(context))
    }

    if (!disclosureAccepted) {
        OnboardingScreen(
            onAcceptAndActivate = {
                OnboardingPrefs.setDisclosureAccepted(context, true)
                disclosureAccepted = true
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
            onRefuse = {
                // "Plus tard" : on entre dans l'app mais l'accessibilité reste inactive.
                OnboardingPrefs.setDisclosureAccepted(context, true)
                disclosureAccepted = true
            }
        )
        return
    }

    MainScaffold()
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination

    Scaffold(
        bottomBar = {
            Column {
                Text(
                    "Made by Faucobingo",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationBar {
                tabs.forEach { dest ->
                    val selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(Dest.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Dashboard.route) { DashboardScreen() }
            composable(Dest.Stats.route) { StatsScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
            composable(Dest.History.route) { HistoryScreen() }
        }
    }
}
