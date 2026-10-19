package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.WorkoutCategory
import com.kevan.hangry.domain.model.WorkoutType

/**
 * Everything burned during a workout that came with no calorie data, so it still counts:
 *
 * - **Heart rate** (Keytel et al. 2005, J Sports Sci) when the workout's average is in the
 *   exercising range and weight, age and sex are known.
 * - Otherwise **type × duration**: the activity's MET from the Compendium of Physical Activities
 *   (Herrmann et al. 2024) × your own resting burn per minute (BMR ÷ 1440, the Compendium's
 *   "corrected MET"), or × 1 kcal/kg/h when BMR isn't known.
 *
 * Both are gross figures - the resting burn during the workout included - like a recorded total.
 */
object WorkoutCalorieEstimator {

    /** Who did the workout; any of it may be unknown. */
    data class Person(val weightKg: Double?, val age: Int?, val sex: BiologicalSex?)

    /** Keytel's equations were fitted on exercising heart rates; below this it's mostly rest. */
    const val MIN_EXERCISE_HR = 90.0
    private const val KJ_PER_KCAL = 4.184
    private const val MINUTES_PER_DAY = 1440.0

    fun estimate(workout: ExerciseSessionEntity, bmr: Double?, person: Person?): Double? {
        val minutes = workout.durationMinutes.takeIf { it > 0 } ?: return null
        val metKcal = fromMet(workout, minutes, bmr, person?.weightKg)
        val hrKcal = person?.let { fromHeartRate(workout.avgHeartRate, minutes, it) }
        // A heart-rate figure under resting burn means the equation's out of its range.
        val restingKcal = bmr?.let { it * minutes / MINUTES_PER_DAY } ?: 0.0
        return hrKcal?.takeIf { it > restingKcal } ?: metKcal
    }

    /** Keytel 2005 gross energy expenditure, kJ/min → kcal over the workout. */
    fun fromHeartRate(avgHr: Double?, minutes: Int, person: Person): Double? {
        val hr = avgHr?.takeIf { it >= MIN_EXERCISE_HR } ?: return null
        val w = person.weightKg ?: return null
        val a = person.age?.toDouble() ?: return null
        val male = -55.0969 + 0.6309 * hr + 0.1988 * w + 0.2017 * a
        val female = -20.4022 + 0.4472 * hr - 0.1263 * w + 0.074 * a
        val kjPerMin = when (person.sex ?: return null) {
            BiologicalSex.MALE -> male
            BiologicalSex.FEMALE -> female
            BiologicalSex.OTHER -> (male + female) / 2
        }
        return (kjPerMin / KJ_PER_KCAL * minutes).takeIf { it > 0 }
    }

    fun fromMet(workout: ExerciseSessionEntity, minutes: Int, bmr: Double?, weightKg: Double?): Double? {
        val kcalPerMet = bmr?.let { it / MINUTES_PER_DAY } ?: weightKg?.let { it / 60.0 } ?: return null
        return met(WorkoutType.fromId(workout.exerciseType)) * kcalPerMet * minutes
    }

    /** Compendium 2024 values for a general/moderate effort - the type's own where it differs. */
    fun met(type: WorkoutType): Double = TYPE_MET[type] ?: CATEGORY_MET.getValue(type.category)

    private val CATEGORY_MET = mapOf(
        WorkoutCategory.RUN to 9.0,
        WorkoutCategory.HIIT to 8.0,
        WorkoutCategory.CYCLE to 7.0,
        WorkoutCategory.SWIM to 6.0,
        WorkoutCategory.SPORT to 6.5,
        WorkoutCategory.WINTER to 6.5,
        WorkoutCategory.STRENGTH to 5.0,
        WorkoutCategory.WATER to 5.0,
        WorkoutCategory.OTHER to 4.0,
        WorkoutCategory.WALK to 4.0,
        WorkoutCategory.MIND_BODY to 2.5
    )

    private val TYPE_MET = mapOf(
        WorkoutType.ELLIPTICAL to 5.0,
        WorkoutType.HIKING to 6.0,
        WorkoutType.GOLF to 4.8,
        WorkoutType.STAIR_CLIMBING to 8.0,
        WorkoutType.DANCING to 5.0,
        WorkoutType.PILATES to 3.0,
        WorkoutType.STRETCHING to 2.3,
        WorkoutType.GUIDED_BREATHING to 1.3,
        WorkoutType.TABLE_TENNIS to 4.0,
        WorkoutType.VOLLEYBALL to 4.0,
        WorkoutType.SAILING to 3.0,
        WorkoutType.PARAGLIDING to 3.5,
        WorkoutType.ROCK_CLIMBING to 7.5,
        WorkoutType.SOCCER to 7.0,
        WorkoutType.TENNIS to 7.0
    )
}
