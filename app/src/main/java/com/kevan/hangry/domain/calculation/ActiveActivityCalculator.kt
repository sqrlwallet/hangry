package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A day's active calories and active time:
 *
 * - **Workouts** count in full: every calorie burned during the workout (its total, not just
 *   what was burned above resting) and every minute of it.
 * - **Steps** outside workouts count too: every 150 steps is one active minute, and each step's
 *   full cost - the walking itself plus the resting burn during that walking time.
 *
 * Steps taken during a workout that has calorie data aren't counted again as steps, since the
 * workout's calories already include them.
 */
object ActiveActivityCalculator {

    /** A brisk walking cadence: 150 steps adds one minute of active time. */
    const val STEPS_PER_ACTIVE_MINUTE = 150.0
    private const val MINUTES_PER_DAY = 1440.0

    data class ActiveDay(
        val activeCalories: Double?,
        val activeMinutes: Int?,
        val workoutCalories: Double,
        val workoutMinutes: Int,
        /** Null when weight or height is missing, so a step's cost can't be worked out. */
        val stepCalories: Double?,
        val stepMinutes: Int
    )

    fun calculate(
        totalSteps: Long?,
        workouts: List<ExerciseSessionEntity>,
        weightKg: Double?,
        heightCm: Double?,
        bmr: Double?,
        /** Health Connect's own active-calorie total - only used when a step's cost is unknown. */
        recordedActiveCalories: Double? = null
    ): ActiveDay {
        val workoutMinutes = workouts.sumOf { it.durationMinutes.coerceAtLeast(0) }
        val workoutCalories = workouts.sumOf { workoutCalories(it, bmr) ?: 0.0 }

        val steps = totalSteps?.coerceAtLeast(0) ?: 0L
        // Time: steps in any workout are already inside that workout's minutes.
        val stepsOutsideWorkouts = max(0L, steps - workouts.sumOf { it.steps ?: 0L })
        // Calories: only skip steps whose workout brought its own calorie figure.
        val stepsNotInCaloriedWorkouts = max(0L, steps - workouts.filter { workoutCalories(it, bmr) != null }.sumOf { it.steps ?: 0L })

        val stepMinutes = (stepsOutsideWorkouts / STEPS_PER_ACTIVE_MINUTE).roundToInt()
        val stepCalories = if (weightKg != null && heightCm != null) {
            stepsNotInCaloriedWorkouts * kcalPerStep(weightKg, heightCm, bmr)
        } else null

        val hasAnyData = totalSteps != null || workouts.isNotEmpty()
        val activeCalories = when {
            !hasAnyData -> recordedActiveCalories
            stepCalories != null -> workoutCalories + stepCalories
            // Can't cost steps without weight/height: fall back to what Health Connect recorded.
            recordedActiveCalories != null -> max(recordedActiveCalories, workoutCalories)
            workoutCalories > 0 -> workoutCalories
            else -> null
        }
        return ActiveDay(
            activeCalories = activeCalories,
            activeMinutes = if (hasAnyData) workoutMinutes + stepMinutes else null,
            workoutCalories = workoutCalories,
            workoutMinutes = workoutMinutes,
            stepCalories = stepCalories,
            stepMinutes = stepMinutes
        )
    }

    /**
     * Everything burned during the workout: the recorded total if there is one, otherwise the
     * recorded active calories plus the resting burn over the workout's duration.
     */
    fun workoutCalories(workout: ExerciseSessionEntity, bmr: Double?): Double? {
        workout.totalCalories?.takeIf { it > 0 }?.let { return it }
        val active = workout.activeCalories?.takeIf { it > 0 } ?: return null
        val restingShare = (bmr ?: 0.0) * workout.durationMinutes.coerceAtLeast(0) / MINUTES_PER_DAY
        return active + restingShare
    }

    /**
     * The full cost of one step: the walking itself (see [EnergyBalanceCalculator.kcalPerStep])
     * plus the resting burn during the 1/150th of a minute it takes.
     */
    fun kcalPerStep(weightKg: Double, heightCm: Double, bmr: Double?): Double =
        EnergyBalanceCalculator.kcalPerStep(weightKg, heightCm) + (bmr ?: 0.0) / MINUTES_PER_DAY / STEPS_PER_ACTIVE_MINUTE

    /**
     * The whole day's burn without counting resting calories twice: resting burn for the
     * minutes that weren't active, plus everything burned while active.
     */
    fun totalBurn(bmr: Double?, activeCalories: Double?, activeMinutes: Int?): Double? {
        bmr ?: return activeCalories
        val restingMinutes = (MINUTES_PER_DAY - (activeMinutes ?: 0)).coerceIn(0.0, MINUTES_PER_DAY)
        return bmr * restingMinutes / MINUTES_PER_DAY + (activeCalories ?: 0.0)
    }
}
