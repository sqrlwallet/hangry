package com.kevan.hangry.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The shared motion language for Hangry.
 *
 * Every animation across the app should draw its duration, easing and spring from here rather
 * than inventing new numbers inline, so the whole app moves with one personality: quick,
 * settled entrances and soft, non-oscillating springs. All values respect the system animation
 * scale - Compose collapses them to an instant change when the user turns animations off.
 */
object HangryMotion {

    /** Almost imperceptible accents: ripples, tints, small fades. */
    const val instant = 120

    /** Quick responses: chips, tints, indicator fades. */
    const val fast = 180

    /** The default for spatial moves: sheets, screen transitions, reveals. */
    const val medium = 240

    /** Deliberate moves: expanding content, larger surface changes. */
    const val slow = 400

    /** Hero reveals that should feel considered: gauges, score count-ups, charts. */
    const val reveal = 650

    // Easings
    /** Balanced accelerate-then-settle. The default for spatial moves. */
    val standard: Easing = FastOutSlowInEasing

    /** Starts quickly, eases in. Use when content is entering. */
    val decelerate: Easing = LinearOutSlowInEasing

    /** Starts slowly, leaves quickly. Use when content is exiting. */
    val accelerate: Easing = FastOutLinearInEasing

    /** Crisp, responsive spring for taps, toggles and the sliding tab indicator. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 520f)

    /** Settled spring for entrances and size changes - no visible overshoot. */
    fun <T> standardSpring(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

    /** Soft, slow spring for large surfaces and ambient motion. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** A touch of play for celebratory or attention-drawing moments. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f)
}
