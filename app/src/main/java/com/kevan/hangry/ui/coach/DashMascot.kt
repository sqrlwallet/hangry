package com.kevan.hangry.ui.coach

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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

/** Dash's expressions, each a full-body illustration on a transparent background. */
enum class DashMood(@DrawableRes val imageRes: Int, val description: String) {
    HAPPY(R.drawable.dash_mood_happy, "$MASCOT_NAME giving a thumbs up"),
    CHEER(R.drawable.dash_mood_cheer, "$MASCOT_NAME cheering you on"),
    CELEBRATE(R.drawable.dash_mood_celebrate, "$MASCOT_NAME celebrating with confetti"),
    CONCERNED(R.drawable.dash_mood_concerned, "$MASCOT_NAME looking concerned"),
    SLEEPY(R.drawable.dash_mood_sleepy, "$MASCOT_NAME sleepy in pajamas"),
    THINKING(R.drawable.dash_mood_thinking, "$MASCOT_NAME thinking"),
    WAVE(R.drawable.dash_wave, "$MASCOT_NAME waving hello"),
    HEART(R.drawable.dash_heart, "$MASCOT_NAME holding a glowing heart");

    /** Moods that mark a win spring in when they appear. */
    val popsIn: Boolean get() = this == CELEBRATE || this == CHEER
}

fun RecoveryState.dashMood(): DashMood = when (this) {
    RecoveryState.PRIMED -> DashMood.HAPPY
    RecoveryState.BALANCED -> DashMood.CHEER
    RecoveryState.REBUILD -> DashMood.CONCERNED
    RecoveryState.BUILDING_BASELINE -> DashMood.THINKING
}

/**
 * Dash showing [mood], with a gentle idle bob plus a per-mood flourish: floating Zs when
 * sleepy, a heartbeat when holding the heart, and a springy entrance (with a confetti burst
 * for [DashMood.CELEBRATE]) for wins. Tapping makes Dash hop and flash a thumbs up.
 */
@Composable
fun DashExpression(
    mood: DashMood,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = mood.description,
    interactive: Boolean = true
) {
    val bob = rememberIdleBob(durationMillis = 1800)
    val tap = rememberDashTap()
    val entrance = remember(mood) { Animatable(if (mood.popsIn) 0.55f else 1f) }
    LaunchedEffect(mood) {
        if (mood.popsIn) entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val beat = if (mood == DashMood.HEART) rememberHeartbeat() else 1f
    val shown = if (tap.reacting) DashMood.HAPPY else mood

    Box(
        modifier = modifier
            .size(size)
            .then(if (interactive) tap.modifier else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (mood == DashMood.HEART) {
            HeartGlow(beat = beat, modifier = Modifier.matchParentSize())
        }
        Crossfade(targetState = shown, animationSpec = tween(200), label = "dashMood") { current ->
            Image(
                painter = painterResource(id = current.imageRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        translationY = -size.toPx() * (0.025f * bob + HOP_HEIGHT * tap.hop.value)
                        val scale = entrance.value * beat
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
        }
        if (mood == DashMood.CELEBRATE) {
            ConfettiBurst(modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.SLEEPY) {
            FloatingZs(size = size, modifier = Modifier.matchParentSize())
        }
    }
}

/** A card with Dash in [mood] beside a short line of coaching. */
@Composable
fun DashNote(
    mood: DashMood,
    text: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashExpression(mood = mood, size = 72.dp, contentDescription = null)
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
        }
    }
}

// region Animation helpers

/** How high a tap hop lifts Dash, as a fraction of the figure's size. */
private const val HOP_HEIGHT = 0.12f

@Composable
private fun rememberIdleBob(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "dashIdle")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashBob"
    )
    return bob
}

/** Lub-dub: two quick swells, then a rest, about 55 bpm. */
@Composable
private fun rememberHeartbeat(): Float {
    val transition = rememberInfiniteTransition(label = "dashHeartbeat")
    val beat by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1100
                1f at 0
                1.06f at 120
                1f at 260
                1.035f at 380
                1f at 560
            }
        ),
        label = "dashBeat"
    )
    return beat
}

private class DashTapState(
    val modifier: Modifier,
    val hop: Animatable<Float, *>,
    val reacting: Boolean
)

@Composable
private fun rememberDashTap(): DashTapState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var reacting by remember { mutableStateOf(false) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = "Pet $MASCOT_NAME"
    ) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            hop.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
            hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f))
        }
        reacting = true
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(1200)
            reacting = false
        }
    }
    return DashTapState(modifier, hop, reacting)
}

/** Warm glow behind the heart that brightens with each beat. */
@Composable
private fun HeartGlow(beat: Float, modifier: Modifier = Modifier) {
    // beat runs 1.0..1.06; map it to 0..1 for the glow strength.
    val strength = ((beat - 1f) / 0.06f).coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val center = Offset(this.size.width * 0.42f, this.size.height * 0.6f)
        val radius = this.size.minDimension * (0.34f + 0.06f * strength)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFB38A).copy(alpha = 0.35f + 0.3f * strength), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

private val CONFETTI_COLORS = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFC145), Color(0xFF4ECDC4), Color(0xFF5B8DEF), Color(0xFFB384F5), Color(0xFF6BCB77)
)

private class ConfettiPiece(val angle: Float, val speed: Float, val spin: Float, val color: Color, val wide: Boolean)

/** One burst of confetti from Dash's chest that arcs out and falls, then disappears. */
@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val pieces = remember {
        List(28) {
            ConfettiPiece(
                // Mostly upward: -160°..-20°, so the burst fans over Dash's head.
                angle = (-160f + Random.nextFloat() * 140f) * (PI.toFloat() / 180f),
                speed = 0.45f + Random.nextFloat() * 0.45f,
                spin = Random.nextFloat() * 720f - 360f,
                color = CONFETTI_COLORS.random(),
                wide = Random.nextBoolean()
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 1600, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier = modifier) {
        val p = progress.value
        val origin = Offset(this.size.width / 2f, this.size.height * 0.55f)
        val span = this.size.minDimension
        val alpha = (1f - p * p).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            // Ease out the launch, then let gravity take over.
            val travel = span * piece.speed * (1f - (1f - p) * (1f - p))
            val x = origin.x + cos(piece.angle) * travel
            val y = origin.y + sin(piece.angle) * travel + span * 0.55f * p * p
            val w = span * (if (piece.wide) 0.05f else 0.025f)
            val h = span * 0.025f
            rotate(degrees = piece.spin * p, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - w / 2, y - h / 2),
                    size = Size(w, h)
                )
            }
        }
    }
}

/** Three Zs that drift up and away from Dash's head, staggered so one is always rising. */
@Composable
private fun FloatingZs(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashZs")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3000, easing = LinearEasing)),
        label = "dashZsTime"
    )
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    Box(modifier = modifier) {
        repeat(3) { i ->
            val p = (t + i / 3f) % 1f
            val fontPx = sizePx * (0.08f + 0.07f * p)
            Text(
                text = "z",
                color = Color(0xFF8FA6D6),
                fontWeight = FontWeight.Black,
                fontSize = with(density) { fontPx.toSp() },
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (sizePx * (0.66f + 0.2f * p) + sizePx * 0.03f * sin(p * 2 * PI.toFloat())).roundToInt(),
                            y = (sizePx * (0.28f - 0.3f * p)).roundToInt()
                        )
                    }
                    .graphicsLayer { alpha = sin(p * PI.toFloat()) }
            )
        }
    }
}

// endregion

/** Something went wrong: a worried Dash with what happened and how to fix it. */
@Composable
fun DashAlertCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            DashExpression(mood = DashMood.CONCERNED, size = 64.dp, contentDescription = null)
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                action?.invoke()
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
