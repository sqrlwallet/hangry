package com.kevan.hangry.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BlueRibbon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EEFF),
    onPrimaryContainer = Color(0xFF002A66),
    secondary = PumpkinDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBDC),
    onSecondaryContainer = Color(0xFF4A2000),
    tertiary = GreenHazeDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F5EA),
    onTertiaryContainer = Color(0xFF00361A),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF5F1111),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = Color.Transparent, // keep elevated surfaces warm — no blue tonal cast
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = Color(0xFFF7F1EE),
    surfaceContainerHigh = Color(0xFFF2ECE8),
    surfaceContainerHighest = Color(0xFFECE5E1),
    inverseSurface = ShipGray,
    inverseOnSurface = Cream,
    inversePrimary = BlueRibbonLight,
    outline = Color(0xFFCFC6C1),
    outlineVariant = OutlineLight,
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueRibbonLight,
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF123A73),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Pumpkin,
    onSecondary = Color(0xFF3A1800),
    secondaryContainer = Color(0xFF3A2213),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = GreenHaze,
    onTertiary = Color(0xFF00210E),
    tertiaryContainer = Color(0xFF16301F),
    onTertiaryContainer = Color(0xFFC4F0D3),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0B0B),
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF17151A),
    surfaceContainerLow = Color(0xFF211F24),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF38353C),
    inverseSurface = Cream,
    inverseOnSurface = ShipGray,
    inversePrimary = BlueRibbon,
    outline = Color(0xFF5A5560),
    outlineVariant = OutlineDark,
    scrim = Color.Black
)

@Composable
fun HangryTheme(
    // Hangry opens on the warm cream canvas by default (the brand is light-first);
    // pass isSystemInDarkTheme() at the call site to follow the system setting instead.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customTokens = if (darkTheme) DarkHangryTokens else LightHangryTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Nested HangryTheme blocks (e.g. the dark onboarding flow) each register here; the
        // most recently composed one owns the system-bar icon style. A stack rather than a
        // plain SideEffect so that crossfading between screens and popping back to the
        // cream canvas always restores the right icon contrast.
        DisposableEffect(darkTheme, view) {
            val entry = Any() to darkTheme
            SystemBarAppearance.stack.add(entry)
            SystemBarAppearance.apply(view)
            onDispose {
                SystemBarAppearance.stack.remove(entry)
                SystemBarAppearance.apply(view)
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

private object SystemBarAppearance {
    val stack = mutableListOf<Pair<Any, Boolean>>()

    fun apply(view: View) {
        val dark = stack.lastOrNull()?.second ?: return
        val window = (view.context as? Activity)?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}
