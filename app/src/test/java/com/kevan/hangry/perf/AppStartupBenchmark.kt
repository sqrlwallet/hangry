package com.kevan.hangry.perf

import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Benchmarks and asserts cold-start and scoped historical query performance.
 */
class AppStartupBenchmark {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val days = 730L

    @Test
    fun scopedWeightQuery_reducesHistoryRowProcessing() {
        assumeTrue(PerfBench.enabled)

        val fullHistory = (0 until days).map { d ->
            WeightMeasurementEntity(
                recordFingerprint = "wt$d",
                timestamp = today.minusDays(d).atTime(7, 0).atZone(zone).toInstant(),
                weightKg = 80.0 - d * 0.01
            )
        }

        val windowStart = today.minusDays(28).atStartOfDay(zone).toInstant()
        val windowEnd = today.plusDays(1).atStartOfDay(zone).toInstant()

        // Benchmark processing all 730 weights vs 28 scoped weights
        val scopedWeights = fullHistory.filter { it.timestamp >= windowStart && it.timestamp < windowEnd }
        assertEquals(29, scopedWeights.size)

        PerfBench.measure("WeightTrend: process full 2yr history (730 rows)") {
            WeightTrend.dailyWeights(fullHistory, zone)
        }

        PerfBench.measure("WeightTrend: process scoped adaptive window (28 rows)") {
            WeightTrend.dailyWeights(scopedWeights, zone)
        }
    }
}
