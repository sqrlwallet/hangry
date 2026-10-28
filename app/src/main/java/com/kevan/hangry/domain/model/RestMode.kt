package com.kevan.hangry.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Why the user is resting; shown on Today and told to Dash. */
enum class RestReason(val label: String) {
    UNWELL("Feeling unwell"),
    INJURY("Injury"),
    RECOVERY("Just need rest");

    companion object {
        fun fromName(name: String?): RestReason = entries.firstOrNull { it.name == name } ?: UNWELL
    }
}

/**
 * A stretch of rest days, like Oura's Rest Mode: from [start] to [end] (inclusive), or ongoing
 * while [end] is null. While resting, streaks are paused and the strain target is gentle.
 */
data class RestPeriod(val start: LocalDate, val end: LocalDate?, val reason: RestReason) {
    val active: Boolean get() = end == null

    fun covers(day: LocalDate): Boolean = !day.isBefore(start) && (end == null || !day.isAfter(end))

    /** Day 1 is the first rest day. */
    fun dayNumber(today: LocalDate): Int = ChronoUnit.DAYS.between(start, today).toInt() + 1
}

data class RestModeState(val periods: List<RestPeriod> = emptyList()) {

    /** The ongoing rest period, if rest mode is on. */
    val active: RestPeriod? get() = periods.firstOrNull { it.active }

    fun periodOn(day: LocalDate): RestPeriod? = periods.firstOrNull { it.covers(day) }

    fun isRestDay(day: LocalDate): Boolean = periodOn(day) != null

    /** Every rest day in [from]..[to]; an ongoing period runs through [to]. */
    fun restDays(from: LocalDate, to: LocalDate): Set<LocalDate> = periods.flatMap { p ->
        val first = maxOf(p.start, from)
        val last = minOf(p.end ?: to, to)
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }.toSet()

    /** Turns rest mode on from [day]. Already resting: only the reason changes. */
    fun started(day: LocalDate, reason: RestReason): RestModeState {
        active?.let { current -> return RestModeState(periods.map { if (it === current) it.copy(reason = reason) else it }) }
        return RestModeState(periods + RestPeriod(day, null, reason))
    }

    /**
     * Ends rest mode with [day] as the last rest day. One switched on and off the same day was a
     * slip, so it's forgotten rather than kept as a one-day rest.
     */
    fun ended(day: LocalDate): RestModeState {
        val current = active ?: return this
        val kept = periods.filterNot { it === current }
        return if (!day.isAfter(current.start)) RestModeState(kept) else RestModeState(kept + current.copy(end = day))
    }

    /** Stored form: "2026-10-01|2026-10-04|UNWELL;2026-10-20||INJURY". */
    fun encode(): String = periods.joinToString(";") { "${it.start}|${it.end ?: ""}|${it.reason.name}" }

    companion object {
        /** Ongoing rest suggests ending once the body is back to normal, after this many days. */
        const val SUGGEST_END_AFTER_DAYS = 2

        fun decode(text: String?): RestModeState = RestModeState(
            text.orEmpty().split(";").mapNotNull { part ->
                val fields = part.split("|").takeIf { it.size == 3 } ?: return@mapNotNull null
                val start = runCatching { LocalDate.parse(fields[0]) }.getOrNull() ?: return@mapNotNull null
                val end = fields[1].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                RestPeriod(start, end, RestReason.fromName(fields[2]))
            }.sortedBy { it.start }
        )
    }
}
