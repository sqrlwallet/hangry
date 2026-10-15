package com.kevan.hangry.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Central tactile feedback for Hangry.
 *
 * Compose only exposes [androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress] and
 * `TextHandleMove`, the latter intended for dragging a text cursor. Rather than misusing it for
 * every tap, screens call these intent-named helpers, which map to the correct Android
 * [HapticFeedbackConstants]. Driving the [View] directly keeps this independent of the Compose
 * version, which only added richer haptic types in later releases.
 */
class HangryHaptics internal constructor(private val view: View) {

    /** A light tick for selecting a tab, chip, date or segment. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** A firmer confirm for logging, toggling or committing an action. */
    fun confirm() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Repeated light feedback while scrubbing a chart or slider. */
    fun scrub() {
        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    }
}

@Composable
fun rememberHangryHaptics(): HangryHaptics {
    val view = LocalView.current
    return remember(view) { HangryHaptics(view) }
}
