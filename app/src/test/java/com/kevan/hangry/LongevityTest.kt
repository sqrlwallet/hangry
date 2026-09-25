package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.calculation.LongevityCalculator
import com.kevan.hangry.domain.model.LongevityPillar
import com.kevan.hangry.domain.model.LongevityWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LongevityTest {

    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 9, 21)
    private val zones = HeartRateZones.forUser(40, null, 60.0) // max 180; zone 2 = 132-143, zone 4 = 156+

    private fun workout(day: LocalDate, type: String, id: String) = ExerciseSessionEntity(
        recordFingerprint = id, exerciseType = type,
        startTime = day.atTime(7, 0).toInstant(zone), endTime = day.atTime(8, 0).toInstant(zone), durationMinutes = 60
    )

    private fun minutes(day: LocalDate, hour: Int, count: Int, bpm: Double) = (0 until count).map {
        HeartRateSampleEntity(recordFingerprint = "hr-$day-$hour-$it", timestamp = day.atTime(hour, 0).plusMinutes(it.toLong()).toInstant(zone), bpm = bpm)
    }

    @Test
    fun `weeks start on Monday`() {
        assertEquals(monday, LongevityWeek.weekStart(LocalDate.of(2026, 9, 27)))
        assertEquals(monday, LongevityWeek.weekStart(monday))
    }

    @Test
    fun `strength sessions and mobility workouts count, breathing doesn't`() {
        val workouts = listOf(
            workout(monday, "WEIGHTLIFTING", "a"),
            workout(monday.plusDays(2), "STRENGTH_TRAINING", "b"),
            workout(monday.plusDays(1), "YOGA", "c"),
            workout(monday.plusDays(3), "GUIDED_BREATHING", "d"),
            workout(monday.minusDays(1), "WEIGHTLIFTING", "last-week")
        )
        val week = LongevityCalculator.week(monday, workouts, emptyList(), emptyList(), zones, emptyMap(), zone)
        assertEquals(2, week.of(LongevityPillar.STRENGTH).value)
        assertEquals(1, week.of(LongevityPillar.MOBILITY).value)
    }

    @Test
    fun `cardio minutes come from personal heart-rate zones`() {
        val hr = minutes(monday, 18, 61, 138.0) + minutes(monday.plusDays(1), 18, 21, 165.0)
        val week = LongevityCalculator.week(monday, emptyList(), hr, emptyList(), zones, emptyMap(), zone)
        assertEquals(60, week.of(LongevityPillar.ZONE2).value)
        assertEquals(20, week.of(LongevityPillar.HIGH_INTENSITY).value)
        assertTrue(week.of(LongevityPillar.ZONE2).measurable)
    }

    @Test
    fun `without heart rate, cardio pillars say so instead of reading zero`() {
        val week = LongevityCalculator.week(monday, emptyList(), emptyList(), emptyList(), zones, emptyMap(), zone)
        assertFalse(week.of(LongevityPillar.ZONE2).measurable)
    }

    @Test
    fun `balance comes from tick-offs, and a met target counts as done`() {
        val days = (0L..6L).map { monday.plusDays(it) }.toSet()
        val week = LongevityCalculator.week(monday, emptyList(), emptyList(), emptyList(), zones, mapOf(LongevityPillar.BALANCE to days), zone)
        assertEquals(7, week.of(LongevityPillar.BALANCE).value)
        assertTrue(week.of(LongevityPillar.BALANCE).met)
        assertEquals(1, week.pillarsMet)
    }
}
