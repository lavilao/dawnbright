package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SunshineCyan,
    onPrimary = Color.Black,
    primaryContainer = SunshineCyanGlow,
    onPrimaryContainer = SunshineCyan,
    secondary = SunshinePurple,
    onSecondary = Color.White,
    tertiary = SunshineEmerald,
    background = DarkObsidian,
    onBackground = TextPrimary,
    surface = NavySlate,
    onSurface = TextPrimary,
    surfaceVariant = NavySlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
