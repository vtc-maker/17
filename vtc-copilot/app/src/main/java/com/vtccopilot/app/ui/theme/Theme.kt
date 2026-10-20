package com.vtccopilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VtcCopilotColors = darkColorScheme(
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
fun VtcCopilotTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // App volontairement toujours en sombre "cockpit".
    MaterialTheme(
        colorScheme = VtcCopilotColors,
        typography = Typography(),
        content = content
    )
}
