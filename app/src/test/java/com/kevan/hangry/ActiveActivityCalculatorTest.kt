package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActiveActivityCalculatorTest {

    private fun workout(minutes: Int, total: Double? = null, active: Double? = null, steps: Long? = null) = ExerciseSessionEntity(
        recordFingerprint = "w-$minutes-$total-$active-$steps",
        exerciseType = "RUNNING",
        startTime = Instant.parse("2026-09-23T07:00:00Z"),
        endTime = Instant.parse("2026-09-23T07:00:00Z").plusSeconds(minutes * 60L),
        durationMinutes = minutes,
        activeCalories = active,
        totalCalories = total,
        steps = steps
    )

    // 80 kg, 180 cm: 0.5 × 80 × (180 × 0.415 / 100 000) = 0.02988 kcal walking cost per step,
    // plus 1800 / 1440 / 150 = 0.008333 kcal resting burn per step at 150 steps a minute.
    private val kcalPerStep = 0.02988 + 1800.0 / 1440 / 150

    @Test
    fun workoutCountsInFull_andOnlyStepsOutsideItAreAdded() {
        val day = ActiveActivityCalculator.calculate(
            totalSteps = 9_000,
            workouts = listOf(workout(minutes = 45, total = 400.0, steps = 3_000)),
            weightKg = 80.0, heightCm = 180.0, bmr = 1800.0
        )
        assertEquals(400.0, day.workoutCalories, 1e-9)
        // 6,000 steps outside the workout: 40 active minutes and their full cost.
        assertEquals(40, day.stepMinutes)
        assertEquals(6_000 * kcalPerStep, day.stepCalories!!, 0.01)
        assertEquals(85, day.activeMinutes)
        assertEquals(400.0 + 6_000 * kcalPerStep, day.activeCalories!!, 0.01)
    }

    @Test
    fun workoutWithOnlyActiveCalories_addsTheRestingBurnDuringIt() {
        // 300 active kcal over 30 minutes, with a 1440 kcal/day BMR = 1 kcal a minute resting.
        val kcal = ActiveActivityCalculator.workoutCalories(workout(minutes = 30, active = 300.0), bmr = 1440.0)
        assertEquals(330.0, kcal!!, 1e-9)
        // The recorded total always wins when there is one.
        assertEquals(500.0, ActiveActivityCalculator.workoutCalories(workout(minutes = 30, total = 500.0, active = 300.0), bmr = 1440.0)!!, 1e-9)
    }

    @Test
    fun workoutWithoutCalories_stillCountsItsTime_andItsStepsStillCountAsCalories() {
        val day = ActiveActivityCalculator.calculate(
            totalSteps = 4_500,
            workouts = listOf(workout(minutes = 20, steps = 3_000)),
            weightKg = 80.0, heightCm = 180.0, bmr = 1800.0
        )
        // Time: 20 workout minutes + 1,500 other steps / 150 = 30.
        assertEquals(30, day.activeMinutes)
        // Calories: the workout has no figure, so all 4,500 steps are costed.
        assertEquals(4_500 * kcalPerStep, day.activeCalories!!, 0.01)
    }

    @Test
    fun withoutWeightOrHeight_fallsBackToWhatHealthConnectRecorded() {
        val day = ActiveActivityCalculator.calculate(
            totalSteps = 8_000,
            workouts = listOf(workout(minutes = 30, total = 250.0)),
            weightKg = null, heightCm = 180.0, bmr = null,
            recordedActiveCalories = 320.0
        )
        assertNull(day.stepCalories)
        assertEquals(320.0, day.activeCalories!!, 1e-9)
        // Active time doesn't need weight: 30 workout minutes + 8,000 / 150 (53) step minutes.
        assertEquals(83, day.activeMinutes)
    }

    @Test
    fun noData_isNull() {
        val day = ActiveActivityCalculator.calculate(null, emptyList(), 80.0, 180.0, 1800.0)
        assertNull(day.activeCalories)
        assertNull(day.activeMinutes)
    }

    @Test
    fun totalBurn_doesNotCountRestingCaloriesTwice() {
        // 1440 kcal/day BMR = 1 kcal a minute. 100 active minutes already include their resting
        // burn in the 600 active kcal, so BMR only covers the other 1,340 minutes.
        assertEquals(1_940.0, ActiveActivityCalculator.totalBurn(1440.0, 600.0, 100)!!, 1e-9)
        assertEquals(600.0, ActiveActivityCalculator.totalBurn(null, 600.0, 100)!!, 1e-9)

        val burn = HangryCalorieCalculator().calculateDailyBurn(
            bmr = 1440.0, totalActiveCalories = 600.0, exerciseCalories = 400.0, activeMinutes = 100
        )
        assertEquals(1_940.0, burn.totalBurnedCalories!!, 1e-9)
    }
}
