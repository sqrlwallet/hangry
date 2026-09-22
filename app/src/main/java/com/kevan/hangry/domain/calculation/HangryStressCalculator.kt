package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.StressLevel
import com.kevan.hangry.domain.model.StressResult
import kotlin.math.roundToInt

class HangryStressCalculator : StressCalculator {

    override fun calculateStress(
        hrvToday: Double?,
        hrvBaseline7d: Double?,
        rhrToday: Double?,
        rhrBaseline7d: Double?,
        dayStrain: Double?,
        baselineDaysCount: Int
    ): StressResult? {
        // Stress can ONLY be calculated from data.
        // If we do not have at least 3 baseline days and usable baseline metrics, we cannot calculate it.
        if (baselineDaysCount < 3 || (hrvBaseline7d == null && rhrBaseline7d == null)) {
            return null
        }

        var totalWeight = 0.0
        var weightedSum = 0.0

        val hrvRatio = if (hrvToday != null && hrvBaseline7d != null && hrvBaseline7d > 0) {
            val ratio = hrvToday / hrvBaseline7d
            // Depressed HRV relative to baseline indicates sympathetic stress
            val hrvScore = (50.0 + (1.0 - ratio) * 150.0).coerceIn(0.0, 100.0)
            weightedSum += hrvScore * 0.60
            totalWeight += 0.60
            ratio
        } else null

        val rhrDiff = if (rhrToday != null && rhrBaseline7d != null) {
            val diff = rhrToday - rhrBaseline7d
            // Elevated RHR relative to baseline indicates cardiovascular/systemic stress
            val rhrScore = (35.0 + diff * 7.0).coerceIn(0.0, 100.0)
            weightedSum += rhrScore * 0.40
            totalWeight += 0.40
            diff
        } else null

        // If neither HRV nor RHR is available today, stress cannot be calculated from data.
        if (totalWeight == 0.0) {
            return null
        }

        var calculatedScore = weightedSum / totalWeight

        // Adjustment for high cardiovascular strain accumulated today
        if (dayStrain != null && dayStrain > 14.0) {
            val strainBonus = ((dayStrain - 14.0) / 7.0 * 8.0).coerceIn(0.0, 8.0)
            calculatedScore = (calculatedScore + strainBonus).coerceIn(0.0, 100.0)
        }

        val roundedScore = calculatedScore.roundToInt()

        val level = when {
            roundedScore < 35 -> StressLevel.LOW
            roundedScore < 65 -> StressLevel.MODERATE
            roundedScore < 80 -> StressLevel.ELEVATED
            else -> StressLevel.HIGH
        }

        val confidence = when {
            baselineDaysCount >= 7 && hrvRatio != null && rhrDiff != null -> "HIGH"
            baselineDaysCount >= 3 && (hrvRatio != null || rhrDiff != null) -> "MEDIUM"
            else -> "LOW"
        }

        val (summary, advice) = when (level) {
            StressLevel.LOW -> Pair(
                "Autonomic system is balanced and calm.",
                "Your heart rate and variability show optimal parasympathetic tone. A great day for productive focus or high effort."
            )
            StressLevel.MODERATE -> Pair(
                "Steady energy with normal everyday stress.",
                "Your body is managing typical daily demands well. Maintain balanced hydration and regular movement."
            )
            StressLevel.ELEVATED -> Pair(
                "Elevated physiological strain detected.",
                "Suppressed HRV or elevated resting pulse indicates fatigue. Consider active recovery, breathwork, or an earlier bedtime."
            )
            StressLevel.HIGH, StressLevel.BUILDING_BASELINE -> Pair(
                "High autonomic stress state.",
                "Your body is under notable physiological strain. Prioritize gentle stretching, restorative rest, and good sleep tonight."
            )
        }

        return StressResult(
            score = roundedScore,
            level = level,
            hrvDeviationRatio = hrvRatio,
            rhrDeviationBpm = rhrDiff,
            summaryText = summary,
            supportiveAdvice = advice,
            confidence = confidence
        )
    }
}
