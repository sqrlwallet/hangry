package com.kevan.hangry.domain.model

import java.time.LocalDate

/**
 * Maintenance calories estimated from the last 7 full days of real activity:
 * BMR + NEAT (from a third of average daily steps) + average workout calories.
 */
data class EnergyBalanceEstimate(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    /** Days in the window that had step data - the averages are over these days only. */
    val daysWithData: Int,
    val bmrKcal: Double,
    val avgTotalSteps: Double,
    /** avgTotalSteps ÷ 3 - the share of steps counted as NEAT (see EnergyBalanceCalculator). */
    val avgNeatSteps: Double,
    val kcalPerStep: Double,
    val neatKcal: Double,
    val avgWorkoutKcal: Double,
    val workoutsCounted: Int,
    /** Workouts with no calorie data at all - they add nothing to the estimate. */
    val workoutsWithoutCalories: Int,
    val maintenanceKcal: Double,
    val goalWeightKg: Double?,
    val goalDate: LocalDate?,
    /** Null when no goal weight + target date is set. */
    val goal: CalorieGoalRecommendation?
) {
    /** Negative = daily deficit, positive = surplus, relative to maintenance. */
    val dailyAdjustmentKcal: Double? get() = goal?.dailyCalorieTarget?.let { it - maintenanceKcal }
}

data class EnergyBalanceResult(
    val estimate: EnergyBalanceEstimate?,
    /** What's needed before an estimate can be made; empty when [estimate] is present. */
    val missing: List<String>
)
