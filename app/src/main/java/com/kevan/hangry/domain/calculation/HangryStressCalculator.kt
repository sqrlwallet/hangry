package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.StressLevel
import com.kevan.hangry.domain.model.StressResult
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Autonomic stress from overnight HRV and resting heart rate against the user's own last 30 days,
 * scored by how unusual today is *for them* - standard deviations from their normal, HRV on a log
 * scale - the same way Hangry Recovery reads them. A 30-day normal (WHOOP uses 30, Polar 28)
 * doesn't absorb a stressful week the way a 7-day one did, and someone whose readings swing a
 * lot needs a bigger move to register.
 */
class HangryStressCalculator : StressCalculator {

    override fun calculateStress(
        hrvToday: Double?,
        hrvHistory: List<Double>,
        rhrToday: Double?,
        rhrHistory: List<Double>,
        dayStrain: Double?
    ): StressResult? {
        val lnHrvHistory = hrvHistory.filter { it > 0 }.map(::ln)
        val rhrBaseline = rhrHistory.filter { it > 0 }
        val baselineDaysCount = max(lnHrvHistory.size, rhrBaseline.size)
        // Stress can ONLY be calculated from data, against at least 3 days of your own normal.
        if (baselineDaysCount < MIN_BASELINE_DAYS) return null

        var totalWeight = 0.0
        var weightedSum = 0.0

        // HRV below your normal reads as stress: at normal 42, each SD lower +20.
        val hrvRatio = if (hrvToday != null && hrvToday > 0 && lnHrvHistory.size >= MIN_BASELINE_DAYS) {
            val meanLn = lnHrvHistory.average()
            val z = (ln(hrvToday) - meanLn) / max(sd(lnHrvHistory), MIN_LN_HRV_SD)
            weightedSum += (AT_NORMAL - POINTS_PER_SD * z).coerceIn(0.0, 100.0) * HRV_WEIGHT
            totalWeight += HRV_WEIGHT
            hrvToday / exp(meanLn)
        } else null

        // Resting heart rate above your normal reads as stress: each SD higher +20.
        val rhrDiff = if (rhrToday != null && rhrToday > 0 && rhrBaseline.size >= MIN_BASELINE_DAYS) {
            val mean = rhrBaseline.average()
            val z = (rhrToday - mean) / max(sd(rhrBaseline), MIN_RHR_SD)
            weightedSum += (AT_NORMAL + POINTS_PER_SD * z).coerceIn(0.0, 100.0) * RHR_WEIGHT
            totalWeight += RHR_WEIGHT
            rhrToday - mean
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

    private fun sd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    private companion object {
        const val MIN_BASELINE_DAYS = 3
        const val HRV_WEIGHT = 0.60
        const val RHR_WEIGHT = 0.40
        /** A day right at your normal sits in Moderate (35-64). */
        const val AT_NORMAL = 42.0
        /** 1.2 SD off normal is Elevated (65+), 2 SD High (80+). */
        const val POINTS_PER_SD = 20.0
        /** Floors so a very steady (or short) history doesn't turn tiny changes into big swings. */
        const val MIN_LN_HRV_SD = 0.08
        const val MIN_RHR_SD = 1.5
    }
}
