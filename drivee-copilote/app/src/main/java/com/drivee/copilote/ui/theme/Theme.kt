package com.drivee.copilote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DriveeColors = darkColorScheme(
    primary = AmberAccent,
    onPrimary = Graphite900,
    background = Graphite900,
    onBackground = TextPrimary,
    surface = Graphite800,
    onSurface = TextPrimary,
    surfaceVariant = Graphite700,
    onSurfaceVariant = TextSecondary,
    outline = Graphite700
)

@Composable
fun DriveeTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // App volontairement toujours en sombre "cockpit".
    MaterialTheme(
        colorScheme = DriveeColors,
        typography = Typography(),
        content = content
    )
}
