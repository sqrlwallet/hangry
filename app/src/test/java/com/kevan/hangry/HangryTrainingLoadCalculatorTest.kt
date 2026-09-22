package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class HangryTrainingLoadCalculatorTest {

    private lateinit var calculator: HangryTrainingLoadCalculator

    @Before
    fun setup() {
        calculator = HangryTrainingLoadCalculator()
    }

    @Test
    fun testRestDay_returnsZeroLoadAndRestMessage() {
        val analysis = calculator.calculateDailyLoad(
            workoutsToday = emptyList(),
            recentWorkoutsHistory = emptyList()
        )

        assertEquals(0.0, analysis.dailyLoad, 0.01)
        assertEquals(0, analysis.workoutCount)
        assertTrue(analysis.supportiveNote.contains("Rest day", ignoreCase = true))
    }

    @Test
    fun testIntensityMultiplier_runningYieldsHigherLoadThanWalking() {
        val now = Instant.now()
        val run = ExerciseSessionEntity(
            recordFingerprint = "run-1",
            exerciseType = "RUNNING",
            startTime = now,
            endTime = now.plusSeconds(1800),
            durationMinutes = 30
        )
        val walk = ExerciseSessionEntity(
            recordFingerprint = "walk-1",
            exerciseType = "WALKING",
            startTime = now,
            endTime = now.plusSeconds(1800),
            durationMinutes = 30
        )

        val runLoad = calculator.estimateSessionLoad(run)
        val walkLoad = calculator.estimateSessionLoad(walk)

        assertTrue("Running load must exceed walking load for identical duration", runLoad > walkLoad)
    }

    @Test
    fun testMultipleWorkoutsSameDay_aggregatesDailyLoad() {
        val now = Instant.now()
        val run = ExerciseSessionEntity(
            recordFingerprint = "run-2",
            exerciseType = "RUNNING",
            startTime = now,
            endTime = now.plusSeconds(1800),
            durationMinutes = 30
        )
        val strength = ExerciseSessionEntity(
            recordFingerprint = "strength-1",
            exerciseType = "STRENGTH_TRAINING",
            startTime = now.plusSeconds(3600),
            endTime = now.plusSeconds(6000),
            durationMinutes = 40
        )

        val analysis = calculator.calculateDailyLoad(
            workoutsToday = listOf(run, strength),
            recentWorkoutsHistory = listOf(run, strength)
        )

        assertEquals(2, analysis.workoutCount)
        assertTrue(analysis.dailyLoad > calculator.estimateSessionLoad(run))
    }
}
