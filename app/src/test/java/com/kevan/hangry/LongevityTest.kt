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

    private fun workout(day: LocalDate, type: String, id: String, minutes: Int = 60) = ExerciseSessionEntity(
        recordFingerprint = id, exerciseType = type,
        startTime = day.atTime(7, 0).toInstant(zone), endTime = day.atTime(7, 0).plusMinutes(minutes.toLong()).toInstant(zone),
        durationMinutes = minutes
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
    fun `strength sessions and mobility minutes count, breathing doesn't`() {
        val workouts = listOf(
            workout(monday, "WEIGHTLIFTING", "a"),
            workout(monday.plusDays(2), "STRENGTH_TRAINING", "b"),
            workout(monday.plusDays(1), "YOGA", "c", minutes = 30),
            workout(monday.plusDays(3), "GUIDED_BREATHING", "d"),
            workout(monday.minusDays(1), "WEIGHTLIFTING", "last-week")
        )
        val week = LongevityCalculator.week(monday, workouts, emptyList(), emptyList(), zones, emptyMap(), zone)
        assertEquals(2, week.of(LongevityPillar.STRENGTH).value)
        assertEquals(30, week.of(LongevityPillar.MOBILITY).value)
        assertEquals(setOf(monday.plusDays(1)), week.of(LongevityPillar.MOBILITY).days)
    }

    @Test
    fun `three strength sessions meet the target`() {
        val workouts = (0L..2L).map { workout(monday.plusDays(it * 2), "STRENGTH_TRAINING", "s$it") }
        val week = LongevityCalculator.week(monday, workouts, emptyList(), emptyList(), zones, emptyMap(), zone)
        assertTrue(week.of(LongevityPillar.STRENGTH).met)
    }

    @Test
    fun `an hour of yoga meets both mobility and balance`() {
        val workouts = listOf(workout(monday, "YOGA", "y1", minutes = 40), workout(monday.plusDays(3), "PILATES", "p1", minutes = 20))
        val week = LongevityCalculator.week(monday, workouts, emptyList(), emptyList(), zones, emptyMap(), zone)
        assertEquals(60, week.of(LongevityPillar.MOBILITY).value)
        assertEquals(60, week.of(LongevityPillar.BALANCE).value)
        assertTrue(week.of(LongevityPillar.MOBILITY).met)
        assertTrue(week.of(LongevityPillar.BALANCE).met)
        assertEquals(setOf(monday, monday.plusDays(3)), week.of(LongevityPillar.BALANCE).days)
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
    fun `each tick-off adds minutes, and a met target counts as done`() {
        val days = (0L..5L).map { monday.plusDays(it) }.toSet()
        val week = LongevityCalculator.week(monday, emptyList(), emptyList(), emptyList(), zones, mapOf(LongevityPillar.BALANCE to days), zone)
        assertEquals(6 * LongevityCalculator.TICK_MINUTES, week.of(LongevityPillar.BALANCE).value)
        assertTrue(week.of(LongevityPillar.BALANCE).met)
        assertEquals(0, week.of(LongevityPillar.MOBILITY).value)
        assertEquals(1, week.pillarsMet)
    }
}
