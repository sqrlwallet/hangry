package com.kevan.hangry.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R

/**
 * Central Hangry Branding Configuration.
 * All visual tokens, asset references, and semantic styles are defined here.
 * If official Hangry brand assets or colors are supplied in the future,
 * update this single file without modifying UI composables.
 */
object HangryTokens {
    const val APP_NAME = "Hangry"

    object Assets {
        val LOGO_RES = R.drawable.hangry_logo
        val LAUNCHER_FOREGROUND_RES = R.drawable.ic_launcher_foreground
    }

    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 16.dp
        val l: Dp = 24.dp
        val xl: Dp = 32.dp
        val xxl: Dp = 48.dp
    }

    object CornerRadii {
        val small: Dp = 8.dp
        val medium: Dp = 16.dp
        val large: Dp = 24.dp
        val pill: Dp = 100.dp
    }

    object Elevations {
        val flat: Dp = 0.dp
        val card: Dp = 2.dp
        val floating: Dp = 6.dp
    }
}

@Immutable
data class ScoreStateColors(
    val primed: Color,
    val primedContainer: Color,
    val balanced: Color,
    val balancedContainer: Color,
    val rebuild: Color,
    val rebuildContainer: Color,
    val buildingBaseline: Color,
    val buildingBaselineContainer: Color
)

@Immutable
data class ChartMetricColors(
    val sleep: Color,
    val hrv: Color,
    val restingHeartRate: Color,
    val trainingLoad: Color,
    val steps: Color,
    val activeCalories: Color = trainingLoad,
    /** Sleep stages: deep, REM, light, awake. */
    val sleepDeep: Color = Color(0xFF0C6FF9),
    val sleepRem: Color = Color(0xFF00A4FF),
    val sleepLight: Color = Color(0xFF7DBBFF),
    val sleepAwake: Color = Color(0xFFFF7E1D),
    /** Heart-rate zones 1-5, easy to peak. */
    val zones: List<Color> = listOf(Color(0xFF00A4FF), Color(0xFF01A652), Color(0xFFFFCE00), Color(0xFFFF7E1D), Color(0xFFE5484D))
)

@Immutable
data class MacroNutrientColors(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val calories: Color,
    val water: Color
)

@Immutable
data class HangryCustomTokens(
    val scoreColors: ScoreStateColors,
    val chartColors: ChartMetricColors,
    val macroColors: MacroNutrientColors,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Signature Pumpkin, shaded per theme so it stays legible as text/icon tint. */
    val brandAccent: Color,
    /** Soft tinted fill behind brand-accent content (chips, badges). */
    val brandAccentContainer: Color,
    /** Floating chrome (bottom nav, overlays) — slightly translucent surface. */
    val glassSurface: Color,
    /** Hairline highlight used on card edges; tuned so it reads on either canvas. */
    val edgeHighlight: Color
)

val DarkScoreStateColors = ScoreStateColors(
    primed = GreenHaze,
    primedContainer = Color(0xFF16301F),
    balanced = Supernova,
    balancedContainer = Color(0xFF332A0A),
    rebuild = Pumpkin,
    rebuildContainer = Color(0xFF3A2213),
    buildingBaseline = TextMutedDark,
    buildingBaselineContainer = SurfaceVariantDark
)

val LightScoreStateColors = ScoreStateColors(
    primed = GreenHazeDeep,
    primedContainer = Color(0xFFE3F5EA),
    balanced = SupernovaDeep,
    balancedContainer = Color(0xFFFFF4CC),
    rebuild = PumpkinDeep,
    rebuildContainer = Color(0xFFFFEBDC),
    buildingBaseline = TextSecondaryLight,
    buildingBaselineContainer = SurfaceVariantLight
)

val DarkChartMetricColors = ChartMetricColors(
    sleep = AzureRadiance,
    hrv = GreenHaze,
    restingHeartRate = Cream,
    trainingLoad = Pumpkin,
    steps = BlueRibbonLight
)

val LightChartMetricColors = ChartMetricColors(
    sleep = AzureDeep,
    hrv = GreenHazeDeep,
    restingHeartRate = ShipGray,
    trainingLoad = Color(0xFFE0650F),
    steps = BlueRibbon,
    // Deeper shades so stages and zones stay readable on the light cream background.
    sleepDeep = Color(0xFF0A5BD1),
    sleepRem = Color(0xFF0086D1),
    sleepLight = Color(0xFF4F93DD),
    sleepAwake = Color(0xFFD9640F),
    zones = listOf(Color(0xFF0086D1), Color(0xFF018A44), Color(0xFFB88C00), Color(0xFFD9640F), Color(0xFFC7373C))
)

val DarkMacroNutrientColors = MacroNutrientColors(
    protein = BlueRibbonLight,
    carbs = GreenHaze,
    fat = Supernova,
    calories = Pumpkin,
    water = AzureRadiance
)

val LightMacroNutrientColors = MacroNutrientColors(
    protein = BlueRibbon,
    carbs = GreenHazeDeep,
    fat = SupernovaDeep,
    calories = Color(0xFFE0650F),
    water = AzureDeep
)

val LightHangryTokens = HangryCustomTokens(
    scoreColors = LightScoreStateColors,
    chartColors = LightChartMetricColors,
    macroColors = LightMacroNutrientColors,
    cardBackground = SurfaceLight,
    cardBorder = OutlineLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    brandAccent = PumpkinDeep,
    brandAccentContainer = Color(0xFFFFEBDC),
    glassSurface = Color(0xF2FFFDFC),
    edgeHighlight = ShipGray.copy(alpha = 0.08f)
)

val DarkHangryTokens = HangryCustomTokens(
    scoreColors = DarkScoreStateColors,
    chartColors = DarkChartMetricColors,
    macroColors = DarkMacroNutrientColors,
    cardBackground = SurfaceDark,
    cardBorder = OutlineDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    brandAccent = Pumpkin,
    brandAccentContainer = Color(0xFF3A2213),
    glassSurface = Color(0xF2252329),
    edgeHighlight = Color.White.copy(alpha = 0.08f)
)

val LocalHangryTokens = staticCompositionLocalOf { LightHangryTokens }
