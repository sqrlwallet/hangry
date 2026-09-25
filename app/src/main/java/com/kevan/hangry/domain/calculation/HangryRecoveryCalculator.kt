package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.RecoveryResult
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Recovery from overnight HRV and resting heart rate against the user's own last 30 days, how
 * much of their sleep need they got, and whether yesterday's strain stayed within its target.
 *
 * HRV is scored by how unusual last night is for *you* (standard deviations from your normal,
 * on a log scale as in the research): a reading at your normal scores 65, each SD moves it 15.
 * Resting heart rate loses 2 points per bpm above your monthly average.
 */
class HangryRecoveryCalculator : RecoveryCalculator {

    override fun calculateRecovery(
        date: LocalDate,
        currentDayMetrics: DayMetrics,
        baselineHistory: List<DayMetrics>,
        config: RecoveryConfig
    ): RecoveryResult {
        // Filter history to usable days with at least one physiological signal
        val usableHistory = baselineHistory.filter {
            it.hrvRmssd != null || it.restingHeartRate != null || it.sleepDurationMinutes != null
        }

        // Rule: Fewer than minDaysForBaseline (3 days) -> Show Building your baseline
        if (usableHistory.size < config.minDaysForBaseline) {
            return RecoveryResult(
                score = null,
                confidence = ScoreConfidence.LOW,
                state = RecoveryState.BUILDING_BASELINE,
                supportiveAdvice = "Your baseline is still building. Wear your device regularly to establish your personal physiological baseline.",
                baselineDaysCount = usableHistory.size,
                positiveContributors = listOf("Calibrating your baseline metrics"),
                negativeContributors = emptyList(),
                algorithmVersion = config.algorithmVersion
            )
        }

        val positiveContributors = mutableListOf<String>()
        val negativeContributors = mutableListOf<String>()

        // 1. HRV: higher than your normal is better. Log scale, because HRV is skewed.
        val hrvHistory = usableHistory.mapNotNull { it.hrvRmssd?.takeIf { v -> v > 0 }?.let(::ln) }
        val hrvScore: Double? = currentDayMetrics.hrvRmssd?.takeIf { it > 0 && hrvHistory.isNotEmpty() }?.let { today ->
            val z = (ln(today) - hrvHistory.average()) / max(sd(hrvHistory), MIN_LN_HRV_SD)
            if (z >= NOTABLE_Z) positiveContributors.add("HRV is above your usual - your body is handling stress well.")
            else if (z <= -NOTABLE_Z) negativeContributors.add("HRV is below your usual, a sign your body is still recovering.")
            zScore(z)
        }

        // 2. Resting heart rate against your monthly average: at or below it is full marks, and
        //    each bpm above it costs 2 points (1 bpm up = 98, 3 up = 94, 10 up = 80).
        val rhrHistory = usableHistory.mapNotNull { it.restingHeartRate }
        val rhrScore: Double? = currentDayMetrics.restingHeartRate?.takeIf { it > 0 && rhrHistory.isNotEmpty() }?.let { today ->
            val deviation = today - rhrHistory.average()
            val bpm = deviation.roundToInt()
            if (deviation <= -RHR_NOTABLE_BPM) positiveContributors.add("Resting heart rate is ${-bpm} bpm below your monthly average.")
            else if (deviation >= RHR_NOTABLE_BPM) negativeContributors.add("Resting heart rate is $bpm bpm above your monthly average.")
            clampScore(100.0 - RHR_POINTS_PER_BPM * max(0.0, deviation))
        }

        // 3. Sleep: how much of what you needed you got, with a quarter for how well you slept.
        val sleepScore: Double? = currentDayMetrics.sleepDurationMinutes?.let { slept ->
            val need = currentDayMetrics.sleepNeedMinutes
            val durationScore = if (need != null && need > 0) {
                val met = slept.toDouble() / need
                if (met >= 0.95) positiveContributors.add("You got the sleep you needed.")
                else if (met < 0.85) negativeContributors.add("You slept ${(met * 100).roundToInt()}% of what you needed.")
                clampScore(85.0 + (met - 1.0) * 150.0)
            } else {
                // No need worked out (older callers): compare with your usual length instead.
                val usual = usableHistory.mapNotNull { it.sleepDurationMinutes }.takeIf { it.isNotEmpty() }?.average()
                    ?: config.defaultTargetSleepMinutes.toDouble()
                val ratio = slept / usual
                if (ratio >= 0.95) positiveContributors.add("Solid sleep duration supported your recovery today.")
                else if (ratio < 0.88) negativeContributors.add("Your sleep was shorter than your usual pattern.")
                clampScore(65.0 + (ratio - 1.0) * 90.0)
            }
            currentDayMetrics.sleepQualityScore?.let { durationScore * 0.75 + it * 0.25 } ?: durationScore
        }

        // A day with no sleep, resting heart rate or HRV has nothing to score. The assumed-HRV
        // rule below only fills a gap next to real readings - it can't stand in for all of them.
        if (hrvScore == null && rhrScore == null && sleepScore == null) {
            return RecoveryResult(
                score = null,
                confidence = ScoreConfidence.LOW,
                state = RecoveryState.BUILDING_BASELINE,
                supportiveAdvice = "No sleep or heart readings for this day yet.",
                baselineDaysCount = usableHistory.size,
                positiveContributors = emptyList(),
                negativeContributors = emptyList(),
                algorithmVersion = config.algorithmVersion
            )
        }

        // 4. Strain balance: yesterday's strain against the target it was given. In range or
        //    lighter is fine (lighter leaves room to push today); going over costs points.
        val loadScore: Double? = currentDayMetrics.previousDayStrain?.let { strain ->
            val target = currentDayMetrics.previousDayStrainTarget
            when {
                target == null -> null
                strain > target.endInclusive -> {
                    negativeContributors.add(
                        String.format(java.util.Locale.US, "Yesterday's strain (%.1f) went over its target of %.1f - your body needs some recovery.", strain, target.endInclusive)
                    )
                    clampScore(100.0 - (strain - target.endInclusive) * STRAIN_OVERSHOOT_POINTS)
                }
                strain < target.start -> {
                    positiveContributors.add(String.format(java.util.Locale.US, "Yesterday was lighter than its target (%.1f) - you have room to push today.", strain))
                    100.0
                }
                else -> {
                    positiveContributors.add(String.format(java.util.Locale.US, "Yesterday's strain (%.1f) was right in its target range.", strain))
                    100.0
                }
            }
        }

        // Dynamic re-weighting based on available components (NEVER substitute missing data with zero).
        // HRV is the exception: when it isn't available it counts as an excellent day (config.assumedHrvScore).
        // result.hrvScore stays null so the UI still shows HRV as not reported.
        var totalWeight = 0.0
        var weightedSum = 0.0

        weightedSum += (hrvScore ?: config.assumedHrvScore) * config.hrvWeight
        totalWeight += config.hrvWeight
        if (rhrScore != null) {
            weightedSum += rhrScore * config.rhrWeight
            totalWeight += config.rhrWeight
        }
        if (sleepScore != null) {
            weightedSum += sleepScore * config.sleepWeight
            totalWeight += config.sleepWeight
        }
        if (loadScore != null) {
            weightedSum += loadScore * config.loadWeight
            totalWeight += config.loadWeight
        }

        // 5. Breathing rate well above your usual is often an early sign of illness - it pulls
        //    the score down even when HRV hasn't caught up yet.
        val respHistory = usableHistory.mapNotNull { it.respiratoryRate }
        val respPenalty = currentDayMetrics.respiratoryRate?.takeIf { respHistory.size >= MIN_RESP_HISTORY }?.let { today ->
            val rise = today - respHistory.average()
            if (rise >= RESP_RISE_NOTABLE) {
                negativeContributors.add("Breathing rate is up ${String.format(java.util.Locale.US, "%.1f", rise)}/min on your usual - sometimes an early sign of illness.")
                ((rise - 0.5) * RESP_PENALTY_PER_BREATH).coerceIn(0.0, MAX_RESP_PENALTY)
            } else 0.0
        } ?: 0.0

        val rawFinalScore = (if (totalWeight > 0.0) weightedSum / totalWeight else 50.0) - respPenalty
        val finalScore = clampIntScore(rawFinalScore.roundToInt())

        // Confidence determination
        val confidence = when {
            usableHistory.size >= config.optimalBaselineDays && hrvScore != null && rhrScore != null && sleepScore != null ->
                ScoreConfidence.HIGH
            usableHistory.size >= config.minDaysForBaseline && (hrvScore != null || rhrScore != null || sleepScore != null) ->
                ScoreConfidence.MEDIUM
            else ->
                ScoreConfidence.LOW
        }

        // Recovery State categorization
        val state = when {
            finalScore >= 70 -> RecoveryState.PRIMED
            finalScore >= 40 -> RecoveryState.BALANCED
            else -> RecoveryState.REBUILD
        }

        // Supportive advice without clinical or shame-based language
        val supportiveAdvice = when (state) {
            RecoveryState.PRIMED ->
                "Your body is well recovered compared with your usual. You're primed for high effort today."
            RecoveryState.BALANCED ->
                "Steady energy today. Well-suited for consistent training and daily activity."
            RecoveryState.REBUILD ->
                "Take it easier today. Focus on restorative rest, good nutrition, and recovery."
            RecoveryState.BUILDING_BASELINE ->
                "Your baseline is still building. Wear your device regularly to establish your baseline."
        }

        if (positiveContributors.isEmpty() && state != RecoveryState.REBUILD) {
            positiveContributors.add("Your readings are close to your usual.")
        }

        return RecoveryResult(
            score = finalScore,
            confidence = confidence,
            state = state,
            hrvScore = hrvScore,
            rhrScore = rhrScore,
            sleepScore = sleepScore,
            trainingLoadScore = loadScore,
            positiveContributors = positiveContributors,
            negativeContributors = negativeContributors,
            supportiveAdvice = supportiveAdvice,
            baselineDaysCount = usableHistory.size,
            algorithmVersion = config.algorithmVersion
        )
    }

    /** 65 at your normal, +/-15 per standard deviation. */
    private fun zScore(z: Double): Double = clampScore(BASELINE_SCORE + z * POINTS_PER_SD)

    private fun sd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    private fun clampScore(value: Double): Double = max(0.0, min(100.0, value))
    private fun clampIntScore(value: Int): Int = max(0, min(100, value))

    private companion object {
        const val BASELINE_SCORE = 65.0
        const val POINTS_PER_SD = 15.0
        /** Past the "smallest worthwhile change" used in HRV research. */
        const val NOTABLE_Z = 0.75
        /** Floors so a very steady (or short) history doesn't turn tiny changes into big swings. */
        const val MIN_LN_HRV_SD = 0.08
        const val RHR_POINTS_PER_BPM = 2.0
        const val RHR_NOTABLE_BPM = 3.0
        /** Points off per strain point over yesterday's target (3 over = 64). */
        const val STRAIN_OVERSHOOT_POINTS = 12.0
        const val MIN_RESP_HISTORY = 5
        const val RESP_RISE_NOTABLE = 1.0
        const val RESP_PENALTY_PER_BREATH = 6.0
        const val MAX_RESP_PENALTY = 12.0
    }
}
