package com.kevan.hangry.domain.model

import java.time.LocalDate

/**
 * Maintenance calories estimated from the last 7 full days of real activity, counted the same
 * way as Daily Activity: resting burn for the minutes you weren't active, plus every step and
 * every workout in full, plus ~10% for digesting food (the thermic effect of food).
 */
data class EnergyBalanceEstimate(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    /** Days in the window that had step data - the averages are over these days only. */
    val daysWithData: Int,
    val bmrKcal: Double,
    /** "Katch-McArdle" when a measured body-fat scan is available, else "Mifflin-St Jeor". */
    val bmrMethod: String,
    /** Resting burn over the minutes that weren't active (BMR × inactive share of the day). */
    val restingKcal: Double,
    /** Whole workouts plus 1 minute per 150 steps outside them. */
    val avgActiveMinutes: Double,
    val avgTotalSteps: Double,
    /** Steps not already covered by a workout's calories. */
    val avgCountedSteps: Double,
    /** The full cost of a step, including the resting burn while taking it. */
    val kcalPerStep: Double,
    val stepKcal: Double,
    val avgWorkoutKcal: Double,
    val workoutsCounted: Int,
    /** Workouts with no calorie data at all - they add nothing to the estimate. */
    val workoutsWithoutCalories: Int,
    /** Thermic effect of food: 10% of resting + steps + workouts. */
    val tefKcal: Double,
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
