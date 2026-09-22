package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import com.kevan.hangry.domain.model.StrainRecommendation
import com.kevan.hangry.domain.model.StrainResult
import com.kevan.hangry.domain.model.StrainSource
import java.time.Duration
import kotlin.math.exp

/**
 * Day-strain uses the same fixed heart-rate zone boundaries as [com.kevan.hangry.domain.model.HeartRateZoneDistribution]
 * (114/133/152/171 bpm, see HeartRateDao's zone queries), accumulated through a saturating
 * curve so strain rises quickly at first and flattens near the top of the 0-21 scale -
 * see CALCULATIONS.md §6 for the exact formula and worked examples.
 */
class HangryStrainCalculator(
    private val trainingLoadCalculator: TrainingLoadCalculator
) : StrainCalculator {

    override fun calculateDayStrain(
        heartRateSamples: List<HeartRateSampleEntity>,
        workoutsToday: List<ExerciseSessionEntity>
    ): StrainResult {
        if (heartRateSamples.size >= MIN_SAMPLES_FOR_ZONE_BASED) {
            val sorted = heartRateSamples.sortedBy { it.timestamp }
            val zoneMinutes = DoubleArray(5)
            for (i in 0 until sorted.size - 1) {
                val gapMinutes = Duration.between(sorted[i].timestamp, sorted[i + 1].timestamp).toMillis() / 60000.0
                if (gapMinutes <= 0.0 || gapMinutes > MAX_GAP_MINUTES) continue
                zoneMinutes[zoneIndexForBpm(sorted[i].bpm)] += gapMinutes
            }
            val points = zoneMinutes.indices.sumOf { zoneMinutes[it] * ZONE_STRAIN_WEIGHTS[it] }
            val strain = strainFromPoints(points)
            val confidence = if (sorted.size >= HIGH_CONFIDENCE_SAMPLE_COUNT) ScoreConfidence.HIGH else ScoreConfidence.MEDIUM
            return StrainResult(
                dayStrain = strain,
                confidence = confidence,
                source = StrainSource.HEART_RATE_ZONES,
                supportiveNote = supportiveNoteFor(strain)
            )
        }

        if (workoutsToday.isEmpty()) {
            return StrainResult(
                dayStrain = 0.0,
                confidence = ScoreConfidence.LOW,
                source = StrainSource.NONE,
                supportiveNote = "No cardiovascular activity recorded yet today."
            )
        }

        // Fallback: no continuous heart-rate trace for the day, estimate from logged workouts.
        val sessionLoad = workoutsToday.sumOf { trainingLoadCalculator.estimateSessionLoad(it) }
        val strain = strainFromPoints(sessionLoad * WORKOUT_LOAD_TO_POINTS)
        return StrainResult(
            dayStrain = strain,
            confidence = ScoreConfidence.LOW,
            source = StrainSource.WORKOUT_ESTIMATE,
            supportiveNote = "Estimated from logged workouts. Connect a wearable for continuous heart-rate-based strain."
        )
    }

    override fun recommendStrainTarget(
        recoveryState: RecoveryState,
        recentDailyStrain: List<Double>
    ): StrainRecommendation {
        val rollingAverage = if (recentDailyStrain.isNotEmpty()) recentDailyStrain.average() else DEFAULT_ROLLING_AVERAGE

        val (lowFraction, highFraction, guidance) = when (recoveryState) {
            RecoveryState.PRIMED -> Triple(
                1.0, 1.3,
                "Your markers are elevated today - a good day to push toward the top of your recent range."
            )
            RecoveryState.BALANCED -> Triple(
                0.75, 1.05,
                "Steady energy today. Aim for a strain in line with your recent routine."
            )
            RecoveryState.REBUILD -> Triple(
                0.3, 0.65,
                "Prioritize restorative movement today and stay well under your recent strain range."
            )
            RecoveryState.BUILDING_BASELINE -> Triple(
                0.5, 1.0,
                "Still calibrating your baseline - moderate activity today is a safe default."
            )
        }

        val low = (rollingAverage * lowFraction).coerceIn(0.0, MAX_STRAIN)
        val high = (rollingAverage * highFraction).coerceIn(low, MAX_STRAIN)
        return StrainRecommendation(targetLow = low, targetHigh = high, guidance = guidance)
    }

    private fun strainFromPoints(points: Double): Double {
        if (points <= 0.0) return 0.0
        return (MAX_STRAIN * (1.0 - exp(-STRAIN_RATE * points))).coerceIn(0.0, MAX_STRAIN)
    }

    private fun zoneIndexForBpm(bpm: Double): Int = when {
        bpm < 114 -> 0
        bpm < 133 -> 1
        bpm < 152 -> 2
        bpm < 171 -> 3
        else -> 4
    }

    private fun supportiveNoteFor(strain: Double): String = when {
        strain >= 18.0 -> "A demanding day physically. Prioritize sleep tonight to recover well."
        strain >= 10.0 -> "Solid, balanced effort today."
        strain > 0.0 -> "Light activity today - good for active recovery."
        else -> "Minimal cardiovascular strain recorded today."
    }

    companion object {
        const val MAX_STRAIN = 21.0
        private const val STRAIN_RATE = 0.04
        private const val MIN_SAMPLES_FOR_ZONE_BASED = 20
        private const val HIGH_CONFIDENCE_SAMPLE_COUNT = 200
        private const val MAX_GAP_MINUTES = 10.0
        private const val WORKOUT_LOAD_TO_POINTS = 0.4
        private const val DEFAULT_ROLLING_AVERAGE = 10.0

        // Strain points accrued per minute spent in each zone (1-5), see CALCULATIONS.md §6.
        val ZONE_STRAIN_WEIGHTS = doubleArrayOf(0.05, 0.20, 0.45, 0.80, 1.20)
    }
}
