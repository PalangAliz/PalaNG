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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = PantherGoldLight,
    onPrimary = Color(0xFF211300),
    primaryContainer = PantherGoldContainerDark,
    onPrimaryContainer = Color(0xFFFFDEB3),
    secondary = PumpkinYellow,
    onSecondary = Color(0xFF211300),
    secondaryContainer = PumpkinSoftDark,
    onSecondaryContainer = Color(0xFFFFD89E),
    tertiary = EmeraldConnected,
    onTertiary = Color(0xFF032318),
    background = Obsidian950,
    onBackground = Color(0xFFF1F4F9),
    surface = Obsidian900,
    onSurface = Color(0xFFF1F4F9),
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Color(0xFF9BA6BA),
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = Color(0xFF354054),
    outlineVariant = Color(0xFF252D3D),
    error = CoralError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PantherInk,
    onPrimary = Color.White,
    primaryContainer = PantherInkContainer,
    onPrimaryContainer = Color(0xFF10141D),
    secondary = PumpkinYellowDeep,
    onSecondary = Color.White,
    secondaryContainer = PumpkinSoftLight,
    onSecondaryContainer = Color(0xFF4D2900),
    tertiary = EmeraldConnected,
    onTertiary = Color.White,
    background = SurfaceWhite,
    onBackground = Color(0xFF141821),
    surface = SurfaceWhite,
    onSurface = Color(0xFF141821),
    surfaceVariant = SurfaceNeutralHigh,
    onSurfaceVariant = Color(0xFF546074),
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceNeutralCard,
    surfaceContainer = SurfaceNeutralHigh,
    surfaceContainerHigh = Color(0xFFE9EDF4),
    surfaceContainerHighest = Color(0xFFDDE3EC),
    outline = Color(0xFFC5CDDA),
    outlineVariant = Color(0xFFE1E6EF),
    error = Color(0xFFDC2626),
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

    CompositionLocalProvider(LocalIsDarkTheme provides useDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
