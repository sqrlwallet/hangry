package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.StrainSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class HangryStrainCalculatorTest {

    private lateinit var calculator: HangryStrainCalculator

    @Before
    fun setup() {
        calculator = HangryStrainCalculator(HangryTrainingLoadCalculator())
    }

    private fun sampleAt(minutesFromNow: Long, bpm: Double, now: Instant): HeartRateSampleEntity =
        HeartRateSampleEntity(
            recordFingerprint = "sample-$minutesFromNow-$bpm",
            timestamp = now.plus(minutesFromNow, ChronoUnit.MINUTES),
            bpm = bpm
        )

    @Test
    fun testNoDataAtAll_returnsZeroStrainWithNoneSource() {
        val result = calculator.calculateDayStrain(emptyList(), emptyList())
        assertEquals(0.0, result.dayStrain, 0.01)
        assertEquals(StrainSource.NONE, result.source)
    }

    @Test
    fun testWorkoutOnlyFallback_usedWhenNoContinuousHeartRateTrace() {
        val now = Instant.now()
        val run = ExerciseSessionEntity(
            recordFingerprint = "run-1",
            exerciseType = "RUNNING",
            startTime = now,
            endTime = now.plusSeconds(1800),
            durationMinutes = 30
        )

        val result = calculator.calculateDayStrain(emptyList(), listOf(run))
        assertEquals(StrainSource.WORKOUT_ESTIMATE, result.source)
        assertTrue("Strain must be positive for a logged workout", result.dayStrain > 0.0)
        assertTrue("Strain must stay within the bounded 0-21 scale", result.dayStrain <= HangryStrainCalculator.MAX_STRAIN)
    }

    @Test
    fun testZoneBasedStrain_higherIntensityYieldsHigherStrain() {
        val now = Instant.now()
        // 60 samples one minute apart: low zone (zone 1, <114bpm)
        val lightSamples = (0 until 60).map { sampleAt(it.toLong(), 100.0, now) }
        // 60 samples one minute apart: high zone (zone 4-5, >=152bpm)
        val hardSamples = (0 until 60).map { sampleAt(it.toLong(), 165.0, now) }

        val lightResult = calculator.calculateDayStrain(lightSamples, emptyList())
        val hardResult = calculator.calculateDayStrain(hardSamples, emptyList())

        assertEquals(StrainSource.HEART_RATE_ZONES, lightResult.source)
        assertEquals(StrainSource.HEART_RATE_ZONES, hardResult.source)
        assertTrue("Harder effort must yield more strain than light effort", hardResult.dayStrain > lightResult.dayStrain)
        assertTrue(hardResult.dayStrain <= HangryStrainCalculator.MAX_STRAIN)
    }

    @Test
    fun testStrainNeverExceedsMaxScale() {
        val now = Instant.now()
        // A very long, very intense trace should still saturate at MAX_STRAIN, never exceed it.
        val extremeSamples = (0 until 600).map { sampleAt(it.toLong(), 190.0, now) }
        val result = calculator.calculateDayStrain(extremeSamples, emptyList())
        assertTrue(result.dayStrain <= HangryStrainCalculator.MAX_STRAIN)
        assertTrue(result.dayStrain >= 0.0)
    }

    @Test
    fun testRecommendStrainTarget_rebuildIsLowerThanPrimed() {
        val history = listOf(10.0, 12.0, 9.0, 11.0)
        val rebuild = calculator.recommendStrainTarget(RecoveryState.REBUILD, history)
        val primed = calculator.recommendStrainTarget(RecoveryState.PRIMED, history)

        assertTrue("Rebuild's target band must sit below Primed's", rebuild.targetHigh < primed.targetLow)
        assertTrue(rebuild.targetLow >= 0.0)
        assertTrue(primed.targetHigh <= HangryStrainCalculator.MAX_STRAIN)
    }

    @Test
    fun testRecommendStrainTarget_noHistoryStillReturnsSaneBand() {
        val recommendation = calculator.recommendStrainTarget(RecoveryState.BALANCED, emptyList())
        assertTrue(recommendation.targetLow >= 0.0)
        assertTrue(recommendation.targetHigh <= HangryStrainCalculator.MAX_STRAIN)
        assertTrue(recommendation.targetLow <= recommendation.targetHigh)
    }
}
