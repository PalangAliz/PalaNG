package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PumpkinLightAccent,
    onPrimary = Color(0xFF241000),
    primaryContainer = PumpkinContainerDark,
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = PumpkinGold,
    onSecondary = Color(0xFF241000),
    secondaryContainer = Color(0xFF3E2700),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = EmeraldConnected,
    onTertiary = Color.White,
    background = Obsidian950,
    onBackground = Color(0xFFF8F6F4),
    surface = Obsidian900,
    onSurface = Color(0xFFF8F6F4),
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Color(0xFFC7BEB6),
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = Color(0xFF4A4038),
    outlineVariant = Color(0xFF2B2520),
    error = CoralError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PumpkinPrimary,
    onPrimary = Color.White,
    primaryContainer = PumpkinContainerLight,
    onPrimaryContainer = Color(0xFF4A1E00),
    secondary = PumpkinGold,
    onSecondary = Color(0xFF2E1500),
    secondaryContainer = Color(0xFFFFF0DB),
    onSecondaryContainer = Color(0xFF4A2500),
    tertiary = EmeraldConnected,
    onTertiary = Color.White,
    background = SurfaceWhite,
    onBackground = Color(0xFF1C1612),
    surface = SurfaceWhite,
    onSurface = Color(0xFF1C1612),
    surfaceVariant = SurfaceVariantWarm,
    onSurfaceVariant = Color(0xFF6B5849),
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceWarmWhite,
    surfaceContainer = SurfaceCardWarm,
    surfaceContainerHigh = Color(0xFFFFEBD6),
    surfaceContainerHighest = Color(0xFFFFDFC0),
    outline = Color(0xFFE6CCB2),
    outlineVariant = Color(0xFFF5E6D6),
    error = CoralError,
    onError = Color.White
)

@Composable
fun XrayNgTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
