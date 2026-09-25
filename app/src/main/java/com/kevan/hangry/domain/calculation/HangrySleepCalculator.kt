package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepAnalysis
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sleep need starts from the user's goal and grows with recent sleep debt and an unusually hard
 * day; the Sleep Score weighs how much of that need was met with how well the night went. See
 * docs/logic/06-scores-recovery-sleep-strain-stress.md.
 */
class HangrySleepCalculator(private val zone: ZoneId = ZoneId.systemDefault()) : SleepCalculator {

    override fun analyzeSleep(
        currentSession: SleepSessionEntity?,
        recentSessions: List<SleepSessionEntity>,
        targetDurationMinutes: Int,
        previousDayStrain: Double?,
        rollingAverageStrain: Double?
    ): SleepAnalysis {
        // One entry per night (the main sleep of each wake-up day), so naps and split sessions
        // don't count as extra nights in averages, debt or consistency.
        val nights = mainSleeps(recentSessions)
        val sevenDayAvg = nights.take(7).takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()?.roundToInt()
        val thirtyDayAvg = nights.take(30).takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()?.roundToInt()

        val target = targetDurationMinutes.coerceIn(MIN_TARGET_MINUTES, MAX_TARGET_MINUTES)
        val strainAdjustment = strainAdjustment(previousDayStrain, rollingAverageStrain)
        // Need for the night being analysed: goal + debt from the nights before it + a hard day.
        val sleepNeed = (target + debtCarryover(nights.map { it.durationMinutes }, target) + strainAdjustment)
            .coerceIn(target, target + MAX_EXTRA_NEED_MINUTES)
        // Tonight's need also carries whatever last night fell short by.
        val tonightsNeed = if (currentSession == null) sleepNeed else
            (target + debtCarryover(listOf(currentSession.durationMinutes) + nights.map { it.durationMinutes }, target))
                .coerceIn(target, target + MAX_EXTRA_NEED_MINUTES)

        val timingNights = listOfNotNull(currentSession) + nights.take(if (currentSession == null) 7 else 6)
        val consistency = timingConsistency(timingNights)
        val recommendedBedtime = recommendedBedtime(listOfNotNull(currentSession) + nights, tonightsNeed)

        if (currentSession == null) {
            return SleepAnalysis(
                durationMinutes = 0,
                timeInBedMinutes = null,
                sleepNeedMinutes = sleepNeed,
                tonightsNeedMinutes = tonightsNeed,
                recommendedBedtime = recommendedBedtime,
                consistencyPercentage = consistency,
                sevenDayAverageMinutes = sevenDayAvg,
                thirtyDayAverageMinutes = thirtyDayAvg,
                sleepDebtMinutes = 0,
                baselineRatio = 0.0,
                supportiveNote = "No sleep recorded yet for this session."
            )
        }

        val duration = currentSession.durationMinutes
        val timeInBed = currentSession.timeInBedMinutes?.takeIf { it >= duration }

        val sleepDebt = max(0, sleepNeed - duration)
        val sleepPerformance = ((duration.toDouble() / sleepNeed) * 100).roundToInt().coerceIn(0, 150)
        val baselineRatio = if (sevenDayAvg != null && sevenDayAvg > 0) duration.toDouble() / sevenDayAvg else 1.0

        val supportiveNote = when {
            nights.isEmpty() -> "Your first night logged. Your personal baseline builds from here."
            sleepPerformance >= 100 -> "You got the sleep you needed - that supports your recovery today."
            sleepPerformance >= 85 -> "Close to the sleep you needed."
            else -> "Shorter than the sleep you needed. An earlier night would help."
        }

        // Stages only when the device recorded them - never estimated from fixed percentages.
        val deep = currentSession.deepSleepMinutes
        val rem = currentSession.remSleepMinutes
        val awake = currentSession.awakeMinutes
        // Duration is time asleep; for rows still counting time in bed, awake time comes off too.
        val light = currentSession.lightSleepMinutes
            ?: if (deep != null && rem != null) {
                max(0, duration - deep - rem - if (timeInBed != null && timeInBed > duration) 0 else (awake ?: 0))
            } else null
        val restorativePct = if (deep != null && rem != null && duration > 0) {
            ((deep + rem).toDouble() / duration * 100).roundToInt().coerceIn(0, 100)
        } else null

        val efficiencyScore = timeInBed?.takeIf { it > 0 }?.let { efficiencyScore(duration.toDouble() / it * 100) }
        val restorativeScore = if (deep != null && rem != null && duration > 0) restorativeScore(deep, rem, duration) else null

        val qualityScore = weighted(
            efficiencyScore to QUALITY_EFFICIENCY_WEIGHT,
            restorativeScore to QUALITY_RESTORATIVE_WEIGHT,
            consistency?.toDouble() to QUALITY_CONSISTENCY_WEIGHT
        )
        val sleepScore = weighted(
            sleepPerformance.coerceAtMost(100).toDouble() to SCORE_PERFORMANCE_WEIGHT,
            efficiencyScore to SCORE_EFFICIENCY_WEIGHT,
            restorativeScore to SCORE_RESTORATIVE_WEIGHT,
            consistency?.toDouble() to SCORE_CONSISTENCY_WEIGHT
        )

        return SleepAnalysis(
            durationMinutes = duration,
            timeInBedMinutes = timeInBed,
            deepSleepMinutes = deep,
            remSleepMinutes = rem,
            lightSleepMinutes = light,
            awakeMinutes = awake,
            restorativePercentage = restorativePct,
            efficiencyPercentage = timeInBed?.takeIf { it > 0 }?.let { (duration * 100.0 / it).roundToInt().coerceIn(0, 100) },
            sleepNeedMinutes = sleepNeed,
            tonightsNeedMinutes = tonightsNeed,
            sleepPerformancePercentage = sleepPerformance,
            sleepQualityScore = qualityScore,
            sleepScore = sleepScore,
            efficiencyScore = efficiencyScore?.roundToInt(),
            restorativeScore = restorativeScore?.roundToInt(),
            recommendedBedtime = recommendedBedtime,
            consistencyPercentage = consistency,
            sevenDayAverageMinutes = sevenDayAvg,
            thirtyDayAverageMinutes = thirtyDayAvg,
            sleepDebtMinutes = sleepDebt,
            baselineRatio = baselineRatio,
            supportiveNote = supportiveNote
        )
    }

    /** The longest session per wake-up day, newest first. */
    fun mainSleeps(sessions: List<SleepSessionEntity>): List<SleepSessionEntity> =
        sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .values
            .map { night -> night.maxBy { it.durationMinutes } }

    /**
     * Sleep debt is paid back gradually - over up to two weeks, not in one night. The debt is the
     * running shortfall against the goal over the last 14 nights (a longer night pays some back);
     * each night then needs 1/14 of it extra. Capped at an hour: beyond that the goal itself is
     * probably out of step with the user's life.
     */
    private fun debtCarryover(recentNightsNewestFirst: List<Int>, target: Int): Int {
        var debt = 0.0
        // Oldest first, so a long night only repays debt that already existed.
        for (minutes in recentNightsNewestFirst.take(DEBT_WINDOW_NIGHTS).reversed()) {
            debt = max(0.0, debt + (target - minutes))
        }
        return (debt / DEBT_WINDOW_NIGHTS).roundToInt().coerceIn(0, MAX_DEBT_CARRYOVER_MINUTES)
    }

    /** Extra sleep after a day harder than usual, up to 30 minutes. */
    private fun strainAdjustment(previousDayStrain: Double?, rollingAverageStrain: Double?): Int {
        if (previousDayStrain == null || rollingAverageStrain == null || rollingAverageStrain <= 0.0) return 0
        val ratio = previousDayStrain / rollingAverageStrain
        return if (ratio > 1.0) ((ratio - 1.0) * STRAIN_ADJUSTMENT_MINUTES_PER_RATIO_UNIT).roundToInt().coerceIn(0, MAX_STRAIN_ADJUSTMENT_MINUTES) else 0
    }

    /**
     * How regular bed and wake times are over the last week (including this night), from the
     * circular spread of each - so 23:50 and 00:10 count as 20 minutes apart. A 30-minute spread
     * scores 85, an hour 70, two hours 40. Needs 3 nights.
     */
    private fun timingConsistency(nights: List<SleepSessionEntity>): Int? {
        if (nights.size < 3) return null
        val bedSpread = circularSpreadMinutes(nights.map { minuteOfDay(it.startTime) })
        val wakeSpread = circularSpreadMinutes(nights.map { minuteOfDay(it.endTime) })
        return (100 - CONSISTENCY_POINTS_PER_MINUTE * (bedSpread + wakeSpread) / 2).roundToInt().coerceIn(0, 100)
    }

    /** Usual wake time (circular mean over the last week) minus tonight's need. */
    private fun recommendedBedtime(nights: List<SleepSessionEntity>, needMinutes: Int): LocalTime? {
        val wakes = nights.take(7).map { minuteOfDay(it.endTime) }
        if (wakes.isEmpty()) return null
        val avgWake = circularMeanMinutes(wakes)
        val bedtime = (((avgWake - needMinutes) % 1440 + 1440) % 1440).roundToInt() % 1440
        return LocalTime.of(bedtime / 60, bedtime % 60)
    }

    private fun minuteOfDay(instant: java.time.Instant): Double =
        instant.atZone(zone).let { it.hour * 60.0 + it.minute }

    /** 92%+ asleep while in bed scores 100; 65% or less scores 0. */
    private fun efficiencyScore(efficiencyPct: Double): Double =
        ((efficiencyPct - EFFICIENCY_FLOOR) / (EFFICIENCY_IDEAL - EFFICIENCY_FLOOR) * 100).coerceIn(0.0, 100.0)

    /** Deep and REM each scored against a healthy adult share (about 15% and 20% of sleep). */
    private fun restorativeScore(deep: Int, rem: Int, duration: Int): Double {
        val deepPart = (deep * 100.0 / duration / IDEAL_DEEP_PERCENTAGE).coerceAtMost(1.0)
        val remPart = (rem * 100.0 / duration / IDEAL_REM_PERCENTAGE).coerceAtMost(1.0)
        return (deepPart + remPart) / 2 * 100
    }

    /** Weighted mean of what was measured; missing parts are left out, never scored as zero. */
    private fun weighted(vararg parts: Pair<Double?, Double>): Int? {
        val present = parts.filter { it.first != null }
        if (present.isEmpty()) return null
        val total = present.sumOf { it.second }
        return (present.sumOf { it.first!! * it.second } / total).roundToInt().coerceIn(0, 100)
    }

    companion object {
        private const val MIN_TARGET_MINUTES = 240
        private const val MAX_TARGET_MINUTES = 720
        private const val MAX_EXTRA_NEED_MINUTES = 90

        /** Sleep debt is repaid over this many nights. */
        private const val DEBT_WINDOW_NIGHTS = 14
        private const val MAX_DEBT_CARRYOVER_MINUTES = 60
        private const val STRAIN_ADJUSTMENT_MINUTES_PER_RATIO_UNIT = 30.0
        private const val MAX_STRAIN_ADJUSTMENT_MINUTES = 30

        private const val CONSISTENCY_POINTS_PER_MINUTE = 0.5
        private const val EFFICIENCY_FLOOR = 65.0
        private const val EFFICIENCY_IDEAL = 92.0
        private const val IDEAL_DEEP_PERCENTAGE = 15.0
        private const val IDEAL_REM_PERCENTAGE = 20.0

        // Quality: how well you slept, regardless of how long.
        private const val QUALITY_EFFICIENCY_WEIGHT = 0.40
        private const val QUALITY_RESTORATIVE_WEIGHT = 0.35
        private const val QUALITY_CONSISTENCY_WEIGHT = 0.25

        // Sleep Score: how much of your need you got, plus how well.
        private const val SCORE_PERFORMANCE_WEIGHT = 0.50
        private const val SCORE_EFFICIENCY_WEIGHT = 0.15
        private const val SCORE_RESTORATIVE_WEIGHT = 0.20
        private const val SCORE_CONSISTENCY_WEIGHT = 0.15

        internal fun circularMeanMinutes(minutes: List<Double>): Double {
            val angles = minutes.map { it / 1440.0 * 2 * PI }
            val mean = atan2(angles.sumOf { sin(it) }, angles.sumOf { cos(it) })
            return ((mean / (2 * PI) * 1440.0) + 1440.0) % 1440.0
        }

        /** Circular standard deviation in minutes. */
        internal fun circularSpreadMinutes(minutes: List<Double>): Double {
            val angles = minutes.map { it / 1440.0 * 2 * PI }
            val r = sqrt(angles.sumOf { sin(it) }.let { it * it } + angles.sumOf { cos(it) }.let { it * it }) / angles.size
            if (r >= 1.0) return 0.0
            return sqrt(-2 * ln(r.coerceAtLeast(1e-9))) / (2 * PI) * 1440.0
        }
    }
}
