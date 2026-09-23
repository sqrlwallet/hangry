package com.kevan.hangry

import com.kevan.hangry.ui.components.TrendPoint
import com.kevan.hangry.ui.trends.TrendTimeframe
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrendsAnalyticsTest {

    @Test
    fun testTrendTimeframe_dayOffsets() {
        val today = LocalDate.of(2026, 9, 23)
        assertEquals(7L, TrendTimeframe.DAYS_7.days)
        assertEquals(14L, TrendTimeframe.DAYS_14.days)
        assertEquals(30L, TrendTimeframe.DAYS_30.days)
        assertEquals(90L, TrendTimeframe.DAYS_90.days)
        assertNull(TrendTimeframe.ALL_TIME.days)

        val start7 = today.minusDays(TrendTimeframe.DAYS_7.days!!)
        assertEquals(LocalDate.of(2026, 9, 16), start7)
    }

    @Test
    fun testTrendPoints_metricsComputation() {
        val dates = listOf(
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 21),
            LocalDate.of(2026, 9, 22),
            LocalDate.of(2026, 9, 23)
        )
        val points = listOf(
            TrendPoint(dates[0], 65.0),
            TrendPoint(dates[1], 80.0),
            TrendPoint(dates[2], null), // gap day
            TrendPoint(dates[3], 95.0)
        )

        val validPoints = points.filter { it.value != null }
        assertEquals(3, validPoints.size)

        val values = validPoints.mapNotNull { it.value }
        val min = values.minOrNull()
        val max = values.maxOrNull()
        val avg = values.average()

        assertEquals(65.0, min!!, 0.001)
        assertEquals(95.0, max!!, 0.001)
        assertEquals(80.0, avg, 0.001)

        val peakPoint = validPoints.maxByOrNull { it.value!! }
        assertEquals(dates[3], peakPoint?.date)

        val lowPoint = validPoints.minByOrNull { it.value!! }
        assertEquals(dates[0], lowPoint?.date)
    }

    @Test
    fun testMacroSplit_percentageCalculations() {
        val proteinG = 150.0 // 600 kcal
        val carbsG = 200.0   // 800 kcal
        val fatG = 50.0      // 450 kcal

        val totalGrams = proteinG + carbsG + fatG
        assertEquals(400.0, totalGrams, 0.001)

        val proteinPct = ((proteinG / totalGrams) * 100).toInt()
        val carbsPct = ((carbsG / totalGrams) * 100).toInt()
        val fatPct = 100 - proteinPct - carbsPct

        assertEquals(37, proteinPct)
        assertEquals(50, carbsPct)
        assertEquals(13, fatPct)
        assertEquals(100, proteinPct + carbsPct + fatPct)
    }

    @Test
    fun testHistoryFilter_categorization() {
        data class DayRecord(
            val date: LocalDate,
            val recoveryScore: Int?,
            val workoutCount: Int,
            val mealCount: Int
        )

        val records = listOf(
            DayRecord(LocalDate.of(2026, 9, 20), recoveryScore = 78, workoutCount = 1, mealCount = 3),
            DayRecord(LocalDate.of(2026, 9, 21), recoveryScore = 30, workoutCount = 0, mealCount = 2),
            DayRecord(LocalDate.of(2026, 9, 22), recoveryScore = 55, workoutCount = 2, mealCount = 0),
            DayRecord(LocalDate.of(2026, 9, 23), recoveryScore = null, workoutCount = 0, mealCount = 0)
        )

        val primedDays = records.filter { (it.recoveryScore ?: 0) >= 67 }
        assertEquals(1, primedDays.size)
        assertEquals(LocalDate.of(2026, 9, 20), primedDays.first().date)

        val rebuildDays = records.filter { it.recoveryScore != null && it.recoveryScore < 34 }
        assertEquals(1, rebuildDays.size)
        assertEquals(LocalDate.of(2026, 9, 21), rebuildDays.first().date)

        val workoutDays = records.filter { it.workoutCount > 0 }
        assertEquals(2, workoutDays.size)

        val nutritionDays = records.filter { it.mealCount > 0 }
        assertEquals(2, nutritionDays.size)
    }
}
