package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import com.kevan.hangry.domain.model.StrainRecommendation
import com.kevan.hangry.domain.model.StrainResult
import com.kevan.hangry.domain.model.StrainSource
import java.time.Duration
import kotlin.math.exp
import kotlin.math.max

/**
 * Day strain on a 0-21 scale from time in personal heart-rate zones (see [HeartRateZones]).
 * Each minute earns its zone number in points (Edwards TRIMP: zone 1 = 1 ... zone 5 = 5), and
 * light activity (40-50% of heart-rate reserve, e.g. a brisk walk) half a point; resting and
 * sleeping earn nothing. Points go
 * through a saturating curve, so an easy hour lands around 8 and a hard hour around 15.
 */
class HangryStrainCalculator(
    private val trainingLoadCalculator: TrainingLoadCalculator
) : StrainCalculator {

    override fun calculateDayStrain(
        heartRateSamples: List<HeartRateSampleEntity>,
        workoutsToday: List<ExerciseSessionEntity>,
        zones: HeartRateZones,
        sleepSessions: List<SleepSessionEntity>
    ): StrainResult {
        val awake = heartRateSamples
            .filter { s -> sleepSessions.none { !s.timestamp.isBefore(it.startTime) && s.timestamp.isBefore(it.endTime) } }
            .sortedBy { it.timestamp }

        if (awake.size >= MIN_SAMPLES_FOR_ZONE_BASED) {
            val zoneMinutes = DoubleArray(5)
            var lightMinutes = 0.0
            for (i in 0 until awake.size - 1) {
                val gapMinutes = Duration.between(awake[i].timestamp, awake[i + 1].timestamp).toMillis() / 60000.0
                if (gapMinutes <= 0.0 || gapMinutes > MAX_GAP_MINUTES) continue
                val zone = zones.zoneIndex(awake[i].bpm)
                if (zone >= 0) zoneMinutes[zone] += gapMinutes
                else if (awake[i].bpm >= zones.lightActivityLowerBound) lightMinutes += gapMinutes
            }
            var points = zoneMinutes.indices.sumOf { zoneMinutes[it] * ZONE_STRAIN_WEIGHTS[it] } + lightMinutes * LIGHT_ACTIVITY_WEIGHT
            // Workouts recorded with the watch off (phone GPS, gym app) still count.
            val untracked = workoutsToday.filter { w ->
                val covered = awake.count { !it.timestamp.isBefore(w.startTime) && !it.timestamp.isAfter(w.endTime) }
                covered < max(MIN_WORKOUT_SAMPLES, w.durationMinutes / 5)
            }
            points += untracked.sumOf { trainingLoadCalculator.estimateSessionLoad(it) } * WORKOUT_LOAD_TO_POINTS
            val strain = strainFromPoints(points)
            val confidence = if (awake.size >= HIGH_CONFIDENCE_SAMPLE_COUNT) ScoreConfidence.HIGH else ScoreConfidence.MEDIUM
            return StrainResult(
                dayStrain = strain,
                confidence = confidence,
                source = StrainSource.HEART_RATE_ZONES,
                supportiveNote = supportiveNoteFor(strain),
                zoneMinutes = zoneMinutes.toList()
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
        return (MAX_STRAIN * (1.0 - exp(-points / POINTS_SCALE))).coerceIn(0.0, MAX_STRAIN)
    }

    private fun supportiveNoteFor(strain: Double): String = when {
        strain >= 18.0 -> "A demanding day physically. Prioritize sleep tonight to recover well."
        strain >= 10.0 -> "Solid, balanced effort today."
        strain > 0.0 -> "Light activity today - good for active recovery."
        else -> "Minimal cardiovascular strain recorded today."
    }

    companion object {
        const val MAX_STRAIN = 21.0
        /** Points for ~63% of the scale: 150 is about an hour at zone 2-3. */
        private const val POINTS_SCALE = 150.0
        private const val MIN_SAMPLES_FOR_ZONE_BASED = 20
        private const val HIGH_CONFIDENCE_SAMPLE_COUNT = 200
        private const val MAX_GAP_MINUTES = 10.0
        private const val MIN_WORKOUT_SAMPLES = 5
        /** Workout load (see TrainingLoadCalculator) to zone points: a 45-min run is ~200 points. */
        private const val WORKOUT_LOAD_TO_POINTS = 2.4
        private const val DEFAULT_ROLLING_AVERAGE = 10.0

        /** Edwards TRIMP: points per minute in zones 1-5. */
        val ZONE_STRAIN_WEIGHTS = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        private const val LIGHT_ACTIVITY_WEIGHT = 0.5
    }
}
