package com.saeid.aidirectorcamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF63E7FF),
    secondary = Color(0xFFB99CFF),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF050609),
    surface = Color(0xFF0B0E14),
    surfaceVariant = Color(0xFF151A24),
    onPrimary = Color(0xFF001014),
    onBackground = Color(0xFFF2F5FA),
    onSurface = Color(0xFFF2F5FA)
)

@Composable
fun AiDirectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
