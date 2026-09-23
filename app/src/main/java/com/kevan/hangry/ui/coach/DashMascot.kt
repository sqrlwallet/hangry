package com.kevan.hangry.ui.coach

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R

const val MASCOT_NAME = "Dash"

/** Dash's face in a soft circular badge, used next to coach messages and in the top bar. */
@Composable
fun DashAvatar(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.dash_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    )
}

/** Full-body Dash with a gentle idle bob, used for the empty conversation hero. */
@Composable
fun DashHero(
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dashIdle")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashBob"
    )
    Image(
        painter = painterResource(id = R.drawable.dash_mascot),
        contentDescription = "$MASCOT_NAME the fox waving hello",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = -6.dp.toPx() * bob
                rotationZ = -1.5f + 3f * bob
            }
    )
}
