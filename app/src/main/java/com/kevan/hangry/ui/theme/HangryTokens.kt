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
    val activeCalories: Color = trainingLoad
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
    val textMuted: Color
)

val DarkScoreStateColors = ScoreStateColors(
    primed = MintAccent,                     // Vitality Mint
    primedContainer = Color(0xFF0B2925),
    balanced = AmberAccent,                  // Soft Amber
    balancedContainer = Color(0xFF332005),
    rebuild = EmberAccent,                   // Warm Ember
    rebuildContainer = Color(0xFF33140C),
    buildingBaseline = Color(0xFF64748B),    // Muted Slate
    buildingBaselineContainer = Color(0xFF1E293B)
)

val LightScoreStateColors = ScoreStateColors(
    primed = Color(0xFF0D9488),
    primedContainer = Color(0xFFCCFBF1),
    balanced = Color(0xFFD97706),
    balancedContainer = Color(0xFFFEF3C7),
    rebuild = Color(0xFFEA580C),
    rebuildContainer = Color(0xFFFFEDD5),
    buildingBaseline = Color(0xFF475569),
    buildingBaselineContainer = Color(0xFFF1F5F9)
)

val DarkChartMetricColors = ChartMetricColors(
    sleep = Color(0xFF94A3B8),               // Clean Slate
    hrv = MintAccent,                        // Vitality Mint
    restingHeartRate = Color(0xFFF1F5F9),    // Crisp Silver
    trainingLoad = EmberAccent,              // Warm Ember
    steps = MintAccent                       // Vitality Mint
)

val LightChartMetricColors = ChartMetricColors(
    sleep = Color(0xFF475569),
    hrv = Color(0xFF0D9488),
    restingHeartRate = Color(0xFF0F172A),
    trainingLoad = Color(0xFFEA580C),
    steps = Color(0xFF0D9488)
)

val DarkMacroNutrientColors = MacroNutrientColors(
    protein = Color(0xFF818CF8),             // Soft Indigo
    carbs = Color(0xFF34D399),               // Emerald Green
    fat = AmberAccent,                       // Soft Amber
    calories = EmberAccent,                  // Warm Ember
    water = Color(0xFF38BDF8)                // Sky Blue
)

val LightMacroNutrientColors = MacroNutrientColors(
    protein = Color(0xFF4F46E5),             // Deep Indigo
    carbs = Color(0xFF059669),               // Forest Green
    fat = Color(0xFFD97706),                 // Rich Amber
    calories = Color(0xFFEA580C),            // Deep Ember
    water = Color(0xFF0284C7)                // Ocean Blue
)

val LocalHangryTokens = staticCompositionLocalOf {
    HangryCustomTokens(
        scoreColors = DarkScoreStateColors,
        chartColors = DarkChartMetricColors,
        macroColors = DarkMacroNutrientColors,
        cardBackground = Color(0xFF1E1E24),
        cardBorder = Color(0xFF2C2C35),
        textPrimary = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B)
    )
}
