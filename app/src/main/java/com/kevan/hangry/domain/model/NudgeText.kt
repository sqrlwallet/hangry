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
    /** [restDay] is the day of rest mode (1 = first day), or null when it's off. */
    fun morning(
        score: Int, state: RecoveryState, sleepMinutes: Int?, strainLow: Double?, strainHigh: Double?,
        bodySignals: List<String> = emptyList(), restDay: Int? = null
    ): Message {
        val sleep = sleepMinutes?.takeIf { it > 0 }?.let { "Slept ${it / 60}h ${it % 60}m." }
        val strain = if (restDay != null) {
            "Rest mode, day $restDay: keep it gentle today - your streaks are paused."
        } else if (strainLow != null && strainHigh != null) {
            String.format(Locale.US, "Aim for a strain of %.1f–%.1f today.", strainLow, strainHigh)
        } else when (state) {
            RecoveryState.REBUILD -> "Go easy and rest up today."
            else -> "Tap to see today's briefing."
        }
        // Two or more readings off together: lead with it - it matters more than today's strain.
        val warning = bodySignals.takeIf { it.size >= 2 }?.let {
            "Your body may be fighting something (${it.joinToString(", ")}) - " +
                if (restDay == null) "take it easy, or turn on rest mode in Hangry." else "keep resting."
        }
        return Message(
            title = "Recovery $score%, ${stateLabel(state)}",
            body = listOfNotNull(warning, sleep, strain).joinToString(" ")
        )
    }

    /** VO2 max has stayed below the low line ([lowBelow]) for your age and sex. */
    fun cardioLow(vo2Max: Double, lowBelow: Double?): Message = Message(
        title = "Your cardio fitness is low",
        body = String.format(Locale.US, "Your VO2 max is %.1f", vo2Max) +
            (lowBelow?.let { String.format(Locale.US, ", below %.0f, the low line for your age and sex.", it) } ?: ".") +
            " Regular brisk walks, cycling or swimming can raise it within weeks. If you also get breathless or tired more easily than usual, check in with a doctor."
    )

    /** VO2 max is [drop] lower than three months ago. */
    fun cardioDeclining(vo2Max: Double, drop: Double): Message = Message(
        title = "Your cardio fitness has dropped",
        body = String.format(Locale.US, "Your VO2 max is down %.1f to %.1f over the last 3 months.", drop, vo2Max) +
            " Less training, illness or weight change can all do this - a few weeks of regular cardio usually brings it back."
    )

    fun bedtime(bedtime: LocalTime, minutesAhead: Int): Message = Message(
        title = "Time to wind down",
        body = "Bedtime is in $minutesAhead minutes (${bedtime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))}) to get the sleep you need."
    )
}
