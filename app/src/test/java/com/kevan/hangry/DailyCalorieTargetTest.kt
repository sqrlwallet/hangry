package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.DailyCalorieTargetCalculator
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.model.AdaptiveMaintenance
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

class DailyCalorieTargetTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val calorieCalculator = HangryCalorieCalculator()
    private val bmr = 1_700.0
    private val weightKg = 80.0
    private val heightCm = 180.0
    private val kcalPerStep = ActiveActivityCalculator.kcalPerStep(weightKg, heightCm, bmr)

    // A usual day: 6,000 counted steps, 40 minutes of walking.
    private fun estimate(
        adaptive: AdaptiveMaintenance? = null,
        formulaMaintenanceKcal: Double = 2_500.0,
        maintenanceKcal: Double = 2_500.0,
        goalWeightKg: Double? = null,
        goalDate: LocalDate? = null
    ) = EnergyBalanceEstimate(
        windowStart = today.minusDays(7),
        windowEnd = today.minusDays(1),
        daysWithData = 7,
        bmrKcal = bmr,
        bmrMethod = "Mifflin-St Jeor",
        restingKcal = 1_650.0,
        avgActiveMinutes = 40.0,
        avgTotalSteps = 6_000.0,
        avgCountedSteps = 6_000.0,
        kcalPerStep = kcalPerStep,
        stepKcal = 6_000 * kcalPerStep,
        avgWorkoutKcal = 0.0,
        workoutsCounted = 0,
        workoutsWithoutCalories = 0,
        tefKcal = 0.0,
        formulaMaintenanceKcal = formulaMaintenanceKcal,
        adaptive = adaptive,
        adaptiveMissing = null,
        maintenanceKcal = maintenanceKcal,
        goalWeightKg = goalWeightKg,
        goalDate = goalDate,
        goal = null,
        weightKg = weightKg,
        heightCm = heightCm,
        sex = BiologicalSex.MALE,
        avgStepMinutes = 40.0
    )

    private fun workout(minutes: Int, totalCalories: Double? = null) = ExerciseSessionEntity(
        recordFingerprint = "w$minutes$totalCalories",
        exerciseType = "RUNNING",
        startTime = Instant.parse("2026-10-13T07:00:00Z"),
        endTime = Instant.parse("2026-10-13T07:00:00Z").plusSeconds(minutes * 60L),
        durationMinutes = minutes,
        totalCalories = totalCalories
    )

    private fun calculate(
        estimate: EnergyBalanceEstimate = estimate(),
        steps: Long? = null,
        workouts: List<ExerciseSessionEntity> = emptyList()
    ) = DailyCalorieTargetCalculator.calculate(estimate, today, steps, workouts, calorieCalculator)

    @Test
    fun `before any steps the usual day stands in for NEAT`() {
        val target = calculate(steps = null)

        assertFalse(target.neatFromDay)
        assertEquals(6_000 * kcalPerStep, target.neatKcal, 0.01)
        assertEquals(6_000.0, target.neatSteps, 0.01)
        val resting = bmr * (1440 - 40) / 1440
        assertEquals(resting, target.restingKcal, 0.01)
        assertEquals((resting + target.neatKcal) / 0.9, target.burnKcal, 0.01)
        assertEquals(target.burnKcal.roundToInt(), target.targetKcal)
        assertNull(target.goal)
    }

    @Test
    fun `a quiet day so far still uses the usual day`() {
        val usual = calculate(steps = null)
        val quiet = calculate(steps = 2_000)

        assertFalse(quiet.neatFromDay)
        assertEquals(usual.burnKcal, quiet.burnKcal, 0.01)
    }

    @Test
    fun `a bigger walking day than usual earns more`() {
        val usual = calculate(steps = null)
        val target = calculate(steps = 15_000)

        assertTrue(target.neatFromDay)
        assertEquals(15_000 * kcalPerStep, target.neatKcal, 0.01)
        assertEquals(15_000.0, target.neatSteps, 0.5)
        // 15,000 steps is 100 active minutes, taken out of the resting share.
        assertEquals(bmr * (1440 - 100) / 1440, target.restingKcal, 0.01)
        assertTrue(target.burnKcal > usual.burnKcal)
    }

    @Test
    fun `workouts count in full and their minutes leave the resting share`() {
        val target = calculate(workouts = listOf(workout(60, totalCalories = 600.0)))

        assertEquals(600.0, target.workoutKcal, 0.01)
        assertEquals(1, target.workoutsCounted)
        assertEquals(0, target.workoutsWithoutCalories)
        assertEquals(bmr * (1440 - 40 - 60) / 1440, target.restingKcal, 0.01)
        assertEquals((target.restingKcal + target.neatKcal + 600.0) / 0.9, target.burnKcal, 0.01)
    }

    @Test
    fun `a workout without calorie data adds nothing but is counted as missing`() {
        val target = calculate(workouts = listOf(workout(30, totalCalories = 400.0), workout(45)))

        assertEquals(400.0, target.workoutKcal, 0.01)
        assertEquals(1, target.workoutsCounted)
        assertEquals(1, target.workoutsWithoutCalories)
    }

    @Test
    fun `what the logs have taught scales the burn`() {
        val formula = calculate()
        val learnt = AdaptiveMaintenance(
            windowStart = today.minusDays(28),
            windowEnd = today.minusDays(1),
            loggedDays = 21,
            avgIntakeKcal = 2_300.0,
            weighIns = 20,
            weeklyWeightChangeKg = 0.0,
            maintenanceKcal = 2_300.0,
            weight = 1.0
        )
        val target = calculate(estimate(adaptive = learnt, maintenanceKcal = 2_250.0))

        assertEquals(0.9, target.adaptiveFactor, 1e-9)
        assertEquals(formula.burnKcal * 0.9, target.burnKcal, 0.01)
    }

    @Test
    fun `without a learnt correction the formula stands`() {
        // Maintenance differing from the formula alone doesn't scale anything.
        val target = calculate(estimate(maintenanceKcal = 2_250.0))
        assertEquals(1.0, target.adaptiveFactor, 1e-9)
    }

    @Test
    fun `a loss goal puts the target below the day's burn`() {
        val target = calculate(estimate(goalWeightKg = 75.0), steps = 8_000)

        assertNotNull(target.goal)
        assertEquals(target.goal!!.dailyCalorieTarget, target.targetKcal)
        assertTrue(target.targetKcal < target.burnKcal)
    }

    @Test
    fun `a goal whose date has passed falls back to the day's burn`() {
        val target = calculate(estimate(goalWeightKg = 75.0, goalDate = today.minusDays(1)))

        assertNull(target.goal?.dailyCalorieTarget)
        assertEquals(target.burnKcal.roundToInt(), target.targetKcal)
    }
}
