package com.kevan.hangry

import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.HangryRecoveryCalculator
import com.kevan.hangry.domain.calculation.RecoveryConfig
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class HangryRecoveryCalculatorTest {

    private lateinit var calculator: HangryRecoveryCalculator
    private val today = LocalDate.of(2026, 9, 22)

    @Before
    fun setup() {
        calculator = HangryRecoveryCalculator()
    }

    @Test
    fun testZeroDaysOfData_showsBuildingBaseline() {
        val current = DayMetrics(today, 480, 60.0, 65.0)
        val history = emptyList<DayMetrics>()

        val result = calculator.calculateRecovery(today, current, history)

        assertNull("Score must be null when no baseline exists", result.score)
        assertEquals(RecoveryState.BUILDING_BASELINE, result.state)
        assertEquals(ScoreConfidence.LOW, result.confidence)
        assertTrue(result.supportiveAdvice.contains("baseline is still building", ignoreCase = true))
    }

    @Test
    fun testLessThanThreeDays_showsBuildingBaseline() {
        val current = DayMetrics(today, 480, 58.0, 65.0)
        val history = listOf(
            DayMetrics(today.minusDays(1), 470, 59.0, 63.0),
            DayMetrics(today.minusDays(2), 490, 57.0, 66.0)
        )

        val result = calculator.calculateRecovery(today, current, history)

        assertNull("Fewer than 3 usable days must not show a premature score", result.score)
        assertEquals(RecoveryState.BUILDING_BASELINE, result.state)
    }

    @Test
    fun testSevenDaysOfBaseline_returnsValidScoreAndHighConfidence() {
        val current = DayMetrics(today, 490, 56.0, 70.0, trainingLoad = 25.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = 65.0,
                trainingLoad = 30.0
            )
        }

        val result = calculator.calculateRecovery(today, current, history)

        assertNotNull(result.score)
        assertTrue("Score must be between 0 and 100", result.score!! in 0..100)
        assertEquals(ScoreConfidence.HIGH, result.confidence)
        assertTrue(result.positiveContributors.isNotEmpty())
        assertEquals(3, result.algorithmVersion)
    }

    @Test
    fun testMissingHrv_calculatesScoreWithoutZeroSubstitution() {
        // Current day has no HRV recorded
        val current = DayMetrics(today, 480, 58.0, null, trainingLoad = 20.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = 65.0
            )
        }

        val result = calculator.calculateRecovery(today, current, history)

        assertNotNull("Score should still be computed from remaining metrics", result.score)
        assertTrue(result.score!! in 0..100)
        assertNull("Missing HRV must not be reported as a measured component", result.hrvScore)
        assertNotEquals("Missing HRV must reduce confidence", ScoreConfidence.HIGH, result.confidence)
    }

    @Test
    fun testMissingHrv_isTreatedAsGood() {
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = null
            )
        }
        val atBaselineHrv = history.map { it.copy(hrvRmssd = 65.0) }

        val withoutHrv = calculator.calculateRecovery(
            today, DayMetrics(today, 480, 58.0, null), history
        )
        val baselineHrv = calculator.calculateRecovery(
            today, DayMetrics(today, 480, 58.0, 65.0), atBaselineHrv
        )

        assertNotNull(withoutHrv.score)
        assertNull(withoutHrv.hrvScore)
        assertTrue(
            "Missing HRV should score better than an at-baseline HRV reading",
            withoutHrv.score!! > baselineHrv.score!!
        )
    }

    @Test
    fun testMissingSleep_calculatesScoreGracefully() {
        val current = DayMetrics(today, null, 57.0, 68.0, trainingLoad = 15.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = 65.0
            )
        }

        val result = calculator.calculateRecovery(today, current, history)

        assertNotNull(result.score)
        assertNull(result.sleepScore)
        assertTrue(result.score!! in 0..100)
    }

    @Test
    fun testMissingRestingHeartRate_calculatesScoreGracefully() {
        val current = DayMetrics(today, 480, null, 66.0, trainingLoad = 20.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = 65.0
            )
        }

        val result = calculator.calculateRecovery(today, current, history)

        assertNotNull(result.score)
        assertNull(result.rhrScore)
        assertTrue(result.score!! in 0..100)
    }

    @Test
    fun testOutliers_clampsScoreStrictlyBetweenZeroAndOneHundred() {
        // Massive HRV drop + massive RHR spike
        val terribleMetrics = DayMetrics(today, 60, 120.0, 5.0, trainingLoad = 150.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 500,
                restingHeartRate = 50.0,
                hrvRmssd = 80.0
            )
        }

        val result = calculator.calculateRecovery(today, terribleMetrics, history)
        assertNotNull(result.score)
        assertTrue("Score must not go below 0", result.score!! >= 0)
        assertEquals(RecoveryState.REBUILD, result.state)

        // Super human metrics
        val superMetrics = DayMetrics(today, 600, 35.0, 180.0, trainingLoad = 0.0)
        val resultSuper = calculator.calculateRecovery(today, superMetrics, history)
        assertNotNull(resultSuper.score)
        assertTrue("Score must not exceed 100", resultSuper.score!! <= 100)
        assertEquals(RecoveryState.PRIMED, resultSuper.state)
    }

    @Test
    fun testDeterministic_sameInputsYieldIdenticalOutput() {
        val current = DayMetrics(today, 460, 57.5, 62.0, trainingLoad = 30.0)
        val history = (1..7).map { offset ->
            DayMetrics(
                date = today.minusDays(offset.toLong()),
                sleepDurationMinutes = 480,
                restingHeartRate = 58.0,
                hrvRmssd = 60.0
            )
        }

        val run1 = calculator.calculateRecovery(today, current, history)
        val run2 = calculator.calculateRecovery(today, current, history)

        assertEquals("Algorithm must be 100% deterministic", run1.score, run2.score)
        assertEquals(run1.confidence, run2.confidence)
        assertEquals(run1.state, run2.state)
        assertEquals(run1.positiveContributors, run2.positiveContributors)
        assertEquals(run1.negativeContributors, run2.negativeContributors)
    }

    @Test
    fun testSupportiveLanguage_neverUsesClinicalShameWords() {
        val poorMetrics = DayMetrics(today, 240, 75.0, 25.0, trainingLoad = 60.0)
        val history = (1..7).map { offset ->
            DayMetrics(today.minusDays(offset.toLong()), 480, 58.0, 65.0)
        }

        val result = calculator.calculateRecovery(today, poorMetrics, history)
        val text = "${result.supportiveAdvice} ${result.negativeContributors.joinToString()}"

        val prohibitedWords = listOf("unhealthy", "poor", "failing", "danger", "critical", "abnormal")
        prohibitedWords.forEach { word ->
            assertFalse("Copy must not contain shameful/clinical word '$word'", text.contains(word, ignoreCase = true))
        }
    }

    @Test
    fun testMissingHrv_usesFeelingDefaultingToExcellent() {
        val history = (1..7).map { offset ->
            DayMetrics(today.minusDays(offset.toLong()), 480, 58.0, 65.0)
        }
        val noHrv = DayMetrics(today, 480, 58.0, null)

        val excellent = calculator.calculateRecovery(today, noHrv, history)
        val drained = calculator.calculateRecovery(
            today, noHrv, history, com.kevan.hangry.domain.calculation.RecoveryConfig(
                assumedHrvScore = com.kevan.hangry.domain.model.HrvFeeling.DRAINED.score
            )
        )
        assertEquals(
            com.kevan.hangry.domain.model.HrvFeeling.EXCELLENT.score,
            com.kevan.hangry.domain.calculation.RecoveryConfig().assumedHrvScore,
            0.0
        )
        assertTrue("A lower feeling must lower recovery", drained.score!! < excellent.score!!)
    }

    @Test
    fun testRealHrv_ignoresFeeling() {
        val history = (1..7).map { offset ->
            DayMetrics(today.minusDays(offset.toLong()), 480, 58.0, 65.0)
        }
        val withHrv = DayMetrics(today, 480, 58.0, 60.0)
        val default = calculator.calculateRecovery(today, withHrv, history)
        val drained = calculator.calculateRecovery(
            today, withHrv, history, com.kevan.hangry.domain.calculation.RecoveryConfig(
                assumedHrvScore = com.kevan.hangry.domain.model.HrvFeeling.DRAINED.score
            )
        )
        assertEquals(default.score, drained.score)
    }
}
