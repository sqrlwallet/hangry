package com.kevan.hangry

import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeightTrendTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 12)

    private fun weighIn(daysAgo: Long, kg: Double, hour: Int = 7) = WeightMeasurementEntity(
        recordFingerprint = "w$daysAgo-$hour-$kg",
        timestamp = today.minusDays(daysAgo).atTime(hour, 0).toInstant(zone),
        weightKg = kg
    )

    @Test
    fun firstWeighInStartsTheTrendAndEachOneMovesItTenPercent() {
        val points = WeightTrend.ewma(listOf(today.minusDays(1) to 80.0, today to 81.0))
        assertEquals(80.0, points[0].trendKg, 1e-9)
        assertEquals(80.1, points[1].trendKg, 1e-9)
        assertEquals(81.0, points[1].weighInKg, 1e-9)
    }

    @Test
    fun aGapLetsTheNextWeighInPullFurther() {
        // Three days apart: as if the two missing days had read 81 too: 1 - 0.9^3 = 0.271.
        val points = WeightTrend.ewma(listOf(today.minusDays(3) to 80.0, today to 81.0))
        assertEquals(80.271, points[1].trendKg, 1e-9)
    }

    @Test
    fun severalWeighInsOnOneDayAreAveraged() {
        val daily = WeightTrend.dailyWeights(listOf(weighIn(0, 80.0, 7), weighIn(0, 81.0, 20), weighIn(1, 79.0)), zone)
        assertEquals(listOf(today.minusDays(1) to 79.0, today to 80.5), daily)
    }

    @Test
    fun trendSmoothsNoiseAroundASteadyWeight() {
        val noisy = (0L until 30L).map { weighIn(it, if (it % 2 == 0L) 79.0 else 81.0) }
        val summary = WeightTrend.summarize(noisy, today, zone)
        assertEquals(80.0, summary.trendKg!!, 0.2)
        assertEquals(0.0, summary.weeklyChangeKg!!, 0.05)
    }

    @Test
    fun weeklyRateIsTheLeastSquaresSlopeOfTheLastFourWeeks() {
        // Losing 0.1 kg a day = 0.7 kg a week; older weigh-ins outside the window don't count.
        val weights = (0L..27L).map { weighIn(it, 80.0 + it * 0.1) } + weighIn(60, 120.0)
        val summary = WeightTrend.summarize(weights, today, zone)
        assertEquals(-0.7, summary.weeklyChangeKg!!, 1e-6)
    }

    @Test
    fun noRateUntilThereAreEnoughWeighInsSpreadOverAWeek() {
        assertNull(WeightTrend.summarize(listOf(weighIn(0, 80.0), weighIn(1, 80.5)), today, zone).weeklyChangeKg)
        // Three weigh-ins, but all within 3 days.
        assertNull(WeightTrend.summarize(listOf(weighIn(0, 80.0), weighIn(1, 80.5), weighIn(3, 81.0)), today, zone).weeklyChangeKg)
        assertEquals(
            -0.7,
            WeightTrend.summarize(listOf(weighIn(0, 80.0), weighIn(5, 80.5), weighIn(10, 81.0)), today, zone).weeklyChangeKg!!,
            1e-6
        )
    }

    @Test
    fun noWeighInsMeansNoTrend() {
        val summary = WeightTrend.summarize(emptyList(), today, zone)
        assertNull(summary.trendKg)
        assertNull(summary.weeklyChangeKg)
        assertEquals(emptyList<WeightTrend.TrendPoint>(), summary.points)
    }
}
