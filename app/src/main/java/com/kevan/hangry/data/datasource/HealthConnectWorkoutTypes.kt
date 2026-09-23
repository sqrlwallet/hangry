package com.kevan.hangry.data.datasource

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord as E
import com.kevan.hangry.domain.model.WorkoutType

/** Translates Health Connect's integer exercise and segment codes into the app's names. */
object HealthConnectWorkoutTypes {

    fun fromHealthConnect(type: Int): WorkoutType = when (type) {
        E.EXERCISE_TYPE_BADMINTON -> WorkoutType.BADMINTON
        E.EXERCISE_TYPE_BASEBALL -> WorkoutType.BASEBALL
        E.EXERCISE_TYPE_BASKETBALL -> WorkoutType.BASKETBALL
        E.EXERCISE_TYPE_BIKING -> WorkoutType.BIKING
        E.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutType.BIKING_STATIONARY
        E.EXERCISE_TYPE_BOOT_CAMP -> WorkoutType.BOOT_CAMP
        E.EXERCISE_TYPE_BOXING -> WorkoutType.BOXING
        E.EXERCISE_TYPE_CALISTHENICS -> WorkoutType.CALISTHENICS
        E.EXERCISE_TYPE_CRICKET -> WorkoutType.CRICKET
        E.EXERCISE_TYPE_DANCING -> WorkoutType.DANCING
        E.EXERCISE_TYPE_ELLIPTICAL -> WorkoutType.ELLIPTICAL
        E.EXERCISE_TYPE_EXERCISE_CLASS -> WorkoutType.EXERCISE_CLASS
        E.EXERCISE_TYPE_FENCING -> WorkoutType.FENCING
        E.EXERCISE_TYPE_FOOTBALL_AMERICAN -> WorkoutType.FOOTBALL_AMERICAN
        E.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> WorkoutType.FOOTBALL_AUSTRALIAN
        E.EXERCISE_TYPE_FRISBEE_DISC -> WorkoutType.FRISBEE_DISC
        E.EXERCISE_TYPE_GOLF -> WorkoutType.GOLF
        E.EXERCISE_TYPE_GUIDED_BREATHING -> WorkoutType.GUIDED_BREATHING
        E.EXERCISE_TYPE_GYMNASTICS -> WorkoutType.GYMNASTICS
        E.EXERCISE_TYPE_HANDBALL -> WorkoutType.HANDBALL
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING
        E.EXERCISE_TYPE_HIKING -> WorkoutType.HIKING
        E.EXERCISE_TYPE_ICE_HOCKEY -> WorkoutType.ICE_HOCKEY
        E.EXERCISE_TYPE_ICE_SKATING -> WorkoutType.ICE_SKATING
        E.EXERCISE_TYPE_MARTIAL_ARTS -> WorkoutType.MARTIAL_ARTS
        E.EXERCISE_TYPE_PADDLING -> WorkoutType.PADDLING
        E.EXERCISE_TYPE_PARAGLIDING -> WorkoutType.PARAGLIDING
        E.EXERCISE_TYPE_PILATES -> WorkoutType.PILATES
        E.EXERCISE_TYPE_RACQUETBALL -> WorkoutType.RACQUETBALL
        E.EXERCISE_TYPE_ROCK_CLIMBING -> WorkoutType.ROCK_CLIMBING
        E.EXERCISE_TYPE_ROLLER_HOCKEY -> WorkoutType.ROLLER_HOCKEY
        E.EXERCISE_TYPE_ROWING -> WorkoutType.ROWING
        E.EXERCISE_TYPE_ROWING_MACHINE -> WorkoutType.ROWING_MACHINE
        E.EXERCISE_TYPE_RUGBY -> WorkoutType.RUGBY
        E.EXERCISE_TYPE_RUNNING -> WorkoutType.RUNNING
        E.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutType.RUNNING_TREADMILL
        E.EXERCISE_TYPE_SAILING -> WorkoutType.SAILING
        E.EXERCISE_TYPE_SCUBA_DIVING -> WorkoutType.SCUBA_DIVING
        E.EXERCISE_TYPE_SKATING -> WorkoutType.SKATING
        E.EXERCISE_TYPE_SKIING -> WorkoutType.SKIING
        E.EXERCISE_TYPE_SNOWBOARDING -> WorkoutType.SNOWBOARDING
        E.EXERCISE_TYPE_SNOWSHOEING -> WorkoutType.SNOWSHOEING
        E.EXERCISE_TYPE_SOCCER -> WorkoutType.SOCCER
        E.EXERCISE_TYPE_SOFTBALL -> WorkoutType.SOFTBALL
        E.EXERCISE_TYPE_SQUASH -> WorkoutType.SQUASH
        E.EXERCISE_TYPE_STAIR_CLIMBING -> WorkoutType.STAIR_CLIMBING
        E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> WorkoutType.STAIR_CLIMBING_MACHINE
        E.EXERCISE_TYPE_STRENGTH_TRAINING -> WorkoutType.STRENGTH_TRAINING
        E.EXERCISE_TYPE_STRETCHING -> WorkoutType.STRETCHING
        E.EXERCISE_TYPE_SURFING -> WorkoutType.SURFING
        E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WorkoutType.SWIMMING_OPEN_WATER
        E.EXERCISE_TYPE_SWIMMING_POOL -> WorkoutType.SWIMMING_POOL
        E.EXERCISE_TYPE_TABLE_TENNIS -> WorkoutType.TABLE_TENNIS
        E.EXERCISE_TYPE_TENNIS -> WorkoutType.TENNIS
        E.EXERCISE_TYPE_VOLLEYBALL -> WorkoutType.VOLLEYBALL
        E.EXERCISE_TYPE_WALKING -> WorkoutType.WALKING
        E.EXERCISE_TYPE_WATER_POLO -> WorkoutType.WATER_POLO
        E.EXERCISE_TYPE_WEIGHTLIFTING -> WorkoutType.WEIGHTLIFTING
        E.EXERCISE_TYPE_WHEELCHAIR -> WorkoutType.WHEELCHAIR
        E.EXERCISE_TYPE_YOGA -> WorkoutType.YOGA
        else -> WorkoutType.OTHER_WORKOUT
    }

    /** Names for the strength moves apps commonly record; anything else is just "Exercise". */
    private fun segmentName(type: Int): String = when (type) {
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL -> "Arm curl"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION -> "Back extension"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS -> "Barbell shoulder press"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS -> "Bench press"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE -> "Burpee"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH -> "Crunch"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT -> "Deadlift"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW -> "Dumbbell row"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST -> "Hip thrust"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK -> "Jumping jack"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING -> "Kettlebell swing"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN -> "Lat pulldown"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE -> "Lateral raise"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL -> "Leg curl"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION -> "Leg extension"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS -> "Leg press"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE -> "Lunge"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK -> "Plank"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP -> "Pull-up"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS -> "Shoulder press"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP -> "Sit-up"
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT -> "Squat"
        else -> "Exercise"
    }

    /** "Bench press 3×10 · Squat 4×8" from (segment type, reps) pairs, in the order done. */
    fun segmentSummary(sets: List<Pair<Int, Int>>): String? {
        if (sets.isEmpty()) return null
        return sets.groupBy { segmentName(it.first) }.entries.joinToString(" · ") { (name, group) ->
            val reps = group.map { it.second }
            val same = reps.distinct().size == 1 && reps.first() > 0
            when {
                same -> "$name ${group.size}×${reps.first()}"
                reps.sum() > 0 -> "$name ${group.size} sets, ${reps.sum()} reps"
                else -> "$name ${group.size} set${if (group.size == 1) "" else "s"}"
            }
        }
    }
}
