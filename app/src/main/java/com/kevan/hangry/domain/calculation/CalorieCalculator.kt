package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.CalorieBurnResult
import com.kevan.hangry.domain.model.CalorieGoalRecommendation
import java.time.LocalDate

interface CalorieCalculator {
    /** Mifflin-St Jeor resting metabolic rate. See CALCULATIONS.md §8.1. */
    fun calculateBmr(weightKg: Double, heightCm: Double, age: Int, biologicalSex: BiologicalSex): Double

    /**
     * @param totalActiveCalories whole-day active calories from Health Connect (steps +
     *   active-calories records), encompassing both NEAT and any logged exercise.
     * @param exerciseCalories the portion of [totalActiveCalories] attributable to logged
     *   workouts specifically (estimated - Health Connect doesn't expose true per-workout
     *   calories on this data path). NEAT is derived as the remainder.
     * @param activeMinutes time spent active today. Active calories already include the
     *   resting burn during that time, so BMR only covers the remaining minutes.
     */
    fun calculateDailyBurn(
        bmr: Double?,
        totalActiveCalories: Double?,
        exerciseCalories: Double,
        activeMinutes: Int? = null
    ): CalorieBurnResult

    /**
     * Null when there isn't enough information (TDEE, current weight, goal weight, or a
     * target date) to compute a recommendation yet.
     */
    fun recommendDailyCalorieGoal(
        tdee: Double?,
        currentWeightKg: Double?,
        weightGoalKg: Double?,
        targetDate: LocalDate?,
        today: LocalDate
    ): CalorieGoalRecommendation?
}
