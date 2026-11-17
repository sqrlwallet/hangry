package com.kevan.hangry.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kevan.hangry.R

/*
 * Allo (https://allo.joshpuckett.me, SIL OFL 1.1), the full variable font bundled in res/font.
 * It has three axes, and the type scale uses all of them so the voice gets bouncier as text gets
 * bigger:
 *
 *  - ROND (roundness, 0 sharp to 100 round): body copy stays at the designed 50, titles go a bit
 *    softer, and headlines and the big numbers go fully round, so they match the Dash mascot.
 *  - opsz (optical size, 9 to 32): Display (32) for headlines, a mid point for titles, Text (14) for
 *    running text, and Small (9) for the smallest labels, which only widens the spacing to keep
 *    12sp legible.
 *  - wght: one Font entry per weight, so Compose picks the right point on the axis.
 *
 * Every Font points at the same file; the families only differ in their variation settings.
 */

@OptIn(ExperimentalTextApi::class)
private fun allo(weight: FontWeight, roundness: Float, opticalSize: Float) = Font(
    resId = R.font.allo,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", roundness),
        FontVariation.Setting("opsz", opticalSize)
    )
)

private val Weights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black
)

private fun alloFamily(roundness: Float, opticalSize: Float) =
    FontFamily(Weights.map { allo(it, roundness, opticalSize) })

/** Running text: Allo as designed. */
val Allo = alloFamily(roundness = 50f, opticalSize = 14f)

/** The smallest labels: Allo's Small optical size spaces letters out for legibility. */
val AlloSmall = alloFamily(roundness = 50f, opticalSize = 9f)

/** Titles: a little softer, between Text and Display. */
val AlloTitle = alloFamily(roundness = 70f, opticalSize = 20f)

/** Headlines and hero numbers: fully round, Display optical size. */
val AlloDisplay = alloFamily(roundness = 100f, opticalSize = 32f)

/** Tabular figures, so counters and columns of numbers don't jiggle. */
private const val FEATURES_TEXT = "tnum"

/** Big numbers also get Allo's open 4, 6 and 9, which read friendlier at size. */
private const val FEATURES_DISPLAY = "tnum, ss03"

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
    features: String = FEATURES_TEXT
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = features
)

// Allo has a tall x-height, so body and label sizes sit on the Material defaults. labelSmall stays
// at 12sp rather than Material's 11 so nothing drops below 12sp.
val Typography = Typography(
    displayLarge = style(AlloDisplay, FontWeight.Black, 56.sp, 62.sp, (-1).sp, FEATURES_DISPLAY),
    displayMedium = style(AlloDisplay, FontWeight.Black, 44.sp, 50.sp, (-0.75).sp, FEATURES_DISPLAY),
    displaySmall = style(AlloDisplay, FontWeight.Black, 36.sp, 42.sp, (-0.5).sp, FEATURES_DISPLAY),
    headlineLarge = style(AlloDisplay, FontWeight.Black, 32.sp, 38.sp, (-0.5).sp, FEATURES_DISPLAY),
    headlineMedium = style(AlloDisplay, FontWeight.ExtraBold, 26.sp, 32.sp, (-0.25).sp, FEATURES_DISPLAY),
    headlineSmall = style(AlloDisplay, FontWeight.ExtraBold, 22.sp, 28.sp, features = FEATURES_DISPLAY),
    titleLarge = style(AlloTitle, FontWeight.ExtraBold, 20.sp, 26.sp),
    titleMedium = style(AlloTitle, FontWeight.Bold, 16.sp, 22.sp),
    titleSmall = style(AlloTitle, FontWeight.Bold, 14.sp, 20.sp),
    bodyLarge = style(Allo, FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = style(Allo, FontWeight.Normal, 14.sp, 21.sp),
    bodySmall = style(AlloSmall, FontWeight.Medium, 12.sp, 17.sp),
    labelLarge = style(Allo, FontWeight.Bold, 14.sp, 20.sp),
    labelMedium = style(AlloSmall, FontWeight.SemiBold, 12.sp, 16.sp),
    labelSmall = style(AlloSmall, FontWeight.SemiBold, 12.sp, 16.sp, 0.2.sp)
)

/** Styles beyond the Material scale, for the places that should have the most personality. */
object HangryType {
    /** The HANGRY wordmark: roundest, heaviest Allo, spaced out. */
    val wordmark = style(AlloDisplay, FontWeight.Black, 32.sp, 38.sp, 2.sp)

    /**
     * Small all-caps section labels: `case` lifts punctuation and symbols to sit beside capitals.
     * Apply to text that is already uppercase.
     */
    val eyebrow = style(AlloSmall, FontWeight.Bold, 12.sp, 16.sp, 1.sp, "tnum, case")
}
