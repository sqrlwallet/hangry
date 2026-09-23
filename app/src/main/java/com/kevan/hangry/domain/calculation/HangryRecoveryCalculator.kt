package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.RecoveryResult
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

        // 1. HRV RMSSD Component (Higher than baseline is positive)
        val validHrvHistory = usableHistory.mapNotNull { it.hrvRmssd }
        val hrvBaseline = if (validHrvHistory.isNotEmpty()) validHrvHistory.average() else null
        val hrvScore: Double? = if (currentDayMetrics.hrvRmssd != null && hrvBaseline != null && hrvBaseline > 0) {
            val ratio = currentDayMetrics.hrvRmssd / hrvBaseline
            if (ratio >= 1.05) {
                positiveContributors.add("HRV is trending above your personal baseline.")
            } else if (ratio < 0.92) {
                negativeContributors.add("HRV is trending below your rolling baseline.")
            }
            clampScore(65.0 + (ratio - 1.0) * 120.0)
        } else null

        // 2. Resting Heart Rate Component (Lower than baseline is positive)
        val validRhrHistory = usableHistory.mapNotNull { it.restingHeartRate }
        val rhrBaseline = if (validRhrHistory.isNotEmpty()) validRhrHistory.average() else null
        val rhrScore: Double? = if (currentDayMetrics.restingHeartRate != null && rhrBaseline != null && rhrBaseline > 0) {
            val ratio = currentDayMetrics.restingHeartRate / rhrBaseline
            if (ratio <= 0.97) {
                positiveContributors.add("Resting heart rate remained steady near your baseline.")
            } else if (ratio >= 1.04) {
                negativeContributors.add("Resting heart rate was slightly elevated compared to your baseline.")
            }
            clampScore(65.0 + (1.0 - ratio) * 160.0)
        } else null

        // 3. Sleep Duration Component
        val validSleepHistory = usableHistory.mapNotNull { it.sleepDurationMinutes }
        val sleepBaseline = if (validSleepHistory.isNotEmpty()) {
            validSleepHistory.average()
        } else {
            config.defaultTargetSleepMinutes.toDouble()
        }
        val sleepScore: Double? = if (currentDayMetrics.sleepDurationMinutes != null && sleepBaseline > 0) {
            val ratio = currentDayMetrics.sleepDurationMinutes.toDouble() / sleepBaseline
            if (ratio >= 0.95) {
                positiveContributors.add("Solid sleep duration supported your recovery today.")
            } else if (ratio < 0.88) {
                negativeContributors.add("Your sleep was shorter than your usual pattern.")
            }
            clampScore(65.0 + (ratio - 1.0) * 90.0)
        } else null

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

        // 4. Training Load & Consistency Component - only when consistency is actually known.
        val loadScore: Double? = currentDayMetrics.sleepConsistencyPercentage?.let { clampScore((it * 0.7) + 15.0) }

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

        val rawFinalScore = if (totalWeight > 0.0) weightedSum / totalWeight else 50.0
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
                "Your physiological markers are elevated above baseline. You are primed for high effort today."
            RecoveryState.BALANCED ->
                "Steady energy today. Well-suited for consistent training and daily activity."
            RecoveryState.REBUILD ->
                "Take it easier today. Focus on restorative rest, good nutrition, and recovery."
            RecoveryState.BUILDING_BASELINE ->
                "Your baseline is still building. Wear your device regularly to establish your baseline."
        }

        if (positiveContributors.isEmpty() && state != RecoveryState.REBUILD) {
            positiveContributors.add("Consistent physiological baseline rhythm")
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

    private fun clampScore(value: Double): Double = max(0.0, min(100.0, value))
    private fun clampIntScore(value: Int): Int = max(0, min(100, value))
}
