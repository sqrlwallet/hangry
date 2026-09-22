package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HangryStressCalculator
import com.kevan.hangry.domain.model.StressLevel
import org.junit.Assert.*
import org.junit.Test

class HangryStressCalculatorTest {

    private val calculator = HangryStressCalculator()

    @Test
    fun testNull_whenFewerThanThreeDays() {
        val result = calculator.calculateStress(
            hrvToday = 60.0,
            hrvBaseline7d = 60.0,
            rhrToday = 55.0,
            rhrBaseline7d = 55.0,
            baselineDaysCount = 2
        )

        assertNull(result)
    }

    @Test
    fun testLowStress_whenHrvElevatedAndRhrLow() {
        val result = calculator.calculateStress(
            hrvToday = 75.0, // well above baseline
            hrvBaseline7d = 60.0,
            rhrToday = 52.0, // lower than baseline
            rhrBaseline7d = 56.0,
            baselineDaysCount = 7
        )

        assertNotNull(result)
        assertEquals(StressLevel.LOW, result!!.level)
        assertTrue(result.score < 35)
        assertEquals("HIGH", result.confidence)
    }

    @Test
    fun testElevatedStress_whenHrvSuppressedAndRhrElevated() {
        val result = calculator.calculateStress(
            hrvToday = 40.0, // well below 60.0 baseline (~0.67 ratio)
            hrvBaseline7d = 60.0,
            rhrToday = 66.0, // +10 bpm above 56.0 baseline
            rhrBaseline7d = 56.0,
            baselineDaysCount = 7
        )

        assertNotNull(result)
        assertTrue(result!!.level == StressLevel.ELEVATED || result.level == StressLevel.HIGH)
        assertTrue(result.score >= 65)
    }

    @Test
    fun testNull_whenNoWearableData() {
        val result = calculator.calculateStress(
            hrvToday = null,
            hrvBaseline7d = null,
            rhrToday = null,
            rhrBaseline7d = null,
            baselineDaysCount = 0
        )

        assertNull(result)
    }
}
