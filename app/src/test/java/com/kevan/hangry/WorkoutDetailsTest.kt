package com.kevan.hangry

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.kevan.hangry.data.datasource.HealthConnectWorkoutTypes
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.model.WorkoutCategory
import com.kevan.hangry.domain.model.WorkoutText
import com.kevan.hangry.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WorkoutDetailsTest {

    private fun workout(
        type: String,
        minutes: Int = 30,
        title: String? = null,
        distance: Double? = null,
        avgHr: Double? = null,
        maxHr: Double? = null,
        sets: Int? = null,
        reps: Int? = null,
        total: Double? = null
    ) = ExerciseSessionEntity(
        recordFingerprint = "w-$type-$minutes",
        exerciseType = type,
        title = title,
        startTime = Instant.parse("2026-09-23T07:00:00Z"),
        endTime = Instant.parse("2026-09-23T07:00:00Z").plusSeconds(minutes * 60L),
        durationMinutes = minutes,
        totalCalories = total,
        distanceMeters = distance,
        avgHeartRate = avgHr,
        maxHeartRate = maxHr,
        setCount = sets,
        repCount = reps
    )

    @Test
    fun healthConnectTypesMapToSpecificWorkouts() {
        assertEquals(WorkoutType.WEIGHTLIFTING, HealthConnectWorkoutTypes.fromHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals(WorkoutType.ELLIPTICAL, HealthConnectWorkoutTypes.fromHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL))
        assertEquals(WorkoutType.TENNIS, HealthConnectWorkoutTypes.fromHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_TENNIS))
        assertEquals(WorkoutType.OTHER_WORKOUT, HealthConnectWorkoutTypes.fromHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
    }

    @Test
    fun oldSavedIdsStillResolve() {
        assertEquals(WorkoutType.BIKING, WorkoutType.fromId("CYCLING"))
        assertEquals(WorkoutType.SWIMMING_POOL, WorkoutType.fromId("SWIMMING"))
        assertEquals(WorkoutType.HIGH_INTENSITY_INTERVAL_TRAINING, WorkoutType.fromId("HIIT"))
        assertEquals(WorkoutType.OTHER_WORKOUT, WorkoutType.fromId("OTHER"))
        assertEquals(WorkoutType.OTHER_WORKOUT, WorkoutType.fromId(null))
        assertEquals(WorkoutType.WEIGHTLIFTING, WorkoutType.fromId("weightlifting"))
    }

    @Test
    fun namePrefersTheSourcesTitle() {
        assertEquals("Weightlifting", WorkoutText.name(workout("WEIGHTLIFTING")))
        assertEquals("45 min", WorkoutText.subtitle(workout("WEIGHTLIFTING", minutes = 45)))
        val titled = workout("WEIGHTLIFTING", minutes = 75, title = "Push day")
        assertEquals("Push day", WorkoutText.name(titled))
        assertEquals("Weightlifting · 1 h 15 min", WorkoutText.subtitle(titled))
    }

    @Test
    fun runShowsDistancePaceAndHeartRate() {
        val details = WorkoutText.details(workout("RUNNING", minutes = 30, distance = 5_000.0, avgHr = 151.4, maxHr = 176.0))
        assertEquals(listOf("5.00 km", "6:00 /km", "♥ 151 avg · 176 max"), details)
    }

    @Test
    fun rideShowsSpeedAndSwimShowsPacePer100m() {
        assertEquals(listOf("20.00 km", "24.0 km/h"), WorkoutText.details(workout("BIKING", minutes = 50, distance = 20_000.0)))
        assertEquals(listOf("1.50 km", "2:00 /100 m"), WorkoutText.details(workout("SWIMMING_POOL", minutes = 30, distance = 1_500.0)))
    }

    @Test
    fun strengthShowsSetsAndReps() {
        assertEquals(listOf("5 sets · 48 reps"), WorkoutText.details(workout("WEIGHTLIFTING", sets = 5, reps = 48)))
    }

    @Test
    fun segmentSummaryGroupsSets() {
        val summary = HealthConnectWorkoutTypes.segmentSummary(
            listOf(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS to 10,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS to 10,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS to 10,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT to 8,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT to 6
            )
        )
        assertEquals("Bench press 3×10 · Squat 2 sets, 14 reps", summary)
        assertNull(HealthConnectWorkoutTypes.segmentSummary(emptyList()))
    }

    @Test
    fun trainingLoadUsesTheWorkoutFamilyAndFullCalories() {
        assertEquals(WorkoutCategory.STRENGTH, WorkoutType.WEIGHTLIFTING.category)
        val calculator = HangryTrainingLoadCalculator()
        // 30 min × 0.8 = 24, plus 400 kcal total / 100 × 5 = 20, × 1.1 for strength.
        assertEquals((24.0 + 20.0) * 1.1, calculator.estimateSessionLoad(workout("WEIGHTLIFTING", total = 400.0)), 1e-9)
        // A walk is lighter work per minute than a run.
        assertEquals(24.0 * 0.7, calculator.estimateSessionLoad(workout("WALKING")), 1e-9)
    }
}
