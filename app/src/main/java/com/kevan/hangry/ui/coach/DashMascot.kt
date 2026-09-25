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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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

/**
 * Dash's expressions, each a full-body illustration on a transparent background. Moods with
 * [frames] loop through them (every [frameMillis]) - short exercise animations.
 */
enum class DashMood(
    @DrawableRes val imageRes: Int,
    val description: String,
    val frames: List<Int> = emptyList(),
    val frameMillis: Long = 0
) {
    HAPPY(R.drawable.dash_mood_happy, "$MASCOT_NAME giving a thumbs up"),
    CHEER(R.drawable.dash_mood_cheer, "$MASCOT_NAME cheering you on"),
    CELEBRATE(R.drawable.dash_mood_celebrate, "$MASCOT_NAME celebrating with confetti"),
    CONCERNED(R.drawable.dash_mood_concerned, "$MASCOT_NAME looking concerned"),
    SLEEPY(R.drawable.dash_mood_sleepy, "$MASCOT_NAME sleepy in pajamas"),
    THINKING(R.drawable.dash_mood_thinking, "$MASCOT_NAME thinking"),
    WAVE(R.drawable.dash_wave, "$MASCOT_NAME waving hello"),
    HEART(R.drawable.dash_heart, "$MASCOT_NAME holding a glowing heart"),
    WORKOUT(R.drawable.dash_mood_workout, "$MASCOT_NAME running and working out"),
    NUTRITION(R.drawable.dash_mood_nutrition, "$MASCOT_NAME holding a fresh apple"),

    // Fasting
    FASTING(R.drawable.dash_fasting, "$MASCOT_NAME holding a stopwatch"),
    FASTING_DONE(R.drawable.dash_fasting_done, "$MASCOT_NAME proudly holding up a stopwatch"),

    // Mind and body programs
    MEDITATE(R.drawable.dash_meditate, "$MASCOT_NAME meditating calmly"),
    FOCUS(R.drawable.dash_focus, "$MASCOT_NAME focused, writing at a desk"),
    STRETCH(
        R.drawable.dash_stretch_1, "$MASCOT_NAME stretching in a lunge",
        frames = listOf(R.drawable.dash_stretch_1, R.drawable.dash_stretch_2, R.drawable.dash_stretch_3, R.drawable.dash_stretch_4),
        frameMillis = 900
    ),
    STRENGTH(
        R.drawable.dash_strength_1, "$MASCOT_NAME doing push-ups",
        frames = listOf(R.drawable.dash_strength_1, R.drawable.dash_strength_2, R.drawable.dash_strength_3, R.drawable.dash_strength_2),
        frameMillis = 550
    ),
    PULL_UP(
        R.drawable.dash_pullup_1, "$MASCOT_NAME doing a pull-up",
        frames = listOf(R.drawable.dash_pullup_1, R.drawable.dash_pullup_2),
        frameMillis = 800
    ),
    KNEES(R.drawable.dash_knees, "$MASCOT_NAME in a deep split squat"),
    BACK_CARE(R.drawable.dash_back_care, "$MASCOT_NAME doing a bird-dog exercise"),
    SHOULDER(R.drawable.dash_shoulder, "$MASCOT_NAME raising both arms"),
    BALANCE(R.drawable.dash_balance, "$MASCOT_NAME balancing on one leg"),
    LEVEL_UP(R.drawable.dash_level_up, "$MASCOT_NAME stepping up with a raised fist"),

    // How you're doing
    OUCH(R.drawable.dash_ouch, "$MASCOT_NAME gently holding a sore knee"),
    REST(R.drawable.dash_rest, "$MASCOT_NAME resting on a sofa with a warm drink"),
    PUSH(R.drawable.dash_push, "$MASCOT_NAME ready to sprint"),
    SYNC_ERROR(R.drawable.dash_sync_error, "$MASCOT_NAME holding an unplugged cable"),
    UNWELL(R.drawable.dash_unwell, "$MASCOT_NAME wrapped in a blanket with a thermometer"),

    // Sleep
    SLEEP_GREAT(R.drawable.dash_sleep_great, "$MASCOT_NAME stretching awake, well rested"),
    SLEEP_SHORT(R.drawable.dash_sleep_short, "$MASCOT_NAME tired, holding a coffee"),
    BEDTIME(R.drawable.dash_bedtime, "$MASCOT_NAME getting ready for bed"),

    SUPPLEMENTS(R.drawable.dash_supplements, "$MASCOT_NAME holding a supplement bottle"),
    STREAK(R.drawable.dash_streak_fire, "$MASCOT_NAME celebrating next to a flame");

    /** Moods that mark a win spring in when they appear. */
    val popsIn: Boolean get() = this == CELEBRATE || this == CHEER || this == WORKOUT ||
        this == FASTING_DONE || this == LEVEL_UP || this == STREAK || this == SLEEP_GREAT
}

fun RecoveryState.dashMood(): DashMood = when (this) {
    RecoveryState.PRIMED -> DashMood.PUSH
    RecoveryState.BALANCED -> DashMood.CHEER
    RecoveryState.REBUILD -> DashMood.REST
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
    // Animated values are read only inside graphicsLayer/draw blocks, so the idle motion
    // redraws the image each frame without recomposing the card around it.
    val bob = rememberIdleBob(durationMillis = 1800)
    val runBob = rememberRunBob(durationMillis = 420)
    val waveSway = rememberWaveSway(durationMillis = 750)
    val jitter = rememberConcernJitter(durationMillis = 160)
    val tap = rememberDashTap()
    val entrance = remember(mood) { Animatable(if (mood.popsIn) 0.55f else 1f) }
    LaunchedEffect(mood) {
        if (mood.popsIn) entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val beat: State<Float> = if (mood == DashMood.HEART) rememberHeartbeat() else remember { mutableFloatStateOf(1f) }
    val shown = if (tap.reacting) DashMood.HAPPY else mood
    // Exercise moods loop through their frames.
    var frame by remember(mood) { mutableStateOf(0) }
    LaunchedEffect(mood) {
        if (mood.frames.size > 1) while (true) {
            delay(mood.frameMillis)
            frame = (frame + 1) % mood.frames.size
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .then(if (interactive) tap.modifier else Modifier)
            // A tappable Dash needs a name for TalkBack even where the image itself is decorative.
            .then(
                if (interactive && contentDescription == null) Modifier.semantics { this.contentDescription = MASCOT_NAME }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (mood == DashMood.HEART) {
            HeartGlow(beat = beat, modifier = Modifier.matchParentSize())
        }
        Crossfade(targetState = shown, animationSpec = tween(200), label = "dashMood") { current ->
            Image(
                painter = painterResource(id = current.frames.getOrNull(frame) ?: current.imageRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val currentMood = shown
                        val isRunning = currentMood == DashMood.WORKOUT
                        val isWaving = currentMood == DashMood.WAVE
                        val isConcerned = currentMood == DashMood.CONCERNED || currentMood == DashMood.SYNC_ERROR
                        val isBalancing = currentMood == DashMood.BALANCE
                        val isBreathing = currentMood == DashMood.MEDITATE || currentMood == DashMood.REST

                        val bobTravel = if (isRunning) {
                            -size.toPx() * (0.045f * runBob.value + HOP_HEIGHT * tap.hop.value)
                        } else {
                            -size.toPx() * (0.025f * bob.value + HOP_HEIGHT * tap.hop.value)
                        }
                        translationY = bobTravel + if (isConcerned) (jitter.value * size.toPx() * 0.008f) else 0f
                        translationX = if (isConcerned) (jitter.value * size.toPx() * 0.006f) else 0f

                        rotationZ = when {
                            isWaving -> waveSway.value * 5f
                            // A little wobble that never quite tips over.
                            isBalancing -> waveSway.value * 3f
                            isRunning -> -3.5f + (runBob.value * 2f)
                            currentMood == DashMood.HAPPY -> sin(bob.value * PI.toFloat()) * 2f
                            else -> 0f
                        }
                        rotationY = tap.spin.value * 360f

                        // Meditating and resting Dash breathes slowly instead of bobbing.
                        val breath = if (isBreathing) 1f + 0.025f * bob.value else 1f
                        val scale = entrance.value * beat.value * breath
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 12f * density
                    }
            )
        }
        if (mood == DashMood.CELEBRATE) {
            ConfettiBurst(modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.FASTING_DONE || shown == DashMood.LEVEL_UP || shown == DashMood.STREAK || shown == DashMood.SLEEP_GREAT) {
            TwinklingSparkles(modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.BEDTIME || shown == DashMood.SLEEP_SHORT) {
            FloatingZs(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.SLEEPY) {
            FloatingZs(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.CHEER) {
            TwinklingSparkles(modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.THINKING) {
            FloatingThoughtBubbles(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.CONCERNED) {
            NervousSweatDrop(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.FOCUS) {
            FloatingThoughtBubbles(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.WORKOUT || shown == DashMood.PUSH) {
            RunningDustPuffs(size = size, modifier = Modifier.matchParentSize())
        }
        if (shown == DashMood.NUTRITION) {
            HealthyGleam(size = size, modifier = Modifier.matchParentSize())
        }
        if (tap.showHearts) {
            TapHeartsBurst(size = size, modifier = Modifier.matchParentSize())
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
internal fun rememberIdleBob(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "dashIdle")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashBob"
    )
}

@Composable
private fun rememberRunBob(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "dashRun")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashRunBob"
    )
}

@Composable
private fun rememberWaveSway(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "dashWave")
    return transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashWaveSway"
    )
}

@Composable
private fun rememberConcernJitter(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "dashJitter")
    return transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashConcernJitter"
    )
}

/** Lub-dub: two quick swells, then a rest, about 55 bpm. */
@Composable
private fun rememberHeartbeat(): State<Float> {
    val transition = rememberInfiniteTransition(label = "dashHeartbeat")
    return transition.animateFloat(
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
}

private class DashTapState(
    val modifier: Modifier,
    val hop: Animatable<Float, *>,
    val spin: Animatable<Float, *>,
    val reacting: Boolean,
    val showHearts: Boolean
)

@Composable
private fun rememberDashTap(): DashTapState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    var reacting by remember { mutableStateOf(false) }
    var showHearts by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var resetJob by remember { mutableStateOf<Job?>(null) }

    val modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = "Pet $MASCOT_NAME"
    ) {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastTapTime) < 380L
        lastTapTime = now

        if (isDoubleTap) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                spin.snapTo(0f)
                spin.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
                spin.snapTo(0f)
            }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                hop.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
                hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f))
            }
        }
        reacting = true
        showHearts = true
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(1200)
            reacting = false
            showHearts = false
        }
    }
    return DashTapState(modifier, hop, spin, reacting, showHearts)
}

/** Warm glow behind the heart that brightens with each beat. */
@Composable
private fun HeartGlow(beat: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // beat runs 1.0..1.06; map it to 0..1 for the glow strength. Read here, at draw time.
        val strength = ((beat.value - 1f) / 0.06f).coerceIn(0f, 1f)
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
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3000, easing = LinearEasing)),
        label = "dashZsTime"
    )
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    // Drawn at the largest size and scaled down in graphicsLayer, so the animation never
    // triggers recomposition - only redraws.
    val maxFontPx = sizePx * 0.15f
    Box(modifier = modifier) {
        repeat(3) { i ->
            Text(
                text = "z",
                color = Color(0xFF8FA6D6),
                fontWeight = FontWeight.Black,
                fontSize = with(density) { maxFontPx.toSp() },
                modifier = Modifier
                    .offset {
                        val p = (time.value + i / 3f) % 1f
                        IntOffset(
                            x = (sizePx * (0.66f + 0.2f * p) + sizePx * 0.03f * sin(p * 2 * PI.toFloat())).roundToInt(),
                            y = (sizePx * (0.28f - 0.3f * p)).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        val p = (time.value + i / 3f) % 1f
                        alpha = sin(p * PI.toFloat())
                        val scale = (0.08f + 0.07f * p) / 0.15f
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

/** Golden sparkles that twinkle and rotate around Dash's raised paw and head. */
@Composable
private fun TwinklingSparkles(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashSparkles")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2000, easing = LinearEasing)),
        label = "sparkleTime"
    )
    Canvas(modifier = modifier) {
        val t = time.value
        val span = this.size.minDimension
        val sparkles = listOf(
            Triple(this.size.width * 0.82f, this.size.height * 0.16f, 0f),
            Triple(this.size.width * 0.88f, this.size.height * 0.28f, 0.33f),
            Triple(this.size.width * 0.65f, this.size.height * 0.12f, 0.66f)
        )
        sparkles.forEach { (cx, cy, phaseOffset) ->
            val p = (t + phaseOffset) % 1f
            val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val r = alpha * span * 0.075f
            if (r > 0.5f) {
                rotate(degrees = p * 180f, pivot = Offset(cx, cy)) {
                    drawSparkleStar(center = Offset(cx, cy), radius = r)
                }
            }
        }
    }
}

private fun DrawScope.drawSparkleStar(center: Offset, radius: Float) {
    val inner = radius * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path, color = Color(0xFFFFC145))
    drawCircle(color = Color(0xFFFFF7D6), radius = inner * 0.8f, center = center)
}

/** 3 thought bubbles drifting up from Dash's head while pondering. */
@Composable
private fun FloatingThoughtBubbles(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashThoughts")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
        label = "thoughtTime"
    )
    Canvas(modifier = modifier) {
        val t = time.value
        val span = this.size.minDimension
        val origin = Offset(this.size.width * 0.66f, this.size.height * 0.30f)
        repeat(3) { i ->
            val p = (t + i / 3f) % 1f
            val travel = span * 0.22f * p
            val x = origin.x + travel * 0.45f + span * 0.02f * sin(p * 2 * PI.toFloat())
            val y = origin.y - travel
            val radius = span * (0.025f + 0.03f * p)
            val alpha = (sin(p * PI.toFloat()) * 0.75f).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFF93C5FD).copy(alpha = alpha * 0.4f),
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = alpha * 0.75f),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

/** A nervous sweat drop beading on Dash's temple and sliding down. */
@Composable
private fun NervousSweatDrop(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashSweat")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "sweatTime"
    )
    Canvas(modifier = modifier) {
        val t = time.value
        val span = this.size.minDimension
        val startY = this.size.height * 0.24f
        val dropDistance = span * 0.16f
        val p = (t * 1.35f).coerceIn(0f, 1f)
        if (p < 0.05f || p > 0.95f) return@Canvas
        val y = startY + dropDistance * p * p
        val x = this.size.width * 0.34f
        val r = span * 0.024f
        val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f)
        val path = Path().apply {
            moveTo(x, y - r * 1.8f)
            cubicTo(x + r, y - r * 0.5f, x + r, y + r, x, y + r)
            cubicTo(x - r, y + r, x - r, y - r * 0.5f, x, y - r * 1.8f)
            close()
        }
        drawPath(path, color = Color(0xFF60A5FA).copy(alpha = alpha * 0.85f))
    }
}

/** Playful dust puffs billowing behind Dash's sneakers while running. */
@Composable
private fun RunningDustPuffs(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashDust")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 850, easing = LinearEasing)),
        label = "dustTime"
    )
    Canvas(modifier = modifier) {
        val t = time.value
        val span = this.size.minDimension
        val origin = Offset(this.size.width * 0.22f, this.size.height * 0.88f)
        repeat(2) { i ->
            val p = (t + i * 0.5f) % 1f
            val x = origin.x - span * 0.18f * p
            val y = origin.y - span * 0.035f * sin(p * PI.toFloat())
            val radius = span * (0.02f + 0.038f * p)
            val alpha = (1f - p).coerceIn(0f, 1f) * 0.5f
            drawCircle(
                color = Color(0xFFCBD5E1).copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}

/** A gentle sparkle gleam on Dash's crisp apple. */
@Composable
private fun HealthyGleam(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dashGleam")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
        label = "gleamTime"
    )
    Canvas(modifier = modifier) {
        val t = time.value
        if (t < 0.25f || t > 0.75f) return@Canvas
        val p = (t - 0.25f) / 0.5f
        val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f)
        val center = Offset(this.size.width * 0.67f, this.size.height * 0.62f)
        val radius = this.size.minDimension * 0.045f * alpha
        rotate(degrees = p * 90f, pivot = center) {
            drawSparkleStar(center = center, radius = radius)
        }
    }
}

/** Floating hearts that flutter up when Dash is petted. */
@Composable
private fun TapHeartsBurst(size: Dp, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier = modifier) {
        val p = progress.value
        val span = this.size.minDimension
        val origin = Offset(this.size.width * 0.5f, this.size.height * 0.35f)
        val alpha = (1f - p * p).coerceIn(0f, 1f)

        val hearts = listOf(
            Triple(-span * 0.18f * p, -span * 0.32f * p, span * 0.038f),
            Triple(0f, -span * 0.40f * p, span * 0.046f),
            Triple(span * 0.20f * p, -span * 0.30f * p, span * 0.036f)
        )
        hearts.forEach { (dx, dy, r) ->
            val cx = origin.x + dx
            val cy = origin.y + dy
            drawHeart(center = Offset(cx, cy), radius = r, color = Color(0xFFFF5277).copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        val x = center.x
        val y = center.y
        val w = radius * 2
        val h = radius * 2
        moveTo(x, y + h * 0.35f)
        cubicTo(x - w * 0.55f, y, x - w * 0.6f, y - h * 0.5f, x, y - h * 0.3f)
        cubicTo(x + w * 0.6f, y - h * 0.5f, x + w * 0.55f, y, x, y + h * 0.35f)
        close()
    }
    drawPath(path, color = color)
}

// endregion

/** Something went wrong: a worried Dash with what happened and how to fix it. */
@Composable
fun DashAlertCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    mood: DashMood = DashMood.CONCERNED,
    onDismiss: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            DashExpression(mood = mood, size = 64.dp, contentDescription = null)
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
