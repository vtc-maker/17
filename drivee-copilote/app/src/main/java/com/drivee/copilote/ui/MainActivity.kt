package com.drivee.copilote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.drivee.copilote.ui.navigation.DriveeNavHost
import com.drivee.copilote.ui.theme.DriveeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activité unique hébergeant la navigation Compose.
 * Sert aussi de settingsActivity au service d'accessibilité (voir config XML).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriveeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DriveeNavHost()
                }
            }
        }
    }
}
