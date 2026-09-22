package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepAnalysis
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

class HangrySleepCalculator : SleepCalculator {

    override fun analyzeSleep(
        currentSession: SleepSessionEntity?,
        recentSessions: List<SleepSessionEntity>,
        targetDurationMinutes: Int,
        previousDaySleepDebtMinutes: Int,
        previousDayStrain: Double?,
        rollingAverageStrain: Double?
    ): SleepAnalysis {
        val sevenDaySessions = recentSessions.take(7)
        val sevenDayAvg = if (sevenDaySessions.isNotEmpty()) {
            sevenDaySessions.map { it.durationMinutes }.average().roundToInt()
        } else {
            targetDurationMinutes
        }

        val thirtyDaySessions = recentSessions.take(30)
        val thirtyDayAvg = if (thirtyDaySessions.isNotEmpty()) {
            thirtyDaySessions.map { it.durationMinutes }.average().roundToInt()
        } else {
            sevenDayAvg
        }

        val sleepNeed = computeSleepNeedMinutes(
            personalBaselineMinutes = sevenDayAvg,
            previousDaySleepDebtMinutes = previousDaySleepDebtMinutes,
            previousDayStrain = previousDayStrain,
            rollingAverageStrain = rollingAverageStrain
        )
        val recommendedBedtime = recommendedBedtime(recentSessions, sleepNeed)

        if (currentSession == null) {
            return SleepAnalysis(
                durationMinutes = 0,
                timeInBedMinutes = null,
                consistencyPercentage = 0,
                sleepNeedMinutes = sleepNeed,
                sleepPerformancePercentage = 0,
                sleepQualityScore = 0,
                recommendedBedtime = recommendedBedtime,
                sevenDayAverageMinutes = sevenDayAvg,
                thirtyDayAverageMinutes = thirtyDayAvg,
                sleepDebtMinutes = 0,
                baselineRatio = 0.0,
                supportiveNote = "No sleep recorded yet for this session."
            )
        }

        val duration = currentSession.durationMinutes
        val timeInBed = currentSession.timeInBedMinutes

        val sleepDebt = max(0, sleepNeed - duration)
        val sleepPerformance = if (sleepNeed > 0) {
            ((duration.toDouble() / sleepNeed) * 100).roundToInt().coerceIn(0, 150)
        } else {
            100
        }
        val baselineRatio = if (sevenDayAvg > 0) duration.toDouble() / sevenDayAvg else 1.0

        val supportiveNote = when {
            baselineRatio >= 1.05 -> "Solid sleep duration supported your recovery today."
            baselineRatio in 0.95..1.04 -> "Your sleep aligned well with your typical baseline."
            else -> "Your sleep was shorter than your usual pattern."
        }

        // Consistency calculation: comparison of session durations against 7-day average
        val consistency = if (sevenDaySessions.size >= 3) {
            val variance = sevenDaySessions.map { Math.abs(it.durationMinutes - sevenDayAvg) }.average()
            max(40, (100 - (variance / 6.0)).roundToInt())
        } else {
            85 // Default calibration baseline
        }

        val deep = currentSession.deepSleepMinutes ?: (duration * 0.20).roundToInt()
        val rem = currentSession.remSleepMinutes ?: (duration * 0.25).roundToInt()
        val awake = currentSession.awakeMinutes ?: max(0, (duration * 0.05).roundToInt())
        val light = currentSession.lightSleepMinutes ?: max(0, duration - deep - rem - awake)
        val restorativePct = if (duration > 0) ((deep + rem).toDouble() / duration * 100).roundToInt().coerceIn(0, 100) else 0

        val qualityScore = computeQualityScore(
            durationMinutes = duration,
            timeInBedMinutes = timeInBed,
            restorativePercentage = restorativePct,
            consistencyPercentage = consistency
        )

        return SleepAnalysis(
            durationMinutes = duration,
            timeInBedMinutes = timeInBed,
            deepSleepMinutes = deep,
            remSleepMinutes = rem,
            lightSleepMinutes = light,
            awakeMinutes = awake,
            restorativePercentage = restorativePct,
            sleepNeedMinutes = sleepNeed,
            sleepPerformancePercentage = sleepPerformance,
            sleepQualityScore = qualityScore,
            recommendedBedtime = recommendedBedtime,
            consistencyPercentage = consistency,
            sevenDayAverageMinutes = sevenDayAvg,
            thirtyDayAverageMinutes = thirtyDayAvg,
            sleepDebtMinutes = sleepDebt,
            baselineRatio = baselineRatio,
            supportiveNote = supportiveNote
        )
    }

    /**
     * See CALCULATIONS.md §7.1. Unlike sleep performance (how much you slept vs. how much you
     * needed - a quantity measure), quality combines how unbroken the sleep was (efficiency:
     * time actually asleep vs. time in bed), how restorative it was (deep + REM share of total
     * sleep, scored against a healthy-range target rather than used raw), and night-to-night
     * consistency. Never substitutes a missing efficiency reading with zero - it reweights
     * onto the remaining components instead.
     */
    private fun computeQualityScore(
        durationMinutes: Int,
        timeInBedMinutes: Int?,
        restorativePercentage: Int,
        consistencyPercentage: Int
    ): Int {
        val efficiencyScore = if (timeInBedMinutes != null && timeInBedMinutes > 0) {
            ((durationMinutes.toDouble() / timeInBedMinutes) * 100).coerceIn(0.0, 100.0)
        } else {
            null
        }
        val restorativeScore = (restorativePercentage / IDEAL_RESTORATIVE_PERCENTAGE * 100.0).coerceIn(0.0, 100.0)
        val consistencyScore = consistencyPercentage.toDouble().coerceIn(0.0, 100.0)

        var weightedSum = restorativeScore * RESTORATIVE_WEIGHT
        var totalWeight = RESTORATIVE_WEIGHT
        weightedSum += consistencyScore * CONSISTENCY_WEIGHT
        totalWeight += CONSISTENCY_WEIGHT
        if (efficiencyScore != null) {
            weightedSum += efficiencyScore * EFFICIENCY_WEIGHT
            totalWeight += EFFICIENCY_WEIGHT
        }

        return (weightedSum / totalWeight).roundToInt().coerceIn(0, 100)
    }

    /**
     * See CALCULATIONS.md §7. Personal baseline (7-day average duration, or the target when
     * there's no history yet) plus a partial carryover of yesterday's unpaid sleep debt plus
     * extra recovery time when yesterday was unusually demanding relative to the user's own
     * rolling strain average. Naps are not tracked in the current schema and are intentionally
     * excluded rather than silently approximated.
     */
    private fun computeSleepNeedMinutes(
        personalBaselineMinutes: Int,
        previousDaySleepDebtMinutes: Int,
        previousDayStrain: Double?,
        rollingAverageStrain: Double?
    ): Int {
        val debtCarryover = (previousDaySleepDebtMinutes * DEBT_CARRYOVER_FRACTION)
            .roundToInt()
            .coerceIn(0, MAX_DEBT_CARRYOVER_MINUTES)

        val strainAdjustment = if (previousDayStrain != null && rollingAverageStrain != null && rollingAverageStrain > 0.0) {
            val ratio = previousDayStrain / rollingAverageStrain
            if (ratio > 1.0) {
                ((ratio - 1.0) * STRAIN_ADJUSTMENT_MINUTES_PER_RATIO_UNIT).roundToInt().coerceIn(0, MAX_STRAIN_ADJUSTMENT_MINUTES)
            } else {
                0
            }
        } else {
            0
        }

        return (personalBaselineMinutes + debtCarryover + strainAdjustment)
            .coerceIn(personalBaselineMinutes, personalBaselineMinutes + MAX_DEBT_CARRYOVER_MINUTES + MAX_STRAIN_ADJUSTMENT_MINUTES)
    }

    /**
     * Target wake time is the rolling average wake time-of-day over the last 7 sessions;
     * bedtime is simply that minus tonight's sleep need. Returns null until there's at
     * least one recent session to anchor a wake-time baseline on.
     */
    private fun recommendedBedtime(recentSessions: List<SleepSessionEntity>, sleepNeedMinutes: Int): LocalTime? {
        val recentWakeTimes = recentSessions.take(7)
        if (recentWakeTimes.isEmpty()) return null

        val avgWakeMinuteOfDay = recentWakeTimes.map { session ->
            val zoned = session.endTime.atZone(ZoneId.systemDefault())
            zoned.hour * 60 + zoned.minute
        }.average()

        val bedtimeMinuteOfDay = (((avgWakeMinuteOfDay - sleepNeedMinutes) % 1440 + 1440) % 1440).roundToInt()
        return LocalTime.of(bedtimeMinuteOfDay / 60, bedtimeMinuteOfDay % 60)
    }

    companion object {
        private const val DEBT_CARRYOVER_FRACTION = 0.3
        private const val MAX_DEBT_CARRYOVER_MINUTES = 90
        private const val STRAIN_ADJUSTMENT_MINUTES_PER_RATIO_UNIT = 60.0
        private const val MAX_STRAIN_ADJUSTMENT_MINUTES = 60

        // Quality score component weights and the restorative-percentage target they're scored against.
        private const val EFFICIENCY_WEIGHT = 0.40
        private const val RESTORATIVE_WEIGHT = 0.35
        private const val CONSISTENCY_WEIGHT = 0.25
        private const val IDEAL_RESTORATIVE_PERCENTAGE = 45.0
    }
}
