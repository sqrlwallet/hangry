package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.StrainRecommendation
import com.kevan.hangry.domain.model.StrainResult

interface StrainCalculator {
    /**
     * Computes a bounded 0-21 day-strain value. Prefers a continuous heart-rate trace
     * (minutes spent in each cardio zone); falls back to a workout-only estimate when
     * there isn't enough continuous heart-rate data for the day.
     */
    fun calculateDayStrain(
        heartRateSamples: List<HeartRateSampleEntity>,
        workoutsToday: List<ExerciseSessionEntity>
    ): StrainResult

    /**
     * Suggests a target strain band for today, scaled off the user's own recent strain
     * history rather than a fixed population target.
     */
    fun recommendStrainTarget(
        recoveryState: RecoveryState,
        recentDailyStrain: List<Double>
    ): StrainRecommendation
}
