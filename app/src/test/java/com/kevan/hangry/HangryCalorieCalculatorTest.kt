package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class HangryCalorieCalculatorTest {

    private lateinit var calculator: HangryCalorieCalculator

    @Before
    fun setup() {
        calculator = HangryCalorieCalculator()
    }

    @Test
    fun testBmr_menHigherThanWomenForIdenticalStats() {
        val menBmr = calculator.calculateBmr(weightKg = 75.0, heightCm = 178.0, age = 30, biologicalSex = BiologicalSex.MALE)
        val womenBmr = calculator.calculateBmr(weightKg = 75.0, heightCm = 178.0, age = 30, biologicalSex = BiologicalSex.FEMALE)
        assertTrue("Mifflin-St Jeor male coefficient must yield a higher BMR than female for identical stats", menBmr > womenBmr)
    }

    @Test
    fun testBmr_otherIsMidpointOfMaleAndFemale() {
        val menBmr = calculator.calculateBmr(75.0, 178.0, 30, BiologicalSex.MALE)
        val womenBmr = calculator.calculateBmr(75.0, 178.0, 30, BiologicalSex.FEMALE)
        val otherBmr = calculator.calculateBmr(75.0, 178.0, 30, BiologicalSex.OTHER)
        assertEquals((menBmr + womenBmr) / 2.0, otherBmr, 0.01)
    }

    @Test
    fun testDailyBurn_neverSubstitutesMissingDataWithZero() {
        val result = calculator.calculateDailyBurn(bmr = null, totalActiveCalories = null, exerciseCalories = 0.0)
        assertNull("Missing BMR and active calories must not be substituted with zero", result.totalBurnedCalories)
        assertNull(result.neatCalories)
    }

    @Test
    fun testDailyBurn_totalCombinesBmrAndActiveCalories() {
        val result = calculator.calculateDailyBurn(bmr = 1600.0, totalActiveCalories = 400.0, exerciseCalories = 150.0)
        assertEquals(2000.0, result.totalBurnedCalories!!, 0.01)
        assertEquals(250.0, result.neatCalories!!, 0.01) // 400 total active - 150 exercise
    }

    @Test
    fun testGoalRecommendation_missingInputsReturnsNull() {
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2200.0, currentWeightKg = 80.0, weightGoalKg = null, targetDate = null, today = LocalDate.now()
        )
        assertNull(result)
    }

    @Test
    fun testGoalRecommendation_weightLossYieldsDeficitBelowTdee() {
        val today = LocalDate.of(2026, 1, 1)
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2400.0,
            currentWeightKg = 90.0,
            weightGoalKg = 85.0,
            targetDate = today.plusDays(70),
            today = today
        )
        assertNotNull(result)
        assertNotNull(result!!.dailyCalorieTarget)
        assertTrue("A weight-loss goal must recommend eating below TDEE", result.dailyCalorieTarget!! < 2400)
        assertTrue("Weekly pace should reflect a loss (negative)", result.weeklyPaceKg < 0)
    }

    @Test
    fun testGoalRecommendation_unsafelyFastPaceIsCappedAndFlagged() {
        val today = LocalDate.of(2026, 1, 1)
        // Losing 20kg in 10 days is a dangerously fast request - must be capped.
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2400.0,
            currentWeightKg = 90.0,
            weightGoalKg = 70.0,
            targetDate = today.plusDays(10),
            today = today
        )
        assertNotNull(result)
        assertTrue("An unsafe request must be flagged as safety-adjusted", result!!.isPaceAdjustedForSafety)
        assertTrue(
            "Target must never fall below the minimum safe calorie floor",
            result.dailyCalorieTarget!! >= HangryCalorieCalculator.MIN_SAFE_CALORIES.toInt()
        )
    }

    @Test
    fun testGoalRecommendation_pastTargetDateReturnsGuidanceWithoutTarget() {
        val today = LocalDate.of(2026, 1, 10)
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2200.0, currentWeightKg = 80.0, weightGoalKg = 75.0, targetDate = today.minusDays(1), today = today
        )
        assertNotNull(result)
        assertNull(result!!.dailyCalorieTarget)
        assertTrue(result.guidance.isNotBlank())
    }

    @Test
    fun testGoalRecommendation_lossNeverGoesBelowMaleFloorOrBmr() {
        val today = LocalDate.of(2026, 1, 1)
        val male = calculator.recommendDailyCalorieGoal(
            tdee = 2000.0, currentWeightKg = 90.0, weightGoalKg = 70.0, targetDate = today.plusDays(30), today = today,
            bmr = 1400.0, sex = BiologicalSex.MALE
        )!!
        assertEquals(1500, male.dailyCalorieTarget)
        val highBmr = calculator.recommendDailyCalorieGoal(
            tdee = 2100.0, currentWeightKg = 60.0, weightGoalKg = 50.0, targetDate = today.plusDays(30), today = today,
            bmr = 1650.0, sex = BiologicalSex.FEMALE
        )!!
        assertEquals(1650, highBmr.dailyCalorieTarget)
        assertTrue(highBmr.isPaceAdjustedForSafety)
    }

    @Test
    fun testGoalRecommendation_floorNeverPushesAboveMaintenance() {
        val today = LocalDate.of(2026, 1, 1)
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 1400.0, currentWeightKg = 55.0, weightGoalKg = 50.0, targetDate = today.plusDays(60), today = today,
            bmr = 1250.0, sex = BiologicalSex.MALE
        )!!
        assertEquals(1400, result.dailyCalorieTarget)
    }

    @Test
    fun testGoalRecommendation_gainIsCappedAtHalfPercentPerWeek() {
        val today = LocalDate.of(2026, 1, 1)
        // 70 kg × 0.5% = 0.35 kg/week = 385 kcal/day.
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2500.0, currentWeightKg = 70.0, weightGoalKg = 80.0, targetDate = today.plusDays(30), today = today
        )!!
        assertEquals(2885, result.dailyCalorieTarget)
        assertTrue(result.isPaceAdjustedForSafety)
    }

    @Test
    fun testGoalRecommendation_goalWeightAloneUsesDefaultPace() {
        val today = LocalDate.of(2026, 1, 1)
        val lose = calculator.recommendDailyCalorieGoal(tdee = 2500.0, currentWeightKg = 80.0, weightGoalKg = 75.0, targetDate = null, today = today)!!
        assertEquals(1950, lose.dailyCalorieTarget) // 0.5 kg/week = 550 kcal/day
        assertFalse(lose.isPaceAdjustedForSafety)
        val gain = calculator.recommendDailyCalorieGoal(tdee = 2500.0, currentWeightKg = 70.0, weightGoalKg = 75.0, targetDate = null, today = today)!!
        assertEquals(2775, gain.dailyCalorieTarget) // 0.25 kg/week = 275 kcal/day
    }

    @Test
    fun testGoalRecommendation_atGoalWeightMaintains() {
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2300.0, currentWeightKg = 70.3, weightGoalKg = 70.0, targetDate = null, today = LocalDate.of(2026, 1, 1)
        )!!
        assertEquals(2300, result.dailyCalorieTarget)
        assertEquals(0.0, result.weeklyPaceKg, 0.0)
    }

    @Test
    fun testGoalRecommendation_defaultPaceStillCappedForSmallerBodies() {
        // 45 kg × 1% = 0.45 kg/week = 495 kcal/day, below the 550 default.
        val result = calculator.recommendDailyCalorieGoal(
            tdee = 2000.0, currentWeightKg = 45.0, weightGoalKg = 42.0, targetDate = null, today = LocalDate.of(2026, 1, 1),
            bmr = 1100.0, sex = BiologicalSex.FEMALE
        )!!
        assertEquals(1505, result.dailyCalorieTarget)
        assertTrue(result.guidance.startsWith("Losing about"))
    }
}
