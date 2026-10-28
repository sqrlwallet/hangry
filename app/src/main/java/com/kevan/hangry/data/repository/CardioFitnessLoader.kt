package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.CardioFitness
import com.kevan.hangry.domain.calculation.HeartRateRecovery
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.domain.model.BiologicalSex
import java.time.LocalDate
import java.time.ZoneId

/** One workout's heart-rate recovery. */
data class WorkoutRecovery(val workout: ExerciseSessionEntity, val recovery: HeartRateRecovery.Result)

/** VO2 max against your age and sex, its trend, and recent workouts' heart-rate recovery. */
data class CardioFitnessSnapshot(
    val vo2Max: Double?,
    /** Null without VO2 max or a birthday. */
    val level: CardioFitness.Level?,
    val ranges: CardioFitness.Ranges?,
    /** VO2 max now minus [CardioFitness.TREND_DAYS] ago. */
    val change: Double?,
    /** Newest first, from the last [CardioFitnessLoader.RECOVERY_DAYS] days. */
    val recoveries: List<WorkoutRecovery>,
    val needsBirthday: Boolean
) {
    /** Average 1-minute drop over the workouts that have one. */
    val averageOneMinuteDrop: Double? get() = recoveries.mapNotNull { it.recovery.oneMinuteDrop }.takeIf { it.isNotEmpty() }?.average()
}

/** Who the levels are judged for: exact age where there's a birthday, and sex. */
data class CardioPerson(val age: Double?, val sex: BiologicalSex?)

class CardioFitnessLoader(private val database: HangryDatabase) {

    suspend fun person(today: LocalDate): CardioPerson {
        val profile = database.userProfileDao().getProfileSync()
        val age = profile?.dateOfBirth?.let { AgeMath.exact(it, today) } ?: profile?.age?.toDouble()
        val sex = profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
        return CardioPerson(age, sex)
    }

    /** Each day's VO2 max (the latest reading carried forward), far enough back for the trend two weeks ago too. */
    suspend fun vo2Readings(today: LocalDate): Map<LocalDate, Double> =
        database.dailyHealthSummaryDao()
            .getSummariesBetweenList(today.minusDays(CardioFitness.TREND_DAYS + CardioFitness.SUSTAINED_DAYS + 7), today)
            .mapNotNull { s -> s.vo2Max?.takeIf { it > 0 }?.let { s.date to it } }
            .toMap()

    suspend fun load(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): CardioFitnessSnapshot {
        val person = person(today)
        val readings = vo2Readings(today)
        val vo2 = CardioFitness.valueOn(readings, today)
        val zones = zones(today, person)

        // Only workouts that reached the effort heart-rate recovery needs are worth a look.
        val hardEnough = zones.restingHr + HeartRateRecovery.MIN_END_RESERVE_FRACTION * (zones.maxHr - zones.restingHr)
        val workouts = database.exerciseSessionDao().getSessionsBetweenList(
            today.minusDays(RECOVERY_DAYS).atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant()
        ).filter { (it.maxHeartRate ?: Double.MAX_VALUE) >= hardEnough }
        val dao = database.heartRateDao()
        val recoveries = workouts.sortedByDescending { it.endTime }.mapNotNull { w ->
            val samples = dao.getSamplesBetweenList(w.endTime - HeartRateRecovery.WINDOW_BEFORE_END, w.endTime + HeartRateRecovery.WINDOW_AFTER_END)
            HeartRateRecovery.calculate(w.endTime, samples.map { it.timestamp to it.bpm }, zones)?.let { WorkoutRecovery(w, it) }
        }

        return CardioFitnessSnapshot(
            vo2Max = vo2,
            level = if (vo2 != null && person.age != null) CardioFitness.level(vo2, person.age, person.sex) else null,
            ranges = person.age?.let { CardioFitness.ranges(it, person.sex) },
            change = CardioFitness.change(readings, today),
            recoveries = recoveries,
            needsBirthday = person.age == null
        )
    }

    /** The same zones as Today: max from Settings or age, usual resting heart rate. */
    private suspend fun zones(today: LocalDate, person: CardioPerson): HeartRateZones {
        val profile = database.userProfileDao().getProfileSync()
        val resting = database.restingHeartRateDao().getAverageBetween(today.minusDays(30), today)
        return HeartRateZones.forUser(person.age?.toInt(), profile?.maxHeartRate, resting)
    }

    companion object {
        const val RECOVERY_DAYS = 90L
    }
}
