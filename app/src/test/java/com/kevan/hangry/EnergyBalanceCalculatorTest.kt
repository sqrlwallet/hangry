package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.EnergyBalanceCalculator
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EnergyBalanceCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 23)
    private val calculator = EnergyBalanceCalculator(HangryCalorieCalculator())

    private fun day(offset: Long, steps: Long?) =
        DailyHealthSummaryEntity(date = today.minusDays(offset), steps = steps)

    private fun run(date: LocalDate, minutes: Int, kcal: Double?, steps: Long?, type: String = "RUNNING") =
        ExerciseSessionEntity(
            recordFingerprint = "w$date$minutes",
            exerciseType = type,
            startTime = date.atTime(7, 0).toInstant(zone),
            endTime = date.atTime(7, 0).plusMinutes(minutes.toLong()).toInstant(zone),
            durationMinutes = minutes,
            activeCalories = kcal,
            steps = steps
        )

    private fun estimate(
        summaries: List<DailyHealthSummaryEntity>,
        workouts: List<ExerciseSessionEntity> = emptyList(),
        goalWeight: Double? = null,
        goalDate: LocalDate? = null
    ) = calculator.estimate(
        weightKg = 80.0, heightCm = 180.0, age = 30, sex = BiologicalSex.MALE,
        summaries = summaries, workouts = workouts,
        goalWeightKg = goalWeight, goalDate = goalDate, today = today, zone = zone
    )

    private val week = (1L..7L).map { day(it, 10_000) }

    @Test
    fun `maintenance is BMR plus NEAT plus workouts`() {
        val e = estimate(week).estimate!!
        assertEquals(1780.0, e.bmrKcal, 0.5)
        // A third of 10,000 steps counts toward NEAT.
        assertEquals(10_000.0 / 3, e.avgNeatSteps, 0.01)
        // 0.5 x 80 kg x (180 cm x 0.415 stride) = 0.02988 kcal per step
        assertEquals(99.6, e.neatKcal, 0.5)
        // Plus 10% thermic effect of food.
        assertEquals((e.bmrKcal + e.neatKcal) * 1.10, e.maintenanceKcal, 0.01)
        assertEquals("Mifflin-St Jeor", e.bmrMethod)
    }

    @Test
    fun `today is excluded from the window`() {
        val withToday = week + day(0, 50_000)
        assertEquals(estimate(week).estimate!!.avgTotalSteps, estimate(withToday).estimate!!.avgTotalSteps, 0.01)
    }

    @Test
    fun `workout calories are added on top of a third of steps`() {
        // One 30-min run worth 350 kcal on one day.
        val e = estimate(week, listOf(run(today.minusDays(2), 30, 350.0, 4_900))).estimate!!
        assertEquals(10_000.0 / 3, e.avgNeatSteps, 0.01)
        assertEquals(350.0 / 7, e.avgWorkoutKcal, 0.01)
        assertEquals((e.bmrKcal + e.neatKcal + 50.0) * 1.10, e.maintenanceKcal, 0.01)
    }

    @Test
    fun `workout without calories adds nothing`() {
        val e = estimate(week, listOf(run(today.minusDays(2), 30, kcal = null, steps = 4_900))).estimate!!
        assertEquals(0.0, e.avgWorkoutKcal, 0.01)
        assertEquals(1, e.workoutsWithoutCalories)
    }

    @Test
    fun `a measured body fat scan switches BMR to Katch-McArdle`() {
        val e = calculator.estimate(
            weightKg = 80.0, heightCm = 180.0, age = 30, sex = BiologicalSex.MALE,
            summaries = week, workouts = emptyList(), goalWeightKg = null, goalDate = null,
            today = today, zone = zone, measuredBodyFatPercent = 15.0
        ).estimate!!
        // 370 + 21.6 x 68 kg lean
        assertEquals(1838.8, e.bmrKcal, 0.1)
        assertEquals("Katch-McArdle", e.bmrMethod)
    }

    @Test
    fun `too little step data leaves it blank`() {
        // Four days is below the five-day minimum.
        val result = estimate((1L..4L).map { day(it, 8_000) })
        assertNull(result.estimate)
        assertTrue(result.missing.any { it.startsWith("Step data") })
    }

    @Test
    fun `goal date gives a daily deficit`() {
        val e = estimate(week, goalWeight = 76.0, goalDate = today.plusDays(56)).estimate!!
        // 4 kg x 7700 / 56 days = 550 kcal/day
        assertNotNull(e.goal?.dailyCalorieTarget)
        assertEquals(-550.0, e.dailyAdjustmentKcal!!, 1.0)
    }
}
