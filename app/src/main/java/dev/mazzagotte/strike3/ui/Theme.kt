package dev.mazzagotte.strike3.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFD4A531)
val GoldLight = Color(0xFFE2C477)
val Silver = Color(0xFFD3D8DE)
val Ink = Color(0xFF0B0C0E)
val Panel = Color(0xFF15171A)
val Line = Color(0xFF2A2D32)
val Muted = Color(0xFFA3A8B0)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1B1304),
    primaryContainer = Color(0xFF2A2210),
    onPrimaryContainer = GoldLight,
    secondary = Silver,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFE9ECEF),
    surface = Ink,
    onSurface = Color(0xFFE9ECEF),
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerHigh = Color(0xFF1C1F23),
    surfaceContainerHighest = Color(0xFF23262B),
    outline = Line,
    outlineVariant = Line,
)

@Composable
fun Strike3Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
