package com.kevan.hangry.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.kevan.hangry.ui.theme.HangryMotion
import kotlin.math.roundToInt

/**
 * A metric whose number rolls up to its value instead of snapping, giving scores and totals a
 * sense of having been measured. Starts from zero when it first appears and animates between
 * values on change; [value] null shows [placeholder] without animating. Digits use the app's
 * tabular figures, so the width stays put while it counts.
 */
@Composable
fun AnimatedMetricText(
    value: Int?,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    format: (Int) -> String = { it.toString() }
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(value) {
        if (value == null) {
            animatable.snapTo(0f)
        } else {
            animatable.animateTo(
                targetValue = value.toFloat(),
                animationSpec = tween(durationMillis = HangryMotion.reveal, easing = HangryMotion.decelerate)
            )
        }
    }

    Text(
        text = if (value == null) placeholder else format(animatable.value.roundToInt()),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1
    )
}
