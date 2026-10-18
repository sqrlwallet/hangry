package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HangryStressCalculator
import com.kevan.hangry.domain.model.StressLevel
import org.junit.Assert.*
import org.junit.Test

class HangryStressCalculatorTest {

    private val calculator = HangryStressCalculator()

    /** A month of HRV around 60 ms and resting heart rate around 56 bpm, with normal day-to-day wobble. */
    private val hrvMonth = (0 until 30).map { 60.0 * if (it % 2 == 0) 1.08 else 0.92 }
    private val rhrMonth = (0 until 30).map { if (it % 2 == 0) 54.0 else 58.0 }

    @Test
    fun testNull_whenFewerThanThreeDays() {
        val result = calculator.calculateStress(
            hrvToday = 60.0,
            hrvHistory = listOf(60.0, 61.0),
            rhrToday = 55.0,
            rhrHistory = listOf(55.0, 56.0)
        )

        assertNull(result)
    }

    @Test
    fun testLowStress_whenHrvElevatedAndRhrLow() {
        val result = calculator.calculateStress(
            hrvToday = 80.0, // well above a ~60 ms normal
            hrvHistory = hrvMonth,
            rhrToday = 50.0, // well below a 56 bpm normal
            rhrHistory = rhrMonth
        )

        assertNotNull(result)
        assertEquals(StressLevel.LOW, result!!.level)
        assertTrue(result.score < 35)
        assertEquals("HIGH", result.confidence)
    }

    @Test
    fun testElevatedStress_whenHrvSuppressedAndRhrElevated() {
        val result = calculator.calculateStress(
            hrvToday = 40.0, // ~0.67 of normal
            hrvHistory = hrvMonth,
            rhrToday = 66.0, // +10 bpm
            rhrHistory = rhrMonth
        )

        assertNotNull(result)
        assertTrue(result!!.level == StressLevel.ELEVATED || result.level == StressLevel.HIGH)
        assertTrue(result.score >= 65)
    }

    @Test
    fun testNull_whenNoWearableData() {
        val result = calculator.calculateStress(
            hrvToday = null,
            hrvHistory = emptyList(),
            rhrToday = null,
            rhrHistory = emptyList()
        )

        assertNull(result)
    }

    @Test
    fun `a day right at your normal is moderate`() {
        val result = calculator.calculateStress(hrvToday = 60.0, hrvHistory = hrvMonth, rhrToday = 56.0, rhrHistory = rhrMonth)!!
        assertEquals(StressLevel.MODERATE, result.level)
        assertEquals(42, result.score)
    }

    @Test
    fun `a dip that is ordinary for a variable person is not flagged`() {
        // Same 10% HRV dip and 2 bpm rise: unremarkable for someone whose readings swing a lot,
        // notable for someone who is usually steady.
        val variableHrv = (0 until 30).map { 60.0 * if (it % 2 == 0) 1.2 else 0.8 }
        val variableRhr = (0 until 30).map { if (it % 2 == 0) 51.0 else 61.0 }
        val steadyHrv = (0 until 30).map { 60.0 * if (it % 2 == 0) 1.02 else 0.98 }
        val steadyRhr = (0 until 30).map { if (it % 2 == 0) 55.5 else 56.5 }
        val variable = calculator.calculateStress(54.0, variableHrv, 58.0, variableRhr)!!
        val steady = calculator.calculateStress(54.0, steadyHrv, 58.0, steadyRhr)!!
        assertEquals(StressLevel.MODERATE, variable.level)
        assertTrue("steady ${steady.score} vs variable ${variable.score}", steady.score > variable.score + 15)
    }

    @Test
    fun `HRV balance is shown against your usual on a log scale`() {
        val result = calculator.calculateStress(hrvToday = 60.0, hrvHistory = listOf(30.0, 60.0, 120.0), rhrToday = null, rhrHistory = emptyList())!!
        // The geometric mean of 30, 60 and 120 is 60, so today is right at normal.
        assertEquals(1.0, result.hrvDeviationRatio!!, 0.001)
        assertNull(result.rhrDeviationBpm)
        assertEquals("MEDIUM", result.confidence)
    }
}
