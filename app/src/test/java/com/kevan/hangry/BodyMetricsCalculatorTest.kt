package com.kevan.hangry

import com.kevan.hangry.domain.calculation.BodyMetricsCalculator
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.MetricTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMetricsCalculatorTest {

    private val calculator = BodyMetricsCalculator(HangryCalorieCalculator())

    private val fullMale = BodyMetricsInput(
        heightCm = 180.0, weightKg = 80.0, age = 30, sex = BiologicalSex.MALE,
        neckCm = 38.0, chestCm = 104.0, waistCm = 84.0, hipCm = 98.0, bodyFatPercent = 16.0,
        bodyFatSource = "tape-measure", averageDailyBurnKcal = 2600.0, averageDailyBurnDays = 7
    )

    private fun metric(input: BodyMetricsInput, id: String): BodyMetric =
        calculator.calculate(input).flatMap { it.metrics }.first { it.id == id }

    @Test
    fun `every metric is calculated when all inputs are present`() {
        val metrics = calculator.calculate(fullMale).flatMap { it.metrics }
        // Maintenance/goal calories need activity data - covered by EnergyBalanceCalculatorTest.
        val missing = metrics.filterNot { it.isAvailable }.map { it.id } - setOf("maintenance", "goal_calories")
        assertTrue("Unexpectedly missing: $missing", missing.isEmpty())
    }

    @Test
    fun `core formulas match hand calculations`() {
        assertEquals(24.69, metric(fullMale, "bmi").value!!, 0.01)
        assertEquals(0.467, metric(fullMale, "whtr").value!!, 0.001)
        assertEquals(0.857, metric(fullMale, "whr").value!!, 0.001)
        // Lean 67.2 kg / 3.24 = 20.74; normalized adds 0 at exactly 1.8 m.
        assertEquals(20.74, metric(fullMale, "ffmi").value!!, 0.01)
        // Mifflin: 800 + 1125 - 150 + 5
        assertEquals(1780.0, metric(fullMale, "bmr").value!!, 0.5)
        // Katch: 370 + 21.6 x 67.2
        assertEquals(1821.5, metric(fullMale, "bmr_lean").value!!, 0.5)
        assertEquals(187.0, metric(fullMale, "max_hr").value!!, 0.01)
    }

    @Test
    fun `bands classify by sex`() {
        assertEquals("Healthy", metric(fullMale, "whr").status)
        // Same waist and hips on a woman crosses the 0.85 WHO threshold.
        val female = fullMale.copy(sex = BiologicalSex.FEMALE)
        assertEquals(MetricTone.RISK, metric(female, "whr").tone)
    }

    @Test
    fun `missing tape measurements are reported, not faked`() {
        val noTape = fullMale.copy(waistCm = null, hipCm = null)
        val whr = metric(noTape, "whr")
        assertFalse(whr.isAvailable)
        assertEquals(listOf("Waist", "Hips"), whr.missingInputs)
    }

    @Test
    fun `body fat falls back to an estimate without a scan`() {
        val noScan = fullMale.copy(bodyFatPercent = null, bodyFatSource = null)
        val bf = metric(noScan, "body_fat")
        assertTrue(bf.isAvailable)
        assertTrue(bf.name.contains("estimated"))
    }

    @Test
    fun `healthy weight range reports distance outside the range`() {
        val heavy = fullMale.copy(weightKg = 90.0)
        assertEquals("9.3 kg above range", metric(heavy, "weight_range").status)
    }
}
