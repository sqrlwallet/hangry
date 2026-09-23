package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.SupplementTimes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the streaks shown on Today from data the app already stores: daily summaries (steps,
 * sleep), the food log and supplement doses. Only habits the user actually has data for are
 * returned - no step data means no step streak card.
 */
class StreaksLoader(private val database: HangryDatabase) {

    suspend fun load(today: LocalDate, stepGoal: Long, sleepGoalMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): List<Streak> {
        val start = today.minusDays(HISTORY_DAYS)
        val summaries = database.dailyHealthSummaryDao().getSummariesBetweenList(start, today)
        val streaks = mutableListOf<Streak>()

        if (summaries.any { it.steps != null }) {
            val days = summaries.filter { (it.steps ?: 0L) >= stepGoal }.map { it.date }.toSet()
            streaks += StreakCalculator.streak(StreakType.STEPS, days, today)
        }

        val meals = database.foodLogDao().getBetweenList(start, today)
        if (meals.isNotEmpty()) {
            streaks += StreakCalculator.streak(StreakType.MEALS, meals.map { it.date }.toSet(), today)
        }

        if (summaries.any { it.sleepDurationMinutes != null }) {
            val target = sleepGoalMinutes - StreakCalculator.SLEEP_GRACE_MINUTES
            val days = summaries.filter { (it.sleepDurationMinutes ?: 0) >= target }.map { it.date }.toSet()
            streaks += StreakCalculator.streak(StreakType.SLEEP, days, today)
        }

        val supplements = database.supplementDao().getAll().filter { it.active && SupplementTimes.parse(it.times).isNotEmpty() }
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
            streaks += StreakCalculator.streak(StreakType.SUPPLEMENTS, days, today)
        }
        return streaks
    }

    private companion object {
        /** Enough for a year-long streak plus some headroom. */
        const val HISTORY_DAYS = 400L
    }
}
