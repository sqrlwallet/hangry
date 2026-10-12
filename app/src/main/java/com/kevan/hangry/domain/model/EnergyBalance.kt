package com.kevan.hangry.domain.model

import java.time.LocalDate

/**
 * Maintenance calories estimated from the last 7 full days of real activity, counted the same
 * way as Daily Activity: resting burn for the minutes you weren't active, plus every step and
 * every workout in full, plus ~10% for digesting food (the thermic effect of food). Once there
 * are enough food logs and weigh-ins, it's blended with what intake vs. weight change shows
 * (see [AdaptiveMaintenance]).
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
    /** Resting + steps + workouts + TEF, before any blending with [adaptive]. */
    val formulaMaintenanceKcal: Double,
    /** Learnt from logged intake and weight trend; null until there's enough of both. */
    val adaptive: AdaptiveMaintenance?,
    /** Why [adaptive] isn't used yet, when it isn't. */
    val adaptiveMissing: String?,
    /** The number to eat at to maintain: [formulaMaintenanceKcal] blended with [adaptive]. */
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

/**
 * Maintenance learnt from real life: if you ate an average of [avgIntakeKcal] and your weight
 * trend moved by [weeklyWeightChangeKg], your body burnt about intake - change × 7,700 kcal/kg.
 * It catches what formulas miss (individual metabolism, logging habits, untracked activity).
 */
data class AdaptiveMaintenance(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    /** Days with a plausibly complete food log - the intake average is over these only. */
    val loggedDays: Int,
    val avgIntakeKcal: Double,
    val weighIns: Int,
    /** Least-squares slope of daily weights, so one heavy morning doesn't swing it. */
    val weeklyWeightChangeKg: Double,
    val maintenanceKcal: Double,
    /** How much of the final number comes from this (0-1); the rest is the formula. */
    val weight: Double
)
