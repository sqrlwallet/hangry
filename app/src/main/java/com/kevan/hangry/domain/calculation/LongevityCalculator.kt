package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.LongevityPillar
import com.kevan.hangry.domain.model.LongevityWeek
import com.kevan.hangry.domain.model.PillarProgress
import com.kevan.hangry.domain.model.WorkoutCategory
import com.kevan.hangry.domain.model.WorkoutType
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A week's progress on the five longevity pillars. Strength and mobility come from logged
 * workouts, cardio minutes from time in the user's own heart-rate zones (zone 2, and zones
 * 4-5), and anything that isn't a workout - balance drills, a quick stretch - from tick-offs.
 */
object LongevityCalculator {

    private const val MAX_GAP_MINUTES = 10.0

    fun week(
        weekStart: LocalDate,
        workouts: List<ExerciseSessionEntity>,
        heartRate: List<HeartRateSampleEntity>,
        sleep: List<SleepSessionEntity>,
        zones: HeartRateZones,
        checkIns: Map<LongevityPillar, Set<LocalDate>>,
        zone: ZoneId = ZoneId.systemDefault(),
        /** Days with a strength-level program session; a logged workout the same day isn't double-counted. */
        extraStrengthDays: Set<LocalDate> = emptySet(),
        /** Days with a mobility program session (mobility, knees, back, shoulders). */
        extraMobilityDays: Set<LocalDate> = emptySet()
    ): LongevityWeek {
        val weekDays = (0L..6L).map { weekStart.plusDays(it) }.toSet()
        val inWeek = workouts.filter { it.startTime.atZone(zone).toLocalDate() in weekDays }
        fun dayOf(w: ExerciseSessionEntity) = w.startTime.atZone(zone).toLocalDate()
        val category = { w: ExerciseSessionEntity -> WorkoutType.fromId(w.exerciseType) }

        val strength = inWeek.filter { category(it).category == WorkoutCategory.STRENGTH }
        // Breathing sessions are logged as mind-body too, but they aren't mobility work.
        val mobilityWorkoutDays = inWeek
            .filter { category(it).category == WorkoutCategory.MIND_BODY && category(it) != WorkoutType.GUIDED_BREATHING }
            .map(::dayOf).toSet()

        // Minutes in each personal zone while awake; a gap over 10 minutes means the device was off.
        val awake = heartRate
            .filter { s -> sleep.none { !s.timestamp.isBefore(it.startTime) && s.timestamp.isBefore(it.endTime) } }
            .sortedBy { it.timestamp }
        val zoneMinutes = DoubleArray(5)
        for (i in 0 until awake.size - 1) {
            val gap = Duration.between(awake[i].timestamp, awake[i + 1].timestamp).toMillis() / 60000.0
            if (gap <= 0.0 || gap > MAX_GAP_MINUTES) continue
            val z = zones.zoneIndex(awake[i].bpm)
            if (z >= 0) zoneMinutes[z] += gap
        }
        val hasHeartRate = awake.size >= 2

        val mobilityChecked = checkIns[LongevityPillar.MOBILITY].orEmpty() intersect weekDays
        val balanceChecked = checkIns[LongevityPillar.BALANCE].orEmpty() intersect weekDays
        val mobilityDays = mobilityWorkoutDays + mobilityChecked + (extraMobilityDays intersect weekDays)

        return LongevityWeek(
            start = weekStart,
            pillars = listOf(
                (strength.map(::dayOf).toSet()).let { workoutDays ->
                    val extra = (extraStrengthDays intersect weekDays) - workoutDays
                    PillarProgress(LongevityPillar.STRENGTH, strength.size + extra.size, days = workoutDays + extra)
                },
                PillarProgress(LongevityPillar.ZONE2, zoneMinutes[1].roundToInt(), measurable = hasHeartRate),
                PillarProgress(LongevityPillar.HIGH_INTENSITY, (zoneMinutes[3] + zoneMinutes[4]).roundToInt(), measurable = hasHeartRate),
                PillarProgress(LongevityPillar.MOBILITY, mobilityDays.size, days = mobilityDays, checkedDays = mobilityChecked),
                PillarProgress(LongevityPillar.BALANCE, balanceChecked.size, days = balanceChecked, checkedDays = balanceChecked)
            )
        )
    }
}
