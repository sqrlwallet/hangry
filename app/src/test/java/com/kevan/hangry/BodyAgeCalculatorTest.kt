package com.kevan.hangry

import com.kevan.hangry.domain.calculation.BodyAgeCalculator
import com.kevan.hangry.domain.calculation.BodyAgeInputs
import com.kevan.hangry.domain.model.BiologicalSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyAgeCalculatorTest {

    private fun inputs(
        age: Int = 40,
        sex: BiologicalSex? = BiologicalSex.MALE,
        days: Int = 30,
        vo2: Double? = null,
        rhr: Double? = null,
        hrv: Double? = null,
        steps: Double? = null,
        exercise: Double? = null,
        strength: Double? = null,
        sleep: Double? = null,
        consistency: Double? = null,
        bodyFat: Double? = null,
        bmi: Double? = null
    ) = BodyAgeInputs(age.toDouble(), sex, days, vo2, rhr, hrv, steps, exercise, strength, sleep, consistency, bodyFat, bmi)

    @Test
    fun tooFewDaysOrFactors_givesNoResult() {
        assertNull(BodyAgeCalculator.calculate(inputs(days = 5, rhr = 55.0, steps = 9000.0, sleep = 450.0, bmi = 23.0)))
        // Only three factors with data.
        assertNull(BodyAgeCalculator.calculate(inputs(rhr = 55.0, steps = 9000.0, sleep = 450.0)))
    }

    @Test
    fun fitHabits_makeBodyAgeYounger() {
        // 40-year-old man: VO2 48 vs typical 39 -> -5 (capped); RHR 52 -> -1.6; 11k steps -> -1.5; 7.5 h sleep -> -1.
        val r = BodyAgeCalculator.calculate(inputs(vo2 = 48.0, rhr = 52.0, steps = 11_000.0, sleep = 450.0))!!
        assertEquals(40 - 5.0 - 1.6 - 1.5 - 1.0, r.bodyAge, 0.05)
        assertTrue(r.difference < 0)
        // Biggest effect first, and unmeasured factors are listed rather than assumed.
        assertEquals("Cardio fitness", r.factors.first().name)
        assertTrue("Strength training" in r.missing)
    }

    @Test
    fun poorHabits_makeBodyAgeOlder() {
        // 35: RHR 80 -> +3 (capped); 2.5k steps -> +2; 5 h sleep -> +2; BMI 32 -> +2.5.
        val r = BodyAgeCalculator.calculate(inputs(age = 35, rhr = 80.0, steps = 2_500.0, sleep = 300.0, bmi = 32.0))!!
        assertEquals(35 + 3.0 + 2.0 + 2.0 + 2.5, r.bodyAge, 0.05)
    }

    @Test
    fun totalIsCappedAtTwelveYears() {
        val r = BodyAgeCalculator.calculate(
            inputs(vo2 = 20.0, rhr = 90.0, hrv = 10.0, steps = 1_000.0, exercise = 0.0, strength = 0.0, sleep = 240.0, consistency = 40.0, bodyFat = 40.0)
        )!!
        assertEquals(52.0, r.bodyAge, 0.001)
    }

    @Test
    fun bodyFatScanIsPreferredOverBmi() {
        val r = BodyAgeCalculator.calculate(inputs(rhr = 60.0, steps = 6_000.0, sleep = 420.0, bodyFat = 18.0, bmi = 31.0))!!
        val body = r.factors.single { it.name == "Body composition" }
        assertEquals(-1.0, body.years, 0.001)
        assertTrue(body.value.contains("body fat"))
    }

    @Test
    fun vo2NormFallsWithAgeAndDiffersBySex() {
        assertEquals(47.0, BodyAgeCalculator.vo2Norm(20.0, BiologicalSex.MALE), 0.001)
        assertEquals(36.0, BodyAgeCalculator.vo2Norm(30.0, BiologicalSex.FEMALE), 0.001)
        assertEquals(20.0, BodyAgeCalculator.vo2Norm(95.0, BiologicalSex.FEMALE), 0.001)
    }

    @Test
    fun exactAgeIsUsedAsIs() {
        val r = BodyAgeCalculator.calculate(
            BodyAgeInputs(34.7, BiologicalSex.FEMALE, 30, null, 60.0, null, 6_000.0, null, null, 420.0, 75.0, null, null)
        )!!
        // RHR 60 ±0, 6k steps ±0, 7 h sleep -1, consistency 75 ±0.
        assertEquals(34.7, r.chronologicalAge, 0.001)
        assertEquals(33.7, r.bodyAge, 0.001)
    }
}
