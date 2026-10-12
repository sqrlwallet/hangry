package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.CalorieBurnResult
import com.kevan.hangry.domain.model.CalorieGoalRecommendation
import com.kevan.hangry.domain.model.ScoreConfidence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class HangryCalorieCalculator : CalorieCalculator {

    override fun calculateBmr(weightKg: Double, heightCm: Double, age: Int, biologicalSex: BiologicalSex): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return when (biologicalSex) {
            BiologicalSex.MALE -> base + 5.0
            BiologicalSex.FEMALE -> base - 161.0
            BiologicalSex.OTHER -> base - 78.0 // midpoint of the male/female offsets
        }
    }

    override fun calculateDailyBurn(
        bmr: Double?,
        totalActiveCalories: Double?,
        exerciseCalories: Double,
        activeMinutes: Int?
    ): CalorieBurnResult {
        val neat = totalActiveCalories?.let { (it - exerciseCalories).coerceAtLeast(0.0) }
        val total = bmr?.let { ActiveActivityCalculator.totalBurn(it, totalActiveCalories, activeMinutes) }

        val confidence = when {
            bmr != null && totalActiveCalories != null -> ScoreConfidence.HIGH
            bmr != null || totalActiveCalories != null -> ScoreConfidence.MEDIUM
            else -> ScoreConfidence.LOW
        }
        val note = when {
            bmr == null -> "Add your age, height, and biological sex in Settings to estimate resting calorie burn."
            totalActiveCalories == null -> "Resting burn only - sync Health Connect activity data for your full daily total."
            else -> "Resting metabolism, daily movement, and logged workouts combined."
        }

        return CalorieBurnResult(
            bmrCalories = bmr,
            neatCalories = neat,
            exerciseCalories = exerciseCalories,
            totalBurnedCalories = total,
            confidence = confidence,
            supportiveNote = note
        )
    }

    override fun recommendDailyCalorieGoal(
        tdee: Double?,
        currentWeightKg: Double?,
        weightGoalKg: Double?,
        targetDate: LocalDate?,
        today: LocalDate,
        bmr: Double?,
        sex: BiologicalSex?
    ): CalorieGoalRecommendation? {
        if (tdee == null || currentWeightKg == null || weightGoalKg == null || targetDate == null) return null

        val daysRemaining = ChronoUnit.DAYS.between(today, targetDate).toInt()
        if (daysRemaining <= 0) {
            return CalorieGoalRecommendation(
                dailyCalorieTarget = null,
                weeklyPaceKg = 0.0,
                isPaceAdjustedForSafety = false,
                guidance = "Choose a target date in the future to get a daily calorie recommendation."
            )
        }

        val weightChangeKg = weightGoalKg - currentWeightKg
        val requestedDailyAdjustment = (weightChangeKg * KCAL_PER_KG) / daysRemaining

        // Loss: at most ~1% of body weight a week (and never more than 750 kcal/day).
        // Gain: at most ~0.5% a week - faster than that is mostly fat, not muscle.
        val maxLoss = minOf(MAX_SAFE_DAILY_ADJUSTMENT, currentWeightKg * MAX_WEEKLY_LOSS_FRACTION * KCAL_PER_KG / 7.0)
        val maxGain = minOf(MAX_DAILY_SURPLUS, currentWeightKg * MAX_WEEKLY_GAIN_FRACTION * KCAL_PER_KG / 7.0)

        var safetyAdjusted = false
        var dailyAdjustment = requestedDailyAdjustment.coerceIn(-maxLoss, maxGain)
        if (dailyAdjustment != requestedDailyAdjustment) safetyAdjusted = true

        var target = (tdee + dailyAdjustment).roundToInt()
        // Never eat below resting burn or the usual minimum for your sex - unless maintenance
        // itself is lower, in which case maintenance is the floor.
        val floor = minOf(maxOf(minimumCalories(sex), bmr ?: 0.0), tdee).roundToInt()
        if (weightChangeKg < 0 && target < floor) {
            target = floor
            dailyAdjustment = target - tdee
            safetyAdjusted = true
        }

        val actualWeeklyPaceKg = (dailyAdjustment * 7.0) / KCAL_PER_KG
        val paceText = String.format(Locale.US, "%.2f kg/week", abs(actualWeeklyPaceKg))
        val guidance = when {
            safetyAdjusted && weightChangeKg < 0 && abs(actualWeeklyPaceKg) < 0.05 ->
                "Eating less than this would take you below your resting burn - more daily movement is the safer way to lose weight from here."
            safetyAdjusted && weightChangeKg < 0 -> "Your target date implies a faster loss than we'd recommend - capped at a safer pace of about $paceText."
            safetyAdjusted && weightChangeKg > 0 -> "Your target date implies a faster gain than we'd recommend - capped at about $paceText so it's mostly muscle, not fat."
            weightChangeKg < -0.05 -> "On track to lose about $paceText at this target."
            weightChangeKg > 0.05 -> "On track to gain about $paceText at this target."
            else -> "This target maintains your current weight."
        }

        return CalorieGoalRecommendation(
            dailyCalorieTarget = target,
            weeklyPaceKg = actualWeeklyPaceKg,
            isPaceAdjustedForSafety = safetyAdjusted,
            guidance = guidance
        )
    }

    companion object {
        const val KCAL_PER_KG = 7700.0
        const val MAX_SAFE_DAILY_ADJUSTMENT = 750.0 // roughly a 0.68 kg/week ceiling
        const val MAX_DAILY_SURPLUS = 500.0
        const val MAX_WEEKLY_LOSS_FRACTION = 0.01
        const val MAX_WEEKLY_GAIN_FRACTION = 0.005
        const val MIN_SAFE_CALORIES = 1200.0
        const val MIN_SAFE_CALORIES_MALE = 1500.0

        /** The usual lowest daily intake without medical supervision; OTHER sits between. */
        fun minimumCalories(sex: BiologicalSex?): Double = when (sex) {
            BiologicalSex.MALE -> MIN_SAFE_CALORIES_MALE
            BiologicalSex.FEMALE -> MIN_SAFE_CALORIES
            BiologicalSex.OTHER, null -> (MIN_SAFE_CALORIES + MIN_SAFE_CALORIES_MALE) / 2
        }
    }
}
