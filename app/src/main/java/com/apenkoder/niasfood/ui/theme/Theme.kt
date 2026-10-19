package com.apenkoder.niasfood.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F2C6),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF9A3A08),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF3A0A00),
    tertiary = Color(0xFF6B5E00),
    tertiaryContainer = Color(0xFFF5E57A),
    onTertiaryContainer = Color(0xFF211B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF6E3DC),
    onSurfaceVariant = Color(0xFF52443E),
    outline = Color(0xFF85736C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// Dark: true black root, #121212 to #1E1E1E surfaces, off-white text, desaturated brand colours.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CDB8D),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFFB7F2C6),
    secondary = Color(0xFFFFB59A),
    onSecondary = Color(0xFF5A1C00),
    secondaryContainer = Color(0xFF7A2A00),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFD8C95F),
    tertiaryContainer = Color(0xFF504600),
    onTertiaryContainer = Color(0xFFF5E57A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1DF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E1DF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08D86),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun NiasFoodTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        shapes = AppShapes,
        content = content
    )
}
