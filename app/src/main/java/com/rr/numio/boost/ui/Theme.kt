package com.rr.numio.boost.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NumioYellow = Color(0xFFFFC107)
val NumioBg = Color(0xFF111111)
val NumioCard = Color(0xFF1C1C1C)
val NumioKey = Color(0xFF2A2A2A)
val NumioText = Color(0xFFEDEDED)
val NumioMuted = Color(0xFF9A9A9A)
val NumioRed = Color(0xFFE5534B)

private val scheme = darkColorScheme(
    primary = NumioYellow,
    onPrimary = Color.Black,
    background = NumioBg,
    onBackground = NumioText,
    surface = NumioCard,
    onSurface = NumioText,
    surfaceVariant = NumioKey,
    onSurfaceVariant = NumioMuted,
)

@Composable
fun NumioBoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
