package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.SupplementTimes
import com.kevan.hangry.domain.repository.FastingRepository
import com.kevan.hangry.domain.repository.RestModeRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the streaks shown on Today from data the app already stores: daily summaries (steps,
 * sleep), the food log, supplement doses and fasts. Only habits the user actually has data for are
 * returned - no step data means no step streak card. Rest days (rest mode) never break a streak.
 */
class StreaksLoader(
    private val database: HangryDatabase,
    /** Only while fasting is turned on. Optional for tests. */
    private val fastingRepository: FastingRepository? = null,
    /** Optional for tests. */
    private val restModeRepository: RestModeRepository? = null
) {

    suspend fun load(today: LocalDate, stepGoal: Long, sleepGoalMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): List<Streak> {
        val start = today.minusDays(HISTORY_DAYS)
        val summaries = database.dailyHealthSummaryDao().getHabitDaysBetween(start, today)
        val streaks = mutableListOf<Streak>()
        val restDays = restModeRepository?.state?.value?.restDays(start, today).orEmpty()

        if (summaries.any { it.steps != null }) {
            val days = summaries.filter { (it.steps ?: 0L) >= stepGoal }.map { it.date }.toSet()
            streaks += StreakCalculator.streak(StreakType.STEPS, days, today, restDays)
        }

        val mealDays = database.foodLogDao().getLoggedDatesBetween(start, today)
        if (mealDays.isNotEmpty()) {
            streaks += StreakCalculator.streak(StreakType.MEALS, mealDays.toSet(), today, restDays)
        }

        if (summaries.any { it.sleepDurationMinutes != null }) {
            val target = sleepGoalMinutes - StreakCalculator.SLEEP_GRACE_MINUTES
            val days = summaries.filter { (it.sleepDurationMinutes ?: 0) >= target }.map { it.date }.toSet()
            streaks += StreakCalculator.streak(StreakType.SLEEP, days, today, restDays)
        }

        val supplements = database.supplementDao().getAll().filter { it.active && it.tracked && SupplementTimes.parse(it.times).isNotEmpty() }
        if (supplements.isNotEmpty()) {
            val taken = database.supplementDao().getIntakesBetween(start, today)
                .map { Triple(it.supplementId, it.date, it.scheduledTime) }
                .toSet()
            val schedules = supplements.map { s ->
                StreakCalculator.DoseSchedule(
                    supplementId = s.id,
                    since = s.createdAt.atZone(zone).toLocalDate(),
                    times = SupplementTimes.parse(s.times).map(SupplementTimes::key)
                )
            }
            val days = StreakCalculator.fullyTakenDays(schedules, taken, start, today)
            streaks += StreakCalculator.streak(StreakType.SUPPLEMENTS, days, today, restDays)
        }

        fastingRepository?.current()?.takeIf { it.enabled && (it.active != null || it.history.isNotEmpty()) }?.let { fasting ->
            val fasts = listOfNotNull(fasting.active) + fasting.history
            streaks += StreakCalculator.streak(StreakType.FASTING, FastingMath.goalDays(fasts, zone), today, restDays)
        }
        return streaks
    }

    private companion object {
        /** Enough for a year-long streak plus some headroom. */
        const val HISTORY_DAYS = 400L
    }
}
