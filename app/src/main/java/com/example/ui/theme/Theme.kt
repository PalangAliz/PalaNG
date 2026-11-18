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
    onPrimary = Color(0xFF1C0E00),
    primaryContainer = PumpkinContainerDark,
    onPrimaryContainer = Color(0xFFFFD8B0),
    secondary = PumpkinGold,
    onSecondary = Color(0xFF1C0E00),
    secondaryContainer = Color(0xFF2B1C0B),
    onSecondaryContainer = Color(0xFFFDE2C4),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF052E16),
    background = Obsidian950,
    onBackground = Color(0xFFF3F4F6),
    surface = Obsidian900,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF242933),
    error = CoralError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PumpkinPrimary,
    onPrimary = Color.White,
    primaryContainer = PumpkinContainerLight,
    onPrimaryContainer = Color(0xFF4A2200),
    secondary = PumpkinGold,
    onSecondary = Color.White,
    secondaryContainer = SurfaceNeutralHigh,
    onSecondaryContainer = Color(0xFF1E222A),
    tertiary = EmeraldConnected,
    onTertiary = Color.White,
    background = SurfaceWhite,
    onBackground = Color(0xFF111318),
    surface = SurfaceWhite,
    onSurface = Color(0xFF111318),
    surfaceVariant = SurfaceNeutralCard,
    onSurfaceVariant = Color(0xFF5D6472),
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceNeutralLow,
    surfaceContainer = SurfaceNeutralCard,
    surfaceContainerHigh = SurfaceNeutralHigh,
    surfaceContainerHighest = Color(0xFFE3E6EC),
    outline = Color(0xFFD0D5DD),
    outlineVariant = Color(0xFFE5E7EB),
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
