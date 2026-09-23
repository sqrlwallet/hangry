package com.kevan.hangry.domain.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wording for Hangry's daily nudges, kept free of Android so it can be tested. */
object NudgeText {

    data class Message(val title: String, val body: String)

    fun stateLabel(state: RecoveryState): String = when (state) {
        RecoveryState.PRIMED -> "primed"
        RecoveryState.BALANCED -> "balanced"
        RecoveryState.REBUILD -> "time to take it easy"
        RecoveryState.BUILDING_BASELINE -> "still building your baseline"
    }

    /** "Recovery 74%, primed" / "Slept 7h 20m. Aim for a strain of 12.0–15.0 today." */
    fun morning(score: Int, state: RecoveryState, sleepMinutes: Int?, strainLow: Double?, strainHigh: Double?): Message {
        val sleep = sleepMinutes?.takeIf { it > 0 }?.let { "Slept ${it / 60}h ${it % 60}m." }
        val strain = if (strainLow != null && strainHigh != null) {
            String.format(Locale.US, "Aim for a strain of %.1f–%.1f today.", strainLow, strainHigh)
        } else when (state) {
            RecoveryState.REBUILD -> "Go easy and rest up today."
            else -> "Tap to see today's briefing."
        }
        return Message(
            title = "Recovery $score%, ${stateLabel(state)}",
            body = listOfNotNull(sleep, strain).joinToString(" ")
        )
    }

    fun bedtime(bedtime: LocalTime, minutesAhead: Int): Message = Message(
        title = "Time to wind down",
        body = "Bedtime is in $minutesAhead minutes (${bedtime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))}) to get the sleep you need."
    )
}
