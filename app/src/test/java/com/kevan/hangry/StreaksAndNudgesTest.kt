package com.kevan.hangry

import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakCalculator.DoseSchedule
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.domain.model.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StreaksAndNudgesTest {

    private val today = LocalDate.of(2026, 9, 24)
    private fun days(vararg ago: Long) = ago.map { today.minusDays(it) }.toSet()

    @Test
    fun streakCountsBackFromTodayWhenDone() {
        val s = StreakCalculator.streak(StreakType.STEPS, days(0, 1, 2, 3, 5), today)
        assertEquals(4, s.current)
        assertTrue(s.doneToday)
        assertEquals(today.minusDays(3), s.startedOn)
    }

    @Test
    fun todayNotDoneYetDoesNotBreakTheStreak() {
        val s = StreakCalculator.streak(StreakType.MEALS, days(1, 2, 3), today)
        assertEquals(3, s.current)
        assertFalse(s.doneToday)
    }

    @Test
    fun missedYesterdayEndsTheStreak() {
        val s = StreakCalculator.streak(StreakType.SLEEP, days(2, 3, 4), today)
        assertEquals(0, s.current)
        assertNull(s.startedOn)
        assertEquals(3, s.best)
    }

    @Test
    fun bestIsTheLongestRunEver() {
        assertEquals(5, StreakCalculator.longestRun(days(1, 2, 10, 11, 12, 13, 14, 20)))
        assertEquals(0, StreakCalculator.longestRun(emptySet()))
    }

    @Test
    fun milestonesReachedAreAllAtOrBelowTheStreak() {
        val s = StreakCalculator.streak(StreakType.STEPS, (0L..34L).map { today.minusDays(it) }.toSet(), today)
        assertEquals(listOf(7, 14, 30), StreakCalculator.milestonesReached(s))
    }

    @Test
    fun supplementDayCountsOnlyWhenEveryDueDoseWasTaken() {
        val schedules = listOf(
            DoseSchedule(1, today.minusDays(10), listOf("08:00", "20:00")),
            // Added two days ago: not expected before then.
            DoseSchedule(2, today.minusDays(2), listOf("08:00"))
        )
        val taken = setOf(
            Triple(1L, today.minusDays(3), "08:00"), Triple(1L, today.minusDays(3), "20:00"), // full day, before #2 existed
            Triple(1L, today.minusDays(2), "08:00"), Triple(1L, today.minusDays(2), "20:00"), // missing #2's dose
            Triple(1L, today.minusDays(1), "08:00"), Triple(1L, today.minusDays(1), "20:00"), Triple(2L, today.minusDays(1), "08:00")
        )
        val full = StreakCalculator.fullyTakenDays(schedules, taken, today.minusDays(3), today)
        assertEquals(setOf(today.minusDays(3), today.minusDays(1)), full)
    }

    @Test
    fun morningMessageUsesTheRealScoreAndStrainTarget() {
        val m = NudgeText.morning(74, RecoveryState.PRIMED, 440, 12.0, 15.0)
        assertEquals("Recovery 74%, primed", m.title)
        assertEquals("Slept 7h 20m. Aim for a strain of 12.0–15.0 today.", m.body)
        val rebuild = NudgeText.morning(31, RecoveryState.REBUILD, null, null, null)
        assertEquals("Go easy and rest up today.", rebuild.body)
    }

    @Test
    fun bedtimeMessageShowsTheBedtime() {
        val m = NudgeText.bedtime(LocalTime.of(22, 45), 30)
        assertEquals("Bedtime is in 30 minutes (10:45 PM) to get the sleep you need.", m.body)
    }
}
