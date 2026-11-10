package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FileManagerDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueVariant,
    onPrimaryContainer = Color.White,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = CharcoalSurfaceHighlight,
    onSecondaryContainer = Color.White,
    tertiary = WarningAmber,
    onTertiary = Color.Black,
    background = CharcoalBackground,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalSurfaceHighlight,
    outlineVariant = Color(0xFF333B4F),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun FileManagerProTheme(
    darkTheme: Boolean = true, // Default to Dark Mode as requested
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FileManagerDarkColorScheme,
        typography = Typography,
        content = content
    )
}
