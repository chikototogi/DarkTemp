package com.darktemp.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF061221)
val Panel = Color(0xFF112238)
val Teal = Color(0xFF45E1E8)
val Muted = Color(0xFFAFBED1)

private val DarkTempColors = darkColorScheme(
    primary = Teal, onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF074A55), onPrimaryContainer = Color(0xFFA8F5F7),
    secondary = Color(0xFF83B9FF), onSecondary = Color(0xFF00305B),
    background = Navy, onBackground = Color(0xFFEDF3FC),
    surface = Panel, onSurface = Color(0xFFEDF3FC),
    surfaceVariant = Color(0xFF20334B), onSurfaceVariant = Muted,
    outline = Color(0xFF6F829B), error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5B2026), onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun DarkTempTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkTempColors, typography = Typography(), content = content)
}
