package com.kevan.hangry.ui.coach

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kevan.hangry.R

/** One turn of Dash spinning on the spot: front, turning right, back, then round the other side (mirrored). */
internal val DASH_SPIN_FRAMES = listOf(
    R.drawable.dash_spin_front to false,
    R.drawable.dash_spin_quarter to false,
    R.drawable.dash_spin_side to false,
    R.drawable.dash_spin_back to false,
    R.drawable.dash_spin_side to true,
    R.drawable.dash_spin_quarter to true
)

/** Dash spinning on the spot - the app's loading indicator for anything that takes a moment. */
@Composable
fun DashSpinner(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Loading"
) {
    val spin = rememberInfiniteTransition(label = "dashSpinner")
    val step by spin.animateFloat(
        initialValue = 0f,
        targetValue = DASH_SPIN_FRAMES.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 780, easing = LinearEasing)),
        label = "dashSpinnerStep"
    )
    val (frame, mirrored) = DASH_SPIN_FRAMES[step.toInt() % DASH_SPIN_FRAMES.size]
    Image(
        painter = painterResource(frame),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .graphicsLayer { if (mirrored) scaleX = -1f }
    )
}
