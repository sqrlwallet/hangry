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

/**
 * Nunito (variable weight axis, bundled in res/font) — rounded and friendly to match the Dash
 * mascot, with clear tabular-looking numerals for metrics. One Font entry per weight so Compose
 * picks the right point on the wght axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
    nunito(FontWeight.Black)
)

private fun style(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp
) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing
)

// Nunito has a smaller x-height than Roboto, so body and label sizes sit ~1sp above the Material
// defaults to read at the same size, and nothing drops below 12sp.
val Typography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 56.sp, 64.sp, (-0.5).sp),
    displayMedium = style(FontWeight.ExtraBold, 44.sp, 52.sp, (-0.25).sp),
    displaySmall = style(FontWeight.ExtraBold, 36.sp, 44.sp),
    headlineLarge = style(FontWeight.ExtraBold, 32.sp, 40.sp),
    headlineMedium = style(FontWeight.Bold, 26.sp, 34.sp),
    headlineSmall = style(FontWeight.Bold, 22.sp, 30.sp),
    titleLarge = style(FontWeight.Bold, 20.sp, 26.sp),
    titleMedium = style(FontWeight.Bold, 17.sp, 24.sp),
    titleSmall = style(FontWeight.Bold, 15.sp, 20.sp),
    bodyLarge = style(FontWeight.Normal, 17.sp, 26.sp),
    bodyMedium = style(FontWeight.Normal, 15.sp, 22.sp),
    bodySmall = style(FontWeight.Medium, 13.sp, 18.sp),
    labelLarge = style(FontWeight.Bold, 15.sp, 20.sp),
    labelMedium = style(FontWeight.SemiBold, 13.sp, 18.sp),
    labelSmall = style(FontWeight.SemiBold, 12.sp, 16.sp, 0.2.sp)
)
