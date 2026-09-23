package com.kevan.hangry

import com.kevan.hangry.domain.model.BreathPhaseType
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.positionAt
import org.junit.Assert.assertEquals
import org.junit.Test

class BreathingPatternTest {

    @Test
    fun `patterns breathe at their advertised rate`() {
        assertEquals(6.0, BreathingPattern.BPM_6.breathsPerMinute, 0.001)
        assertEquals(5.0, BreathingPattern.BPM_5.breathsPerMinute, 0.001)
        assertEquals(3.0, BreathingPattern.BPM_3.breathsPerMinute, 0.001)
        assertEquals(16, BreathingPattern.BOX_4.cycleSeconds)
    }

    @Test
    fun `session length rounds up to whole breaths`() {
        // 60s of 16s box cycles -> 4 cycles = 64s, never ending mid-breath.
        assertEquals(64, BreathingPattern.BOX_4.sessionSecondsFor(1))
        assertEquals(300, BreathingPattern.BPM_6.sessionSecondsFor(5))
        assertEquals(60, BreathingPattern.BPM_3.sessionSecondsFor(1))
    }

    @Test
    fun `position follows the phase schedule`() {
        val box = BreathingPattern.BOX_4
        assertEquals(BreathPhaseType.INHALE, box.positionAt(0).phase.type)
        assertEquals(BreathPhaseType.HOLD_FULL, box.positionAt(4_000).phase.type)
        assertEquals(BreathPhaseType.EXHALE, box.positionAt(8_500).phase.type)
        assertEquals(BreathPhaseType.HOLD_EMPTY, box.positionAt(15_999).phase.type)

        val next = box.positionAt(16_000)
        assertEquals(1, next.cycleIndex)
        assertEquals(0, next.phaseIndex)
    }

    @Test
    fun `phase progress and countdown`() {
        val position = BreathingPattern.BPM_6.positionAt(7_500) // 2.5s into the 5s exhale
        assertEquals(BreathPhaseType.EXHALE, position.phase.type)
        assertEquals(0.5f, position.phaseProgress, 0.001f)
        assertEquals(3, position.phaseSecondsRemaining)
    }

    @Test
    fun `unknown ids fall back to 6 BPM`() {
        assertEquals(BreathingPattern.BPM_6, BreathingPattern.fromId("nope"))
        assertEquals(BreathingPattern.BOX_4, BreathingPattern.fromId("box_4"))
    }
}
