package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.WorkoutCalorieEstimator
import com.kevan.hangry.domain.calculation.WorkoutCalorieEstimator.Person
import com.kevan.hangry.domain.model.BiologicalSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WorkoutCalorieEstimatorTest {

    private val man = Person(weightKg = 80.0, age = 35, sex = BiologicalSex.MALE)

    private fun workout(type: String, minutes: Int, hr: Double? = null, total: Double? = null) = ExerciseSessionEntity(
        recordFingerprint = "w-$type-$minutes-$hr-$total",
        exerciseType = type,
        startTime = Instant.parse("2026-10-19T07:00:00Z"),
        endTime = Instant.parse("2026-10-19T07:00:00Z").plusSeconds(minutes * 60L),
        durationMinutes = minutes,
        totalCalories = total,
        avgHeartRate = hr
    )

    @Test
    fun `heart rate uses Keytel's equation`() {
        // Male, 150 bpm, 80 kg, 35 y: -55.0969 + 0.6309×150 + 0.1988×80 + 0.2017×35 = 62.50 kJ/min.
        val kcal = WorkoutCalorieEstimator.estimate(workout("RUNNING", 30, hr = 150.0), bmr = 1_780.0, person = man)!!
        assertEquals(62.5016 / 4.184 * 30, kcal, 0.01)
    }

    @Test
    fun `a resting heart rate falls back to type and duration`() {
        val kcal = WorkoutCalorieEstimator.estimate(workout("YOGA", 60, hr = 75.0), bmr = 1_440.0, person = man)!!
        // Yoga 2.5 METs × 1 kcal a minute resting × 60 minutes.
        assertEquals(150.0, kcal, 0.01)
    }

    @Test
    fun `without BMR a MET is a kcal per kg per hour`() {
        val kcal = WorkoutCalorieEstimator.estimate(workout("BIKING", 60), bmr = null, person = man)!!
        assertEquals(7.0 * 80.0, kcal, 0.01)
    }

    @Test
    fun `recorded calories always win, and nothing is estimated without a person`() {
        val recorded = workout("RUNNING", 30, hr = 150.0, total = 321.0)
        assertEquals(321.0, ActiveActivityCalculator.workoutCalories(recorded, 1_780.0, man)!!, 1e-9)
        assertNull(ActiveActivityCalculator.workoutCalories(workout("RUNNING", 30), 1_780.0))
    }
}
