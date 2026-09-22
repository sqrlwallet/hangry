package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.TrainingLoadAnalysis

interface TrainingLoadCalculator {
    /**
     * @param recentWorkoutsHistory sessions from exactly the trailing 7 calendar days before
     * today (excluding today) - not "the last N sessions". The 7-day average divides by a
     * fixed 7, so an unbounded or differently-sized window will silently skew the result.
     */
    fun calculateDailyLoad(
        workoutsToday: List<ExerciseSessionEntity>,
        recentWorkoutsHistory: List<ExerciseSessionEntity>
    ): TrainingLoadAnalysis

    fun estimateSessionLoad(session: ExerciseSessionEntity): Double
}
