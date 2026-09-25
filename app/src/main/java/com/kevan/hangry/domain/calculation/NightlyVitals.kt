package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import java.time.Duration
import java.time.Instant

/**
 * Overnight resting heart rate and HRV - what recovery apps read, because they're measured in
 * the same calm state every night. Daytime spot readings swing with posture, coffee and stress.
 */
object NightlyVitals {

    private const val MIN_BPM = 35.0
    private val WINDOW: Duration = Duration.ofMinutes(5)
    private const val MIN_SLEEP_SAMPLES = 10

    /**
     * Lowest 5-minute average heart rate within [start]..[end]. A rolling average rather than
     * the single lowest reading, so one dropped or glitchy sample can't set the number.
     * Null with too few samples to trust.
     */
    fun lowestSustainedBpm(samples: List<HeartRateSampleEntity>, start: Instant, end: Instant, minSamples: Int = MIN_SLEEP_SAMPLES): Double? {
        val inWindow = samples.filter { it.bpm >= MIN_BPM && !it.timestamp.isBefore(start) && it.timestamp.isBefore(end) }
            .sortedBy { it.timestamp }
        if (inWindow.size < minSamples) return null
        var best: Double? = null
        var from = 0
        var sum = 0.0
        for (to in inWindow.indices) {
            sum += inWindow[to].bpm
            while (Duration.between(inWindow[from].timestamp, inWindow[to].timestamp) > WINDOW) {
                sum -= inWindow[from].bpm
                from++
            }
            val count = to - from + 1
            // Need a few readings in the window so sparse data doesn't reduce to a single sample.
            if (count >= 3 || inWindow.size < 30) {
                val mean = sum / count
                if (best == null || mean < best) best = mean
            }
        }
        return best
    }

    /**
     * The night's HRV: the average of readings taken during sleep (+/- 30 minutes for devices
     * that stamp the reading on waking). Otherwise the day's first reading, which is usually the
     * morning one.
     */
    fun nightlyHrv(readings: List<HrvMeasurementEntity>, sleepStart: Instant?, sleepEnd: Instant?): Double? {
        val valid = readings.filter { it.rmssd in 5.0..300.0 && it.dataQualityState == "VALID" }
        if (valid.isEmpty()) return null
        if (sleepStart != null && sleepEnd != null) {
            val overnight = valid.filter {
                !it.timestamp.isBefore(sleepStart.minus(Duration.ofMinutes(30))) &&
                    !it.timestamp.isAfter(sleepEnd.plus(Duration.ofMinutes(30)))
            }
            if (overnight.isNotEmpty()) return overnight.map { it.rmssd }.average()
        }
        return valid.minByOrNull { it.timestamp }?.rmssd
    }
}
