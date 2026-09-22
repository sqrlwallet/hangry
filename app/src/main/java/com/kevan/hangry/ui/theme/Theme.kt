package com.kevan.hangry.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmberPrimaryDark,
    onPrimary = Color(0xFF3E0A00),
    primaryContainer = EmberPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFFFCCBC),
    secondary = TealSecondaryDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = SlateTertiaryDark,
    onTertiary = Color(0xFF1E293B),
    background = BackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmberPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = EmberPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF3E0A00),
    secondary = TealSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = TealSecondaryContainerLight,
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = SlateTertiaryLight,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = SurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = OutlineLight
)

@Composable
fun HangryTheme(
    // Hangry opens in dark mode by default (the dashboard is designed dark-first);
    // pass isSystemInDarkTheme() explicitly at a call site to follow the system setting instead.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val customTokens = if (darkTheme) {
        HangryCustomTokens(
            scoreColors = DarkScoreStateColors,
            chartColors = DarkChartMetricColors,
            macroColors = DarkMacroNutrientColors,
            cardBackground = SurfaceDark,
            cardBorder = OutlineDark,
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B)
        )
    } else {
        HangryCustomTokens(
            scoreColors = LightScoreStateColors,
            chartColors = LightChartMetricColors,
            macroColors = LightMacroNutrientColors,
            cardBackground = SurfaceLight,
            cardBorder = OutlineLight,
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF94A3B8)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalHangryTokens provides customTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}