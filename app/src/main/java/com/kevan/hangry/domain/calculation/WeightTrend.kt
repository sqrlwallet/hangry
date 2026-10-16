package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * The weight trend behind the scale's day-to-day noise (water, salt, a late meal).
 *
 * - Several weigh-ins on one day count as that day's average.
 * - The trend is an exponentially weighted moving average, as in The Hacker's Diet, Happy Scale
 *   and Libra: each weigh-in pulls the trend [SMOOTHING] (10%) of the way towards it - about a
 *   10-day window. A gap of several days lets the next weigh-in pull further, as if the missing
 *   days had read the same, so a week away doesn't freeze the line.
 * - The rate of change is the least-squares slope of daily weigh-ins over the last
 *   [RATE_WINDOW_DAYS] days - the same fit adaptive maintenance in [EnergyBalanceCalculator]
 *   turns into calories, so both always agree.
 */
object WeightTrend {
    const val SMOOTHING = 0.1
    const val RATE_WINDOW_DAYS = EnergyBalanceCalculator.ADAPTIVE_WINDOW_DAYS
    /** Fewer days, or a shorter spread, and a slope is mostly noise. */
    const val MIN_RATE_DAYS = 3
    const val MIN_RATE_SPAN_DAYS = 7

    data class TrendPoint(val date: LocalDate, val weighInKg: Double, val trendKg: Double)

    data class Summary(
        val points: List<TrendPoint>,
        /** Today's trend weight, or null with no weigh-ins. */
        val trendKg: Double?,
        /** Positive when gaining; null until there are enough weigh-ins to say. */
        val weeklyChangeKg: Double?
    )

    /** One averaged weight per day, oldest first. */
    fun dailyWeights(weights: List<WeightMeasurementEntity>, zone: ZoneId): List<Pair<LocalDate, Double>> =
        weights.groupBy({ it.timestamp.atZone(zone).toLocalDate() }, { it.weightKg })
            .mapValues { (_, kg) -> kg.average() }
            .toSortedMap()
            .map { (day, kg) -> day to kg }

    /** The smoothed trend at every weigh-in day. The first weigh-in starts the trend. */
    fun ewma(daily: List<Pair<LocalDate, Double>>, smoothing: Double = SMOOTHING): List<TrendPoint> {
        var trend: Double? = null
        var lastDay: LocalDate? = null
        return daily.sortedBy { it.first }.map { (day, kg) ->
            val previous = trend
            val next = if (previous == null || lastDay == null) kg else {
                val gapDays = ChronoUnit.DAYS.between(lastDay, day).coerceAtLeast(1)
                val pull = 1 - (1 - smoothing).pow(gapDays.toDouble())
                previous + pull * (kg - previous)
            }
            trend = next
            lastDay = day
            TrendPoint(day, kg, next)
        }
    }

    /** Least-squares change per day across the daily weights from [start] to [end], or null with too few. */
    fun slopePerDay(daily: List<Pair<LocalDate, Double>>, start: LocalDate, end: LocalDate): Double? {
        val window = daily.filter { (day, _) -> day in start..end }
        if (window.size < 2) return null
        return slope(window.map { (day, kg) -> ChronoUnit.DAYS.between(start, day).toDouble() to kg })
    }

    /** Least-squares slope of y over x. */
    fun slope(points: List<Pair<Double, Double>>): Double {
        val meanX = points.sumOf { it.first } / points.size
        val meanY = points.sumOf { it.second } / points.size
        val num = points.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val den = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        return if (den == 0.0) 0.0 else num / den
    }

    fun summarize(dailyWeights: List<Pair<LocalDate, Double>>, today: LocalDate): Summary {
        val daily = dailyWeights.filter { !it.first.isAfter(today) }
        val points = ewma(daily)
        val start = today.minusDays(RATE_WINDOW_DAYS.toLong())
        val recent = daily.filter { it.first in start..today }
        val span = if (recent.isEmpty()) 0L else ChronoUnit.DAYS.between(recent.first().first, recent.last().first)
        val weekly = if (recent.size >= MIN_RATE_DAYS && span >= MIN_RATE_SPAN_DAYS) {
            slopePerDay(recent, start, today)?.times(7)
        } else null
        return Summary(points = points, trendKg = points.lastOrNull()?.trendKg, weeklyChangeKg = weekly)
    }

    fun summarize(weights: List<WeightMeasurementEntity>, today: LocalDate, zone: ZoneId): Summary =
        summarize(dailyWeights(weights, zone), today)
}
