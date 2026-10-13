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
    /** Against [maintenanceKcal] - a typical day. Null when no goal weight is set. */
    val goal: CalorieGoalRecommendation?,
    /** The inputs behind [bmrKcal] and a step's cost, kept for working out a day's target. */
    val weightKg: Double,
    val heightCm: Double,
    val sex: BiologicalSex,
    /** Active minutes from steps outside workouts, per day. */
    val avgStepMinutes: Double,
    /** Today's calorie target - see [DailyCalorieTarget]. */
    val today: DailyCalorieTarget? = null
) {
    /** Negative = daily deficit, positive = surplus, relative to maintenance. */
    val dailyAdjustmentKcal: Double? get() = goal?.dailyCalorieTarget?.let { it - maintenanceKcal }
}

/**
 * One day's calorie target, worked out for that day rather than averaged over a week:
 *
 *   burn   = (resting + NEAT + workouts) × 1.10 digestion × what your logs have taught
 *   target = burn ± the daily deficit or surplus for your goal weight
 *
 * - Resting is BMR over the day's inactive minutes (active minutes carry their own resting burn).
 * - NEAT is the day's steps outside workouts, or your 7-day average if that's higher - so the
 *   morning target isn't starved before you've walked anywhere, and a big walking day earns more.
 * - Workouts are the day's actual workouts, every calorie, as soon as they sync.
 * - [adaptiveFactor] scales the formula by how far your intake vs. weight trend says it's off.
 */
data class DailyCalorieTarget(
    val date: LocalDate,
    val bmrKcal: Double,
    val restingKcal: Double,
    val neatKcal: Double,
    val neatSteps: Double,
    /** True when the day's own steps beat the 7-day average and set NEAT. */
    val neatFromDay: Boolean,
    val workoutKcal: Double,
    val workoutsCounted: Int,
    /** The day's workouts with no calorie data - they add nothing. */
    val workoutsWithoutCalories: Int,
    val tefKcal: Double,
    /** Maintenance learnt from your logs ÷ the formula's; 1.0 until there's enough to learn from. */
    val adaptiveFactor: Double,
    /** What the day burns: the number to eat at to stay the same weight. */
    val burnKcal: Double,
    /** The goal's deficit or surplus on top of [burnKcal]; null when no goal weight is set. */
    val goal: CalorieGoalRecommendation?,
    val targetKcal: Int
) {
    /** Negative = deficit, positive = surplus. */
    val adjustmentKcal: Double get() = targetKcal - burnKcal
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
