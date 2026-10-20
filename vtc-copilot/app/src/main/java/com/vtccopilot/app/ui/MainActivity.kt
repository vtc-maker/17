package com.vtccopilot.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vtccopilot.app.ui.navigation.VtcCopilotNavHost
import com.vtccopilot.app.ui.theme.VtcCopilotTheme
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
            VtcCopilotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VtcCopilotNavHost()
                }
            }
        }
    }
}
