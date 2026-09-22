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
    val steps: Color
)

@Immutable
data class HangryCustomTokens(
    val scoreColors: ScoreStateColors,
    val chartColors: ChartMetricColors,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

val DarkScoreStateColors = ScoreStateColors(
    primed = Color(0xFF4CAF50),              // Vibrant Emerald
    primedContainer = Color(0xFF1B5E20),
    balanced = Color(0xFFFFA726),            // Vitality Amber
    balancedContainer = Color(0xFFE65100),
    rebuild = Color(0xFFFF7043),             // Warm Coral
    rebuildContainer = Color(0xFFBF360C),
    buildingBaseline = Color(0xFF78909C),    // Slate Blue
    buildingBaselineContainer = Color(0xFF263238)
)

val LightScoreStateColors = ScoreStateColors(
    primed = Color(0xFF2E7D32),
    primedContainer = Color(0xFFC8E6C9),
    balanced = Color(0xFFF57C00),
    balancedContainer = Color(0xFFFFE0B2),
    rebuild = Color(0xFFD84315),
    rebuildContainer = Color(0xFFFFCCBC),
    buildingBaseline = Color(0xFF455A64),
    buildingBaselineContainer = Color(0xFFCFD8DC)
)

val DarkChartMetricColors = ChartMetricColors(
    sleep = Color(0xFF9FA8DA),
    hrv = Color(0xFFF48FB1),
    restingHeartRate = Color(0xFFEF5350),
    trainingLoad = Color(0xFFFFB74D),
    steps = Color(0xFF4DB6AC)
)

val LightChartMetricColors = ChartMetricColors(
    sleep = Color(0xFF5C6BC0),
    hrv = Color(0xFFE91E63),
    restingHeartRate = Color(0xFFD32F2F),
    trainingLoad = Color(0xFFF57C00),
    steps = Color(0xFF00897B)
)

val LocalHangryTokens = staticCompositionLocalOf {
    HangryCustomTokens(
        scoreColors = DarkScoreStateColors,
        chartColors = DarkChartMetricColors,
        cardBackground = Color(0xFF1E1E24),
        cardBorder = Color(0xFF2C2C35),
        textPrimary = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B)
    )
}
