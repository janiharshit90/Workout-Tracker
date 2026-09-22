package com.example.mentzertracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color.Black,
    secondary = Color(0xFF9CCC65),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun MentzerTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}