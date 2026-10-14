package com.kevan.hangry.ui.trends

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.WeightTrend
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trends' history, grouped by day: worked out in the ViewModel off the main thread, so opening
 * Trends (or picking "All") doesn't group years of rows while the first frame is drawn.
 */
data class TrendsDerived(
    val queryStartDate: LocalDate,
    val today: LocalDate,
    val weightTrend: WeightTrend.Summary,
    val workoutsByDate: Map<LocalDate, List<ExerciseSessionEntity>>,
    val foodLogsByDate: Map<LocalDate, List<FoodLogEntity>>,
    /** A day's weigh-ins averaged. */
    val weightsByDate: Map<LocalDate, Double>,
    val scoreByDate: Map<LocalDate, RecoveryScoreEntity>,
    val summaryByDate: Map<LocalDate, DailyHealthSummaryEntity>,
    /** Every day in the timeframe with data to chart, oldest first. */
    val chronologicalDates: List<LocalDate>,
    /** Every day in the timeframe with anything in its history row, newest first. */
    val historyDates: List<LocalDate>
) {
    companion object {
        fun empty(today: LocalDate, zone: ZoneId) = derive(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), today, today, zone)

        fun derive(
            scores: List<RecoveryScoreEntity>,
            summaries: List<DailyHealthSummaryEntity>,
            foodLogs: List<FoodLogEntity>,
            workouts: List<ExerciseSessionEntity>,
            weights: List<WeightMeasurementEntity>,
            queryStartDate: LocalDate,
            today: LocalDate,
            zone: ZoneId
        ): TrendsDerived {
            fun inRange(date: LocalDate) = !date.isBefore(queryStartDate) && !date.isAfter(today)
            val workoutsByDate = workouts.groupBy { it.startTime.atZone(zone).toLocalDate() }
            val weightDates = weights.map { it.timestamp.atZone(zone).toLocalDate() }
            val chronological = HashSet<LocalDate>()
            val history = HashSet<LocalDate>()
            for (s in scores) { chronological += s.date; history += s.date }
            for (s in summaries) { chronological += s.date; history += s.date }
            for (f in foodLogs) { chronological += f.date; history += f.date }
            chronological += weightDates
            history += workoutsByDate.keys
            return TrendsDerived(
                queryStartDate = queryStartDate,
                today = today,
                weightTrend = WeightTrend.summarize(weights, today, zone),
                workoutsByDate = workoutsByDate,
                foodLogsByDate = foodLogs.groupBy { it.date },
                weightsByDate = WeightTrend.dailyWeights(weights, zone).toMap(),
                scoreByDate = scores.associateBy { it.date },
                summaryByDate = summaries.associateBy { it.date },
                chronologicalDates = chronological.filter(::inRange).sorted(),
                historyDates = history.filter(::inRange).sortedDescending()
            )
        }
    }
}
