package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.domain.calculation.BodyAgeCalculator
import com.kevan.hangry.domain.calculation.BodyAgeInputs
import com.kevan.hangry.domain.calculation.BodyAgeResult
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.WorkoutCategory
import com.kevan.hangry.domain.model.WorkoutType
import java.time.LocalDate
import java.time.ZoneId

/** Body Age now, and 30 days ago so the screen can say which way it's heading. */
data class BodyAgeSnapshot(
    val current: BodyAgeResult?,
    val previous: BodyAgeResult?,
    /** Why there's no result yet, when [current] is null. */
    val needs: List<String>,
    /** No date of birth or age yet - the screen asks for the birthday. */
    val needsBirthday: Boolean = false,
    /** True when the age comes from a date of birth (exact), not a typed-in whole number. */
    val exactAge: Boolean = false
)

/** Gathers 30 days of stored data into [BodyAgeInputs] and runs [BodyAgeCalculator]. */
class BodyAgeLoader(private val database: HangryDatabase) {

    suspend fun load(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BodyAgeSnapshot {
        val askForBirthday = BodyAgeSnapshot(null, null, listOf("Your date of birth"), needsBirthday = true)
        val profile = database.userProfileDao().getProfileSync() ?: return askForBirthday
        // Exact age from the date of birth (e.g. 34.7); a typed-in whole age is the fallback.
        val dob = profile.dateOfBirth
        val age = dob?.let { AgeMath.exact(it, today) } ?: profile.age?.toDouble() ?: return askForBirthday
        val sex = profile.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
        val current = inputsFor(today, age, sex, profile.heightCm, zone)?.let(BodyAgeCalculator::calculate)
        // A month ago you were a month younger too.
        val previous = inputsFor(today.minusDays(WINDOW_DAYS), age - WINDOW_DAYS / 365.25, sex, profile.heightCm, zone)?.let(BodyAgeCalculator::calculate)
        val needs = if (current == null) {
            listOf("At least ${BodyAgeCalculator.MIN_DAYS} days of data from Health Connect in the last month",
                "At least ${BodyAgeCalculator.MIN_FACTORS} of: steps, sleep, resting heart rate, HRV, VO₂ max, workouts, body composition")
        } else emptyList()
        return BodyAgeSnapshot(current, previous, needs, exactAge = dob != null)
    }

    private suspend fun inputsFor(end: LocalDate, age: Double, sex: BiologicalSex?, profileHeightCm: Double?, zone: ZoneId): BodyAgeInputs? {
        val start = end.minusDays(WINDOW_DAYS - 1)
        val summaries = database.dailyHealthSummaryDao().getSummariesBetweenList(start, end)
        val daysWithData = summaries.count { it.steps != null || it.sleepDurationMinutes != null || it.restingHeartRate != null }
        if (daysWithData == 0) return null

        fun avg(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.average()
        val weeks = WINDOW_DAYS / 7.0

        // Zero workouts only means "no exercise" if this person's apps record workouts at all.
        val recordsWorkouts = database.exerciseSessionDao().getCount() > 0
        val workouts = database.exerciseSessionDao().getSessionsBetweenList(
            start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant()
        )
        val exerciseMinutes = workouts
            .filter { WorkoutType.fromId(it.exerciseType).category != WorkoutCategory.MIND_BODY }
            .sumOf { it.durationMinutes } / weeks
        val strengthSessions = workouts.count { WorkoutType.fromId(it.exerciseType).category == WorkoutCategory.STRENGTH } / weeks

        val bodyFat = database.bodyFatScanDao().getLatestScanSync()
            ?.takeIf { !it.date.isBefore(end.minusDays(180)) && !it.date.isAfter(end) }?.bodyFatPercentage
            ?: summaries.lastOrNull { it.bodyFatPercentage != null }?.bodyFatPercentage
        val heightCm = database.heightDao().getLatestHeightSync()?.heightCm ?: profileHeightCm
        val weightKg = database.weightDao().getLatestWeightSync()?.weightKg
        val bmi = if (heightCm != null && weightKg != null && heightCm > 0) weightKg / ((heightCm / 100) * (heightCm / 100)) else null

        return BodyAgeInputs(
            age = age,
            sex = sex,
            daysWithData = daysWithData,
            vo2Max = summaries.lastOrNull { it.vo2Max != null }?.vo2Max,
            restingHeartRate = avg(summaries.mapNotNull { it.restingHeartRate }),
            hrvRmssd = avg(summaries.mapNotNull { it.hrvRmssd }),
            averageSteps = avg(summaries.mapNotNull { it.steps?.toDouble() }),
            weeklyExerciseMinutes = if (recordsWorkouts) exerciseMinutes else null,
            weeklyStrengthSessions = if (recordsWorkouts) strengthSessions else null,
            averageSleepMinutes = avg(summaries.mapNotNull { it.sleepDurationMinutes?.toDouble() }),
            sleepConsistency = avg(summaries.mapNotNull { it.sleepConsistencyScore }),
            bodyFatPercent = bodyFat,
            bmi = bmi
        )
    }

    private companion object {
        const val WINDOW_DAYS = 30L
    }
}
