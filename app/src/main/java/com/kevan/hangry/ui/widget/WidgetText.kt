package com.kevan.hangry.ui.widget

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.domain.model.CycleStats
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure text/number logic behind the health widgets, kept apart from RemoteViews so it's testable. */
internal object WidgetText {

    const val HIDDEN_VALUE = "••"
    const val HIDDEN_SUBTITLE = "Hidden · tap to view"

    /** Today's value of a heart metric and how it compares with the 4 weeks before it. */
    data class HeartReading(val value: Double, val date: LocalDate, val baseline: Double?) {
        val delta: Double? get() = baseline?.let { value - it }
    }

    /**
     * The newest value from the last 3 days, against the average of the 28 days before it.
     * No baseline with fewer than 5 days of history, so one odd day isn't called "normal".
     */
    fun heartReading(
        summaries: List<DailyHealthSummaryEntity>,
        today: LocalDate,
        pick: (DailyHealthSummaryEntity) -> Double?
    ): HeartReading? {
        val current = summaries
            .filter { !it.date.isBefore(today.minusDays(2)) && !it.date.isAfter(today) && pick(it) != null }
            .maxByOrNull { it.date } ?: return null
        val value = pick(current) ?: return null
        val history = summaries
            .filter { it.date.isBefore(current.date) && !it.date.isBefore(current.date.minusDays(28)) }
            .mapNotNull(pick)
        return HeartReading(value, current.date, history.takeIf { it.size >= 5 }?.average())
    }

    /** "▲ 3" / "▼ 3", or "steady" within ±[steady]; null without a baseline. */
    fun deltaText(delta: Double?, steady: Double = 1.0): String? = when {
        delta == null -> null
        abs(delta) < steady -> "steady"
        delta > 0 -> "▲ ${abs(delta).roundToInt()}"
        else -> "▼ ${abs(delta).roundToInt()}"
    }

    /** Whether the change is good news, given which direction is better for the metric. */
    fun isImprovement(delta: Double?, higherIsBetter: Boolean, steady: Double = 1.0): Boolean? =
        if (delta == null || abs(delta) < steady) null else (delta > 0) == higherIsBetter

    fun daysAgoText(date: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(date, today)) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

    fun postureBadge(score: Int): String = when {
        score >= 85 -> "GREAT"
        score >= 70 -> "GOOD"
        score >= 55 -> "FAIR"
        else -> "NEEDS WORK"
    }

    /** Posture checks older than this get a nudge to take a new one. */
    const val POSTURE_RECHECK_DAYS = 14

    /** Headline, subtitle and badge for the cycle widget. */
    data class CycleText(val headline: String, val subtitle: String, val badge: String)

    fun cycleText(stats: CycleStats, today: LocalDate): CycleText {
        val day = stats.currentCycleDay
        if (stats.lastPeriodStart == null || day == null) {
            return CycleText("—", "Log your period in Health Records", "")
        }
        if (stats.inPeriodNow) {
            val avg = stats.averagePeriodDays?.let { " · usually ${it.roundToInt()} days" } ?: ""
            return CycleText("Period · day $day", "Cycle day $day$avg", "PERIOD")
        }
        val next = stats.predictedNextStart
            ?: return CycleText("Day $day", "Predictions start after two periods", "")
        val until = ChronoUnit.DAYS.between(today, next)
        val subtitle = when {
            until > 1 -> "Next period in ~$until days"
            until == 1L -> "Next period expected tomorrow"
            until == 0L -> "Next period expected today"
            else -> "Period expected ${-until} day${if (until == -1L) "" else "s"} ago"
        }
        return CycleText("Day $day", subtitle, if (until in 0..3) "SOON" else "")
    }
}
