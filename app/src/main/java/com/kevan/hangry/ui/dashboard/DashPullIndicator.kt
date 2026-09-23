package com.kevan.hangry.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.coach.MASCOT_NAME
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** One turn of Dash spinning on the spot: front, turning right, back, then round the other side. */
private val SPIN_FRAMES = listOf(
    R.drawable.dash_spin_front to false,
    R.drawable.dash_spin_quarter to false,
    R.drawable.dash_spin_side to false,
    R.drawable.dash_spin_back to false,
    R.drawable.dash_spin_side to true,
    R.drawable.dash_spin_quarter to true
)

private val INDICATOR_SIZE = 64.dp
private val PULL_TRAVEL = 88.dp

/**
 * Pull-to-refresh with Dash: he drops down from the top as you pull (with a little hop once
 * you've pulled far enough to sync), then spins on the spot while syncing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val fraction = state.distanceFraction
    if (fraction <= 0f && !isRefreshing) return

    val spin = rememberInfiniteTransition(label = "dashSpin")
    val step by spin.animateFloat(
        initialValue = 0f,
        targetValue = SPIN_FRAMES.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 780, easing = LinearEasing)),
        label = "dashSpinStep"
    )
    val (frame, mirrored) = if (isRefreshing) SPIN_FRAMES[step.toInt() % SPIN_FRAMES.size] else SPIN_FRAMES[0]

    Surface(
        shape = CircleShape,
        color = tokens.cardBackground,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(INDICATOR_SIZE)
            .semantics { contentDescription = if (isRefreshing) "$MASCOT_NAME is syncing" else "Pull to sync" }
            .graphicsLayer {
                val pulled = min(fraction, 1.4f)
                // Starts tucked above the screen and slides down with your finger.
                translationY = PULL_TRAVEL.toPx() * pulled - INDICATOR_SIZE.toPx()
                alpha = min(1f, fraction * 2f)
                // Grows in as you pull, then hops once past the point where letting go syncs.
                val grow = 0.6f + 0.4f * min(fraction, 1f)
                val hop = if (!isRefreshing && fraction > 1f) 1f + 0.08f * sin(((fraction - 1f) / 0.4f).coerceIn(0f, 1f) * PI.toFloat()) else 1f
                scaleX = grow * hop
                scaleY = grow * hop
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(frame),
                contentDescription = null,
                modifier = Modifier
                    .size(INDICATOR_SIZE - 8.dp)
                    .graphicsLayer { if (mirrored) scaleX = -1f }
            )
        }
    }
}
