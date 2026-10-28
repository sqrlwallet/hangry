package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.RestModeState
import com.kevan.hangry.domain.model.RestPeriod
import com.kevan.hangry.domain.model.RestReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RestModeTest {

    private val today = LocalDate.of(2026, 10, 28)
    private fun days(vararg ago: Long) = ago.map { today.minusDays(it) }.toSet()

    // region Rest periods

    @Test
    fun startingAndEndingKeepsTheRestDays() {
        val resting = RestModeState().started(today.minusDays(3), RestReason.UNWELL)
        assertEquals(RestReason.UNWELL, resting.active?.reason)
        assertEquals(4, resting.active?.dayNumber(today))

        val ended = resting.ended(today)
        assertNull(ended.active)
        assertEquals(days(0, 1, 2, 3), ended.restDays(today.minusDays(10), today))
        assertFalse(ended.isRestDay(today.plusDays(1)))
    }

    @Test
    fun startingWhileRestingOnlyChangesTheReason() {
        val resting = RestModeState().started(today.minusDays(2), RestReason.UNWELL).started(today, RestReason.INJURY)
        assertEquals(1, resting.periods.size)
        assertEquals(RestPeriod(today.minusDays(2), null, RestReason.INJURY), resting.active)
    }

    @Test
    fun switchedOnAndOffTheSameDayIsForgotten() {
        val slip = RestModeState().started(today, RestReason.RECOVERY).ended(today)
        assertTrue(slip.periods.isEmpty())
    }

    @Test
    fun anOngoingPeriodCoversEveryDayUpToToday() {
        val resting = RestModeState().started(today.minusDays(1), RestReason.UNWELL)
        assertEquals(days(0, 1), resting.restDays(today.minusDays(30), today))
    }

    @Test
    fun periodsSurviveStorage() {
        val state = RestModeState()
            .started(today.minusDays(20), RestReason.INJURY).ended(today.minusDays(15))
            .started(today.minusDays(1), RestReason.UNWELL)
        assertEquals(state, RestModeState.decode(state.encode()))
        assertEquals(RestModeState(), RestModeState.decode(null))
        // A broken entry is skipped, not a crash.
        assertEquals(1, RestModeState.decode("garbage;2026-10-01||UNWELL").periods.size)
    }

    // endregion

    // region Streaks

    @Test
    fun restDaysDoNotBreakAStreak() {
        // Met the goal 1, 2, then rested 3-5 without meeting it, and met it 6 and 7 days ago.
        val s = StreakCalculator.streak(StreakType.STEPS, days(1, 2, 6, 7), today, restDays = days(3, 4, 5))
        assertEquals(4, s.current)
        assertEquals(today.minusDays(7), s.startedOn)
        assertEquals(4, s.best)
    }

    @Test
    fun aGoalMetOnARestDayStillCounts() {
        val s = StreakCalculator.streak(StreakType.SLEEP, days(1, 2, 3), today, restDays = days(1, 2))
        assertEquals(3, s.current)
    }

    @Test
    fun restingTodayPausesRatherThanRisks() {
        val s = StreakCalculator.streak(StreakType.MEALS, days(1, 2), today, restDays = days(0))
        assertTrue(s.paused)
        assertEquals(2, s.current)
        val done = StreakCalculator.streak(StreakType.MEALS, days(0, 1), today, restDays = days(0))
        assertFalse(done.paused)
    }

    @Test
    fun withoutRestAMissedDayStillBreaks() {
        val s = StreakCalculator.streak(StreakType.STEPS, days(1, 3, 4), today)
        assertEquals(1, s.current)
        assertFalse(s.paused)
    }

    @Test
    fun longestRunBridgesRestDays() {
        assertEquals(4, StreakCalculator.longestRun(days(10, 11, 14, 15), restDays = days(12, 13)))
        assertEquals(2, StreakCalculator.longestRun(days(10, 11, 14, 15)))
    }

    // endregion

    // region Strain and the morning brief

    @Test
    fun restModeKeepsTheStrainTargetGentle() {
        val calculator = HangryStrainCalculator(com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator())
        val primed = calculator.recommendStrainTarget(RecoveryState.PRIMED, listOf(10.0, 10.0))
        val resting = calculator.recommendStrainTarget(RecoveryState.PRIMED, listOf(10.0, 10.0), resting = true)
        assertEquals(0.0, resting.targetLow, 0.001)
        assertEquals(3.0, resting.targetHigh, 0.001)
        assertTrue(resting.targetHigh < primed.targetLow)
        assertTrue(resting.guidance.contains("Rest mode"))
    }

    @Test
    fun morningBriefSaysRestModeInsteadOfAStrainTarget() {
        val m = NudgeText.morning(62, RecoveryState.BALANCED, 480, 0.0, 3.0, restDay = 2)
        assertEquals("Slept 8h 0m. Rest mode, day 2: keep it gentle today - your streaks are paused.", m.body)
    }

    @Test
    fun bodySignalsSuggestRestModeOnlyWhenItIsOff() {
        val signals = listOf("resting heart rate +4 bpm", "HRV -25%")
        assertTrue(NudgeText.morning(48, RecoveryState.BALANCED, 420, null, null, signals).body.contains("turn on rest mode"))
        assertTrue(NudgeText.morning(48, RecoveryState.BALANCED, 420, null, null, signals, restDay = 1).body.contains("keep resting"))
    }

    // endregion
}
