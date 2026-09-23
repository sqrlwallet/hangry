package com.kevan.hangry.domain.model

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import java.util.Locale
import kotlin.math.roundToInt

/** How a workout is named and described across Training, the day sheet and Ask Dash. */
object WorkoutText {

    fun type(workout: ExerciseSessionEntity): WorkoutType = WorkoutType.fromId(workout.exerciseType)

    /** The app's own title if the source gave one ("Push day"), otherwise the activity ("Weightlifting"). */
    fun name(workout: ExerciseSessionEntity): String = workout.title?.takeIf { it.isNotBlank() } ?: type(workout).label

    /** "Weightlifting · 45 min" when there's a custom title, otherwise just "45 min". */
    fun subtitle(workout: ExerciseSessionEntity): String {
        val duration = durationText(workout.durationMinutes)
        return if (workout.title.isNullOrBlank()) duration else "${type(workout).label} · $duration"
    }

    fun durationText(minutes: Int): String = when {
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }

    /**
     * The details worth showing, in order: distance, pace or speed, heart rate, climb, power,
     * sets and reps, laps. Anything the source didn't record is simply left out.
     */
    fun details(workout: ExerciseSessionEntity): List<String> = buildList {
        val type = type(workout)
        val distance = workout.distanceMeters?.takeIf { it >= 50 }
        if (distance != null) {
            add(if (distance >= 1000) String.format(Locale.US, "%.2f km", distance / 1000) else "${distance.roundToInt()} m")
            if (workout.durationMinutes > 0) {
                val minutes = workout.durationMinutes.toDouble()
                if (type.category == WorkoutCategory.SWIM) {
                    add("${formatPace(minutes / (distance / 100))} /100 m")
                } else if (type.showsPace) {
                    add("${formatPace(minutes / (distance / 1000))} /km")
                } else {
                    add(String.format(Locale.US, "%.1f km/h", distance / 1000 / (minutes / 60)))
                }
            }
        }
        val avgHr = workout.avgHeartRate?.roundToInt()
        val maxHr = workout.maxHeartRate?.roundToInt()
        when {
            avgHr != null && maxHr != null -> add("♥ $avgHr avg · $maxHr max")
            avgHr != null -> add("♥ $avgHr avg")
        }
        workout.elevationGainMeters?.takeIf { it >= 1 }?.let { add("↑ ${it.roundToInt()} m") }
        workout.avgPowerWatts?.let { add("${it.roundToInt()} W avg") }
        val sets = workout.setCount
        val reps = workout.repCount
        when {
            sets != null && reps != null -> add("$sets sets · $reps reps")
            sets != null -> add("$sets sets")
        }
        workout.lapCount?.let { add("$it lap${if (it == 1) "" else "s"}") }
    }

    /** Minutes as "m:ss", e.g. 5.5 → "5:30". */
    fun formatPace(minutes: Double): String {
        val totalSeconds = (minutes * 60).roundToInt()
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
