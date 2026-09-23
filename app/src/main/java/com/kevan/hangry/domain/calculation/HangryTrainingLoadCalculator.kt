package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.TrainingLoadAnalysis
import com.kevan.hangry.domain.model.WorkoutType
import kotlin.math.roundToInt

class HangryTrainingLoadCalculator : TrainingLoadCalculator {

    override fun estimateSessionLoad(session: ExerciseSessionEntity): Double {
        // Harder families of exercise (running, HIIT) count for more per minute than easy ones.
        val multiplier = WorkoutType.fromId(session.exerciseType).category.intensity
        val durationScore = session.durationMinutes.toDouble()
        // Everything burned during the workout - many sources only report a total, not "active".
        val calorieBonus = ActiveActivityCalculator.workoutCalories(session, bmr = null)?.let { it / 100.0 } ?: 0.0
        return (durationScore * 0.8 + calorieBonus * 5.0) * multiplier
    }

    override fun calculateDailyLoad(
        workoutsToday: List<ExerciseSessionEntity>,
        recentWorkoutsHistory: List<ExerciseSessionEntity>
    ): TrainingLoadAnalysis {
        val dailyLoad = workoutsToday.sumOf { estimateSessionLoad(it) }

        // recentWorkoutsHistory must already be scoped by the caller to exactly the trailing
        // 7 calendar days before today (not "last N sessions") - dividing by a fixed 7.0 is
        // only correct because the window itself is fixed, not because we truncated a list.
        val sevenDayAvg = if (recentWorkoutsHistory.isNotEmpty()) {
            (recentWorkoutsHistory.sumOf { estimateSessionLoad(it) } / 7.0)
        } else {
            dailyLoad
        }

        val ratio = if (sevenDayAvg > 0) dailyLoad / sevenDayAvg else 1.0

        val supportiveNote = when {
            workoutsToday.isEmpty() -> "Rest day — restorative recovery"
            ratio >= 1.3 -> "Higher training effort today. Plan for a solid night's sleep to recover well."
            ratio in 0.8..1.29 -> "Balanced training volume aligned with your recent routine."
            else -> "Light activity today — good active recovery."
        }

        return TrainingLoadAnalysis(
            dailyLoad = (dailyLoad * 10.0).roundToInt() / 10.0,
            workoutCount = workoutsToday.size,
            sevenDayAverageLoad = (sevenDayAvg * 10.0).roundToInt() / 10.0,
            acuteToChronicRatio = (ratio * 100.0).roundToInt() / 100.0,
            supportiveNote = supportiveNote
        )
    }
}
