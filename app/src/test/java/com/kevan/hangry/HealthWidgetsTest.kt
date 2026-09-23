package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.domain.model.CycleStats
import com.kevan.hangry.ui.navigation.Screen
import com.kevan.hangry.ui.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HealthWidgetsTest {

    private val today = LocalDate.of(2026, 9, 23)

    private fun summary(daysAgo: Long, rhr: Double? = null, hrv: Double? = null) =
        DailyHealthSummaryEntity(date = today.minusDays(daysAgo), restingHeartRate = rhr, hrvRmssd = hrv)

    // region Heart

    @Test
    fun heartReading_comparesNewestValueWithPrior28DayAverage() {
        val summaries = listOf(summary(0, rhr = 55.0)) + (1L..10L).map { summary(it, rhr = 60.0) }
        val reading = WidgetText.heartReading(summaries, today) { it.restingHeartRate }!!
        assertEquals(55.0, reading.value, 0.0)
        assertEquals(-5.0, reading.delta!!, 1e-9)
        assertEquals("▼ 5", WidgetText.deltaText(reading.delta))
        // Lower resting heart rate is good news.
        assertEquals(true, WidgetText.isImprovement(reading.delta, higherIsBetter = false))
    }

    @Test
    fun heartReading_ignoresValuesOlderThanThreeDays() {
        val summaries = listOf(summary(3, hrv = 40.0))
        assertNull(WidgetText.heartReading(summaries, today) { it.hrvRmssd })
    }

    @Test
    fun heartReading_hasNoBaselineWithFewerThanFiveDaysOfHistory() {
        val summaries = listOf(summary(0, hrv = 50.0)) + (1L..4L).map { summary(it, hrv = 45.0) }
        val reading = WidgetText.heartReading(summaries, today) { it.hrvRmssd }!!
        assertNull(reading.baseline)
        assertNull(WidgetText.deltaText(reading.delta))
    }

    @Test
    fun deltaText_callsSmallChangesSteady() {
        assertEquals("steady", WidgetText.deltaText(0.6))
        assertEquals("▲ 4", WidgetText.deltaText(3.7))
        assertNull(WidgetText.isImprovement(0.6, higherIsBetter = true))
    }

    // endregion

    // region Cycle

    private fun stats(
        lastStart: LocalDate? = today.minusDays(11),
        next: LocalDate? = today.plusDays(17),
        inPeriod: Boolean = false,
        averagePeriod: Double? = 5.0
    ) = CycleStats(
        periodCount = 3,
        averageCycleDays = 28.0,
        averagePeriodDays = averagePeriod,
        lastPeriodStart = lastStart,
        predictedNextStart = next,
        currentCycleDay = lastStart?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt() + 1 },
        inPeriodNow = inPeriod
    )

    @Test
    fun cycleText_showsDayAndCountdownToNextPeriod() {
        val text = WidgetText.cycleText(stats(), today)
        assertEquals("Day 12", text.headline)
        assertEquals("Next period in ~17 days", text.subtitle)
        assertEquals("", text.badge)
    }

    @Test
    fun cycleText_flagsAPeriodDueWithinThreeDays() {
        val text = WidgetText.cycleText(stats(next = today.plusDays(2)), today)
        assertEquals("SOON", text.badge)
        assertEquals("Next period in ~2 days", text.subtitle)
    }

    @Test
    fun cycleText_duringPeriod() {
        val text = WidgetText.cycleText(stats(lastStart = today.minusDays(2), inPeriod = true), today)
        assertEquals("Period · day 3", text.headline)
        assertEquals("PERIOD", text.badge)
    }

    @Test
    fun cycleText_latePeriodAndNoData() {
        assertEquals("Period expected 1 day ago", WidgetText.cycleText(stats(next = today.minusDays(1)), today).subtitle)
        assertEquals("Predictions start after two periods", WidgetText.cycleText(stats(next = null), today).subtitle)
        assertEquals("Log your period in Health Records", WidgetText.cycleText(stats(lastStart = null), today).subtitle)
    }

    // endregion

    @Test
    fun postureBadgeAndDaysAgo() {
        assertEquals("GREAT", WidgetText.postureBadge(90))
        assertEquals("GOOD", WidgetText.postureBadge(70))
        assertEquals("FAIR", WidgetText.postureBadge(55))
        assertEquals("NEEDS WORK", WidgetText.postureBadge(54))
        assertEquals("today", WidgetText.daysAgoText(today, today))
        assertEquals("yesterday", WidgetText.daysAgoText(today.minusDays(1), today))
        assertEquals("16 days ago", WidgetText.daysAgoText(today.minusDays(16), today))
    }

    @Test
    fun breathingRoute_widgetStartFlag() {
        assertEquals("breathing", Screen.Breathing.createRoute())
        assertEquals("breathing?pattern=bpm_6", Screen.Breathing.createRoute("bpm_6"))
        assertEquals("breathing?pattern=box_4&start=true", Screen.Breathing.createRoute("box_4", start = true))
    }
}
