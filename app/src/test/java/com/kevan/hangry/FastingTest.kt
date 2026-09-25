package com.kevan.hangry

import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.Fast
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingPlan
import com.kevan.hangry.domain.model.FastingStage
import com.kevan.hangry.ui.fasting.FastingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class FastingTest {

    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(2026, 9, day, hour, minute).toInstant(zone)

    private fun fast(startDay: Int, startHour: Int, hours: Long?, target: Int = 16, id: Long = 0) =
        Fast(id, at(startDay, startHour), hours?.let { at(startDay, startHour).plus(Duration.ofHours(it)) }, target * 60, "16_8")

    @Test
    fun `a fast that reaches its goal counts on the day it got there`() {
        // 8 PM on the 20th + 16h = noon on the 21st.
        val days = FastingMath.goalDays(listOf(fast(20, 20, 17)), zone, now = at(25, 0))
        assertEquals(setOf(LocalDate.of(2026, 9, 21)), days)
    }

    @Test
    fun `ending early doesn't count toward the streak`() {
        assertTrue(FastingMath.goalDays(listOf(fast(20, 20, 12)), zone, now = at(25, 0)).isEmpty())
    }

    @Test
    fun `a running fast counts once it passes the goal`() {
        val running = fast(20, 20, null)
        assertTrue(FastingMath.goalDays(listOf(running), zone, now = at(21, 11)).isEmpty())
        assertEquals(setOf(LocalDate.of(2026, 9, 21)), FastingMath.goalDays(listOf(running), zone, now = at(21, 12)))
    }

    @Test
    fun `daily 16-8 fasts build a streak`() {
        val fasts = (18..21).map { fast(it, 20, 16) }
        val days = FastingMath.goalDays(fasts, zone, now = at(25, 0))
        val streak = StreakCalculator.streak(StreakType.FASTING, days, LocalDate.of(2026, 9, 22))
        assertEquals(4, streak.current)
        assertTrue(streak.doneToday)
    }

    @Test
    fun `stages follow hours fasted`() {
        assertEquals(FastingStage.FED, FastingStage.at(Duration.ofHours(1)))
        assertEquals(FastingStage.FAT_BURNING, FastingStage.at(Duration.ofHours(13)))
        assertEquals(FastingStage.KETOSIS, FastingStage.at(Duration.ofHours(16)))
        assertEquals(FastingStage.DEEP, FastingStage.at(Duration.ofHours(30)))
    }

    @Test
    fun `progress is capped at the goal`() {
        val f = fast(20, 20, null)
        assertEquals(0.5f, f.progress(at(21, 4)), 0.001f)
        assertEquals(1f, f.progress(at(22, 4)), 0.001f)
        assertFalse(f.reachedGoal(at(21, 4)))
    }

    @Test
    fun `a start time later than now means yesterday`() {
        val now = at(21, 7)
        assertEquals(at(20, 20), FastingViewModel.mostRecent(LocalTime.of(20, 0), now, zone))
        assertEquals(at(21, 6), FastingViewModel.mostRecent(LocalTime.of(6, 0), now, zone))
    }

    @Test
    fun `plans parse from their ids and fall back to 16-8`() {
        assertEquals(FastingPlan.EIGHTEEN, FastingPlan.fromId("18_6"))
        assertEquals(FastingPlan.SIXTEEN, FastingPlan.fromId(null))
        assertEquals("16h 5m", FastingMath.formatDuration(Duration.ofMinutes(965)))
        assertEquals("45m", FastingMath.formatDuration(Duration.ofMinutes(45)))
    }
}
