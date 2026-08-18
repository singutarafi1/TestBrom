package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ImmersiveDarkColorScheme = darkColorScheme(
    primary = MtkPrimary,
    onPrimary = MtkOnPrimary,
    primaryContainer = MtkPrimaryContainer,
    onPrimaryContainer = MtkOnPrimaryContainer,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = MtkBackground,
    onBackground = MtkTextPrimary,
    surface = MtkSurface,
    onSurface = MtkTextPrimary,
    surfaceVariant = MtkSurfaceVariant,
    onSurfaceVariant = MtkTextSecondary,
    outline = MtkBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Always enforce consistent immersive theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ImmersiveDarkColorScheme,
        typography = Typography,
        content = content
    )
}
