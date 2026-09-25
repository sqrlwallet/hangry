package com.kevan.hangry.domain.calculation

import java.time.LocalDate

/** The habits Hangry keeps streaks for. */
enum class StreakType(val label: String) {
    STEPS("Step goal"),
    MEALS("Meals logged"),
    SLEEP("Sleep goal"),
    SUPPLEMENTS("All supplements"),
    FASTING("Fasting goal")
}

/**
 * A run of consecutive days a habit was met. Today can only extend a streak, never break it:
 * until today is done, the streak still counts through yesterday ([doneToday] is false).
 */
data class Streak(
    val type: StreakType,
    val current: Int,
    val doneToday: Boolean,
    /** First day of the current run; null when there's no run. Identifies the run for milestones. */
    val startedOn: LocalDate?,
    val best: Int
)

object StreakCalculator {

    /** Streak lengths Dash celebrates. */
    val MILESTONES = listOf(7, 14, 30, 60, 100, 180, 365)

    fun streak(type: StreakType, achievedDays: Set<LocalDate>, today: LocalDate): Streak {
        val doneToday = today in achievedDays
        var day = if (doneToday) today else today.minusDays(1)
        var count = 0
        while (day in achievedDays) {
            count++
            day = day.minusDays(1)
        }
        return Streak(
            type = type,
            current = count,
            doneToday = doneToday,
            startedOn = if (count > 0) day.plusDays(1) else null,
            best = maxOf(count, longestRun(achievedDays))
        )
    }

    /** The longest run of consecutive days anywhere in [days]. */
    fun longestRun(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue // only count from the start of each run
            var length = 1
            while (day.plusDays(length.toLong()) in days) length++
            best = maxOf(best, length)
        }
        return best
    }

    /** Milestones this streak has reached, lowest first (e.g. 35 days -> 7, 14, 30). */
    fun milestonesReached(streak: Streak): List<Int> = MILESTONES.filter { it <= streak.current }

    /** A supplement's daily dose times ("HH:mm"), expected from the day it was added. */
    data class DoseSchedule(val supplementId: Long, val since: LocalDate, val times: List<String>)

    /**
     * Days in [start]..[end] where every dose scheduled that day was taken. Only supplements that
     * existed on a day are expected then, so adding a new one doesn't break past days; a day with
     * nothing scheduled doesn't count.
     */
    fun fullyTakenDays(
        schedules: List<DoseSchedule>,
        taken: Set<Triple<Long, LocalDate, String>>,
        start: LocalDate,
        end: LocalDate
    ): Set<LocalDate> = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .filter { day ->
            val due = schedules.filter { !it.since.isAfter(day) }.flatMap { s -> s.times.map { Triple(s.supplementId, day, it) } }
            due.isNotEmpty() && due.all { it in taken }
        }
        .toSet()

    /**
     * Sleep counts toward the streak with a little grace, so 7h 50m against an 8h goal isn't a
     * broken streak.
     */
    const val SLEEP_GRACE_MINUTES = 15
}
