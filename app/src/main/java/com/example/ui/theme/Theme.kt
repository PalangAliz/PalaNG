package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = TealGlacial,
    onPrimary = Color(0xFF03201E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = AzureAccent,
    onSecondary = Color(0xFF041D2E),
    secondaryContainer = Color(0xFF0F2942),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = EmeraldConnected,
    onTertiary = Color(0xFF032318),
    background = Obsidian950,
    onBackground = Color(0xFFEAF0F7),
    surface = Obsidian900,
    onSurface = Color(0xFFEAF0F7),
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Color(0xFF93A4BD),
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = Color(0xFF33435E),
    outlineVariant = Color(0xFF1D293D),
    error = CoralError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = AzureDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = EmeraldConnected,
    onTertiary = Color.White,
    background = SurfaceNeutralLow,
    onBackground = Color(0xFF0F172A),
    surface = SurfaceWhite,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SurfaceNeutralHigh,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceNeutralHigh,
    surfaceContainerHigh = Color(0xFFDCE4EE),
    surfaceContainerHighest = Color(0xFFCFD9E6),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun XrayNgTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
