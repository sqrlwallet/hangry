package com.kevan.hangry.domain.model

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class BreathPhaseType(val label: String) {
    INHALE("Breathe in"),
    HOLD_FULL("Hold"),
    EXHALE("Breathe out"),
    HOLD_EMPTY("Hold")
}

data class BreathPhase(val type: BreathPhaseType, val seconds: Int)

/**
 * Guided breathing patterns. Each pattern is one repeating cycle of phases; a tone plays at
 * the start of every phase so the session can be followed eyes-closed with headphones.
 * [id] is persisted in Room - never rename an existing one.
 */
enum class BreathingPattern(
    val id: String,
    val title: String,
    val shortLabel: String,
    val description: String,
    val phases: List<BreathPhase>
) {
    BOX_4(
        id = "box_4",
        title = "Box Breathing",
        shortLabel = "4s Box",
        description = "Inhale 4 · hold 4 · exhale 4 · hold 4. Steadies focus and calms stress.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 4),
            BreathPhase(BreathPhaseType.HOLD_FULL, 4),
            BreathPhase(BreathPhaseType.EXHALE, 4),
            BreathPhase(BreathPhaseType.HOLD_EMPTY, 4)
        )
    ),
    BPM_5(
        id = "bpm_5",
        title = "5 BPM Resonance",
        shortLabel = "5 BPM",
        description = "Inhale 6 · exhale 6. Close to most people's resonance frequency for peak HRV.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 6),
            BreathPhase(BreathPhaseType.EXHALE, 6)
        )
    ),
    BPM_6(
        id = "bpm_6",
        title = "6 BPM Coherence",
        shortLabel = "6 BPM",
        description = "Inhale 5 · exhale 5. The classic coherent-breathing pace - an easy place to start.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 5),
            BreathPhase(BreathPhaseType.EXHALE, 5)
        )
    ),
    BPM_3(
        id = "bpm_3",
        title = "3 BPM Deep Calm",
        shortLabel = "3 BPM",
        description = "Inhale 10 · exhale 10. Very slow and deep - best for winding down before sleep.",
        phases = listOf(
            BreathPhase(BreathPhaseType.INHALE, 10),
            BreathPhase(BreathPhaseType.EXHALE, 10)
        )
    );

    val cycleSeconds: Int get() = phases.sumOf { it.seconds }

    val breathsPerMinute: Double get() = 60.0 / cycleSeconds

    /** Rounds a requested duration up to whole cycles so a session never ends mid-breath. */
    fun sessionSecondsFor(minutes: Int): Int {
        val cycles = ceil(minutes * 60.0 / cycleSeconds).toInt().coerceAtLeast(1)
        return cycles * cycleSeconds
    }

    fun cyclesIn(elapsedSeconds: Int): Int = (elapsedSeconds.toDouble() / cycleSeconds).roundToInt()

    companion object {
        fun fromId(id: String?): BreathingPattern = entries.firstOrNull { it.id == id } ?: BPM_6

        val DURATION_OPTIONS_MINUTES = listOf(1, 3, 5, 10, 15, 20)
    }
}

/** Where the session currently is in its schedule, derived purely from elapsed time. */
data class BreathPosition(
    val cycleIndex: Int,
    val phaseIndex: Int,
    val phase: BreathPhase,
    val phaseElapsedMs: Long
) {
    val phaseProgress: Float get() = (phaseElapsedMs / (phase.seconds * 1000f)).coerceIn(0f, 1f)
    val phaseSecondsRemaining: Int get() = ceil((phase.seconds * 1000 - phaseElapsedMs) / 1000.0).toInt().coerceAtLeast(1)
}

fun BreathingPattern.positionAt(elapsedMs: Long): BreathPosition {
    val cycleMs = cycleSeconds * 1000L
    val safeElapsed = elapsedMs.coerceAtLeast(0)
    val cycleIndex = (safeElapsed / cycleMs).toInt()
    var withinCycle = safeElapsed % cycleMs
    phases.forEachIndexed { index, phase ->
        val phaseMs = phase.seconds * 1000L
        if (withinCycle < phaseMs) return BreathPosition(cycleIndex, index, phase, withinCycle)
        withinCycle -= phaseMs
    }
    // Unreachable (withinCycle < cycleMs), kept for exhaustiveness.
    return BreathPosition(cycleIndex, phases.lastIndex, phases.last(), phases.last().seconds * 1000L)
}

data class BreathingStats(
    val minutesToday: Int = 0,
    val sessionsToday: Int = 0,
    val minutesThisWeek: Int = 0
)
