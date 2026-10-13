package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
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
import kotlin.math.roundToInt

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
        goalDate: LocalDate? = null,
        foodLogs: List<FoodLogEntity> = emptyList(),
        weights: List<WeightMeasurementEntity> = emptyList()
    ) = calculator.estimate(
        weightKg = 80.0, heightCm = 180.0, age = 30, sex = BiologicalSex.MALE,
        summaries = summaries, workouts = workouts,
        goalWeightKg = goalWeight, goalDate = goalDate, today = today, zone = zone,
        foodLogs = foodLogs, weights = weights
    )

    private fun meal(offset: Long, kcal: Int) = FoodLogEntity(
        date = today.minusDays(offset), timestamp = today.minusDays(offset).atTime(12, 0).toInstant(zone),
        source = FoodLogSource.MANUAL, foodName = "Meal", calories = kcal,
        proteinG = 0.0, carbsG = 0.0, fatG = 0.0, healthConnectSynced = false
    )

    private fun weighIn(offset: Long, kg: Double) = WeightMeasurementEntity(
        recordFingerprint = "kg$offset", timestamp = today.minusDays(offset).atTime(7, 0).toInstant(zone), weightKg = kg
    )

    /** Weigh-ins every 4 days over the last 28, losing [kgPerWeek] steadily. */
    private fun losing(kgPerWeek: Double) = (1L..28L step 4).map { weighIn(it, 80.0 + kgPerWeek * it / 7.0) }

    private val week = (1L..7L).map { day(it, 10_000) }

    @Test
    fun `maintenance is resting burn plus every step plus workouts`() {
        val e = estimate(week).estimate!!
        assertEquals(1780.0, e.bmrKcal, 0.5)
        // 10,000 steps / 150 = 67 active minutes; BMR only covers the other 1,373.
        assertEquals(67.0, e.avgActiveMinutes, 0.01)
        assertEquals(1780.0 * (1440 - 67) / 1440, e.restingKcal, 0.01)
        // Every step counts: 0.02988 kcal walking + 1780 / 1440 / 150 kcal resting burn per step.
        assertEquals(10_000.0, e.avgCountedSteps, 0.01)
        assertEquals(0.02988 + 1780.0 / 1440 / 150, e.kcalPerStep, 0.0001)
        assertEquals(10_000 * e.kcalPerStep, e.stepKcal, 0.01)
        // Plus 10% thermic effect of food.
        assertEquals((e.restingKcal + e.stepKcal) * 1.10, e.maintenanceKcal, 0.01)
        assertEquals("Mifflin-St Jeor", e.bmrMethod)
    }

    @Test
    fun `today is excluded from the window`() {
        val withToday = week + day(0, 50_000)
        assertEquals(estimate(week).estimate!!.avgTotalSteps, estimate(withToday).estimate!!.avgTotalSteps, 0.01)
    }

    @Test
    fun `workouts count in full and their steps are not counted twice`() {
        // One 30-min run with 350 active kcal and 4,900 steps on one day.
        val e = estimate(week, listOf(run(today.minusDays(2), 30, 350.0, 4_900))).estimate!!
        // Active kcal plus the resting burn during those 30 minutes.
        assertEquals((350.0 + 1780.0 * 30 / 1440) / 7, e.avgWorkoutKcal, 0.01)
        // That day only 5,100 steps are counted as steps.
        assertEquals((6 * 10_000 + 5_100) / 7.0, e.avgCountedSteps, 0.5)
        assertEquals((e.restingKcal + e.stepKcal + e.avgWorkoutKcal) * 1.10, e.maintenanceKcal, 0.01)
    }

    @Test
    fun `workout without calories adds only its steps`() {
        val e = estimate(week, listOf(run(today.minusDays(2), 30, kcal = null, steps = 4_900))).estimate!!
        assertEquals(0.0, e.avgWorkoutKcal, 0.01)
        assertEquals(1, e.workoutsWithoutCalories)
        assertEquals(10_000.0, e.avgCountedSteps, 0.5)
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
        val e = estimate(week, goalWeight = 78.0, goalDate = today.plusDays(56)).estimate!!
        // 2 kg x 7700 / 56 days = 275 kcal/day
        assertNotNull(e.goal?.dailyCalorieTarget)
        assertEquals(-275.0, e.dailyAdjustmentKcal!!, 1.0)
    }

    @Test
    fun `a loss target stops at resting burn`() {
        // 4 kg in 8 weeks would need ~1,780 kcal/day - this BMR - so it stops just above.
        val e = estimate(week, goalWeight = 76.0, goalDate = today.plusDays(56)).estimate!!
        assertEquals(e.bmrKcal.roundToInt(), e.goal!!.dailyCalorieTarget)
        assertTrue(e.goal!!.isPaceAdjustedForSafety)
    }

    @Test
    fun `intake and weight trend are learnt and blended into maintenance`() {
        val formula = estimate(week).estimate!!.maintenanceKcal
        // Eating 2,000 a day while losing 0.25 kg a week means burning 2,000 + 0.25 × 7,700 / 7 = 2,275.
        val e = estimate(week, foodLogs = (1L..28L).map { meal(it, 2000) }, weights = losing(0.25)).estimate!!
        val a = e.adaptive!!
        assertEquals(28, a.loggedDays)
        assertEquals(-0.25, a.weeklyWeightChangeKg, 0.001)
        assertEquals(2275.0, a.maintenanceKcal, 1.0)
        assertEquals(0.85, a.weight, 0.001)
        assertEquals(formula, e.formulaMaintenanceKcal, 0.01)
        assertEquals(formula * 0.15 + 2275.0 * 0.85, e.maintenanceKcal, 0.5)
    }

    @Test
    fun `partly logged days don't count and too few leave the formula alone`() {
        // 12 full days and 16 days with only a snack logged.
        val logs = (1L..12L).map { meal(it, 2000) } + (13L..28L).map { meal(it, 300) }
        val e = estimate(week, foodLogs = logs, weights = losing(0.25)).estimate!!
        assertNull(e.adaptive)
        assertTrue(e.adaptiveMissing!!.contains("2 more fully-logged"))
        assertEquals(e.formulaMaintenanceKcal, e.maintenanceKcal, 0.0)
    }

    @Test
    fun `numbers that don't add up are ignored`() {
        // Eating 1,200 while gaining 1 kg a week would mean burning ~100 kcal/day.
        val e = estimate(week, foodLogs = (1L..28L).map { meal(it, 1200) }, weights = losing(-1.0)).estimate!!
        assertNull(e.adaptive)
        assertEquals(e.formulaMaintenanceKcal, e.maintenanceKcal, 0.0)
    }

    // region Today's target

    @Test
    fun `before today's steps pass a usual day, today's target uses the usual day`() {
        // Today: 2,000 steps so far, no workout. Usual day: 10,000 steps.
        val e = estimate(week + day(0, 2_000)).estimate!!
        val t = e.today!!
        assertEquals(today, t.date)
        assertEquals(false, t.neatFromDay)
        assertEquals(e.stepKcal, t.neatKcal, 0.01)
        assertEquals(0.0, t.workoutKcal, 0.01)
        // No goal and no workouts: a usual day's burn, which is maintenance.
        assertEquals(e.formulaMaintenanceKcal, t.burnKcal, 0.5)
        assertEquals(e.formulaMaintenanceKcal.roundToInt(), t.targetKcal)
    }

    @Test
    fun `a big walking day earns more once it passes the usual`() {
        val e = estimate(week + day(0, 20_000)).estimate!!
        val t = e.today!!
        assertTrue(t.neatFromDay)
        assertEquals(20_000 * e.kcalPerStep, t.neatKcal, 0.5)
        // 133 active minutes from steps; resting covers the rest of the day.
        assertEquals(e.bmrKcal * (1440 - 133) / 1440, t.restingKcal, 0.5)
        assertTrue(t.targetKcal > e.formulaMaintenanceKcal + 250)
    }

    @Test
    fun `today's workout raises today's target as soon as it syncs`() {
        val rest = estimate(week + day(0, 3_000)).estimate!!.today!!
        val trained = estimate(week + day(0, 3_000), workouts = listOf(run(today, 60, 600.0, null))).estimate!!.today!!
        assertEquals(1, trained.workoutsCounted)
        // 600 active kcal + resting burn during the hour.
        assertEquals(600.0 + 1780.0 * 60 / 1440, trained.workoutKcal, 0.5)
        // The hour isn't counted as resting too; digestion adds 10% on top.
        val expected = (trained.workoutKcal - 1780.0 * 60 / 1440) * 1.10
        assertEquals(expected, trained.burnKcal - rest.burnKcal, 1.0)
    }

    @Test
    fun `past workouts don't count toward today`() {
        val e = estimate(week + day(0, 3_000), workouts = listOf(run(today.minusDays(1), 60, 600.0, null))).estimate!!
        assertEquals(0.0, e.today!!.workoutKcal, 0.01)
    }

    @Test
    fun `a goal weight alone sets a default pace deficit on today's burn`() {
        // An active day, so the deficit isn't stopped by the resting-burn floor.
        val t = estimate(week + day(0, 20_000), goalWeight = 75.0).estimate!!.today!!
        // 0.5 kg/week × 7,700 / 7 = 550 kcal/day.
        assertEquals(-550.0, t.adjustmentKcal, 1.0)
        assertEquals(-0.5, t.goal!!.weeklyPaceKg, 0.01)
    }

    @Test
    fun `a target date still sets the pace`() {
        // 2 kg in 70 days = 220 kcal/day.
        val t = estimate(week + day(0, 20_000), goalWeight = 78.0, goalDate = today.plusDays(70)).estimate!!.today!!
        assertEquals(-220.0, t.adjustmentKcal, 1.0)
    }

    @Test
    fun `what the logs have taught scales today's burn`() {
        val logs = (1L..20L).map { meal(it, 2_200) }
        val e = estimate(week + day(0, 3_000), foodLogs = logs, weights = losing(-0.25)).estimate!!
        val t = e.today!!
        assertNotNull(e.adaptive)
        assertEquals(e.maintenanceKcal / e.formulaMaintenanceKcal, t.adaptiveFactor, 0.0001)
        assertEquals(e.maintenanceKcal, t.burnKcal, 0.5)
    }

    // endregion
}
