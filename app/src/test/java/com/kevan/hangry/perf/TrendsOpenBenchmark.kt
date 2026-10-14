package com.kevan.hangry.perf

import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.domain.calculation.WeightTrend
import com.kevan.hangry.ui.trends.TrendsDerived
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The per-day grouping Trends needs before it can draw, for the "All" timeframe with two years of
 * history. Before v1.43 this ran inside composition (on the main thread, in the first frame);
 * [inCompositionBefore] is that code as it was. Now [TrendsDerived.derive] runs in the ViewModel.
 */
class TrendsOpenBenchmark {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val start = LocalDate.of(2020, 1, 1)
    private val days = 730L

    private val scores = (0 until days).map { RecoveryScoreEntity(date = today.minusDays(it), score = 70, confidence = "HIGH", state = "PRIMED", supportiveAdvice = "") }
    private val summaries = (0 until days).map { DailyHealthSummaryEntity(date = today.minusDays(it), steps = 8000, sleepDurationMinutes = 420) }
    private val foodLogs = (0 until days).flatMap { d ->
        (0 until 4).map { m ->
            FoodLogEntity(date = today.minusDays(d), timestamp = today.minusDays(d).atTime(8 + m * 4, 0).atZone(zone).toInstant(), source = "AI", foodName = "Meal $m", calories = 600)
        }
    }
    private val workouts = (0 until days step 2).map { d ->
        val t = today.minusDays(d).atTime(18, 0).atZone(zone).toInstant()
        ExerciseSessionEntity(recordFingerprint = "w$d", exerciseType = "RUNNING", startTime = t, endTime = t.plusSeconds(3600), durationMinutes = 60)
    }
    private val weights = (0 until days).map { d ->
        WeightMeasurementEntity(recordFingerprint = "wt$d", timestamp = today.minusDays(d).atTime(7, 0).atZone(zone).toInstant(), weightKg = 80.0 - d * 0.01)
    }

    /** TrendsScreen's remember blocks before this change, verbatim apart from the locals. */
    private fun inCompositionBefore(): Int {
        val weightTrend = WeightTrend.summarize(weights, today, zone)
        val workoutsByDate = workouts.groupBy { it.startTime.atZone(zone).toLocalDate() }
        val foodLogsByDate = foodLogs.groupBy { it.date }
        val weightsByDate = WeightTrend.dailyWeights(weights, zone).toMap()
        val scoreByDate = scores.associateBy { it.date }
        val summaryByDate = summaries.associateBy { it.date }
        val chronologicalDates = (scores.map { it.date } + summaries.map { it.date } + foodLogs.map { it.date } + weights.map { it.timestamp.atZone(zone).toLocalDate() })
            .filter { !it.isBefore(start) && !it.isAfter(today) }
            .distinct()
            .sorted()
        val allHistoryDates = (scores.map { it.date } + summaries.map { it.date } + foodLogs.map { it.date } + workouts.map { it.startTime.atZone(zone).toLocalDate() })
            .filter { !it.isBefore(start) && !it.isAfter(today) }
            .distinct()
            .sortedDescending()
        return weightTrend.hashCode() + workoutsByDate.size + foodLogsByDate.size + weightsByDate.size + scoreByDate.size +
            summaryByDate.size + chronologicalDates.size + allHistoryDates.size
    }

    @Test
    fun allTimeframe_grouping() {
        assumeTrue(PerfBench.enabled)
        PerfBench.measure("Trends 'All': grouping as it was (in composition)") { inCompositionBefore() }
        PerfBench.measure("Trends 'All': TrendsDerived.derive (in the ViewModel)") {
            TrendsDerived.derive(scores, summaries, foodLogs, workouts, weights, start, today, zone)
        }
    }
}
