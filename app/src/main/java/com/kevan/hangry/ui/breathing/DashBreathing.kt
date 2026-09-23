package com.kevan.hangry.ui.breathing

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.BreathPhaseType
import com.kevan.hangry.ui.coach.MASCOT_NAME

/** Which Dash frame matches the current point in the breath. */
@DrawableRes
internal fun dashBreathFrame(phase: BreathPhaseType, phaseProgress: Float, isPaused: Boolean): Int = when {
    isPaused -> R.drawable.dash_breathe_rest
    phase == BreathPhaseType.INHALE -> R.drawable.dash_breathe_in
    phase == BreathPhaseType.HOLD_FULL -> R.drawable.dash_breathe_hold
    phase == BreathPhaseType.EXHALE && phaseProgress < 0.55f -> R.drawable.dash_breathe_out
    phase == BreathPhaseType.EXHALE -> R.drawable.dash_breathe_out_end
    else -> R.drawable.dash_breathe_rest
}

/**
 * Dash breathing along with the session: poses crossfade per phase, and the body
 * swells slightly with [fill] (0 = empty lungs, 1 = full) so the motion stays continuous.
 */
@Composable
fun DashBreathing(
    phase: BreathPhaseType,
    phaseProgress: Float,
    fill: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = dashBreathFrame(phase, phaseProgress, isPaused),
        animationSpec = tween(durationMillis = 350),
        label = "dashBreathFrame",
        modifier = modifier.graphicsLayer {
            val swell = 0.95f + 0.07f * fill
            scaleX = swell
            scaleY = swell
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) { frame ->
        Image(
            painter = painterResource(frame),
            contentDescription = "$MASCOT_NAME breathing along with you",
            contentScale = ContentScale.Fit
        )
    }
}
