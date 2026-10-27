package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = EduTeal,
    onPrimary = Color.White,
    primaryContainer = EduTint,
    onPrimaryContainer = EduText,
    secondary = EduGreen,
    onSecondary = Color.White,
    secondaryContainer = EduCream,
    onSecondaryContainer = EduTeal,
    tertiary = EduYellow,
    onTertiary = EduTeal,
    background = EduWarmBg,
    onBackground = EduText,
    surface = EduSurface,
    onSurface = EduText,
    surfaceVariant = EduTint,
    onSurfaceVariant = EduSlate,
    error = EduRed,
    onError = Color.White,
    outline = EduBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = EduYellow,
    onPrimary = EduDarkTeal,
    primaryContainer = EduDarkTeal,
    onPrimaryContainer = Color.White,
    secondary = EduGreen,
    onSecondary = Color.White,
    background = Color(0xFF102521),
    onBackground = Color(0xFFE8F3EE),
    surface = Color(0xFF17332E),
    onSurface = Color(0xFFE8F3EE),
    surfaceVariant = Color(0xFF1E423C),
    onSurfaceVariant = Color(0xFFB4D0C7),
    error = EduCoral
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
