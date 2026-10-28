package com.kevan.hangry

import com.kevan.hangry.domain.calculation.BodyAgeCalculator
import com.kevan.hangry.domain.calculation.CardioFitness
import com.kevan.hangry.domain.calculation.HeartRateRecovery
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.NudgeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CardioFitnessTest {

    // region Heart-rate recovery

    /** Max 190, resting 60: a hard enough end is 70% of reserve, 151 bpm. */
    private val zones = HeartRateZones(maxHr = 190.0, restingHr = 60.0)
    private val end = Instant.parse("2026-10-27T18:00:00Z")
    private fun at(seconds: Long, bpm: Double) = end.plusSeconds(seconds) to bpm

    @Test
    fun dropsAreMeasuredFromTheEndPeak() {
        val r = HeartRateRecovery.calculate(end, listOf(at(-20, 168.0), at(-5, 172.0), at(58, 146.0), at(121, 128.0)), zones)!!
        assertEquals(172, r.endBpm)
        assertEquals(26, r.oneMinuteDrop)
        assertEquals(44, r.twoMinuteDrop)
        assertEquals(HeartRateRecovery.Rating.STRONG, r.rating)
    }

    @Test
    fun theReadingClosestToEachMarkIsUsed() {
        val r = HeartRateRecovery.calculate(end, listOf(at(0, 160.0), at(48, 150.0), at(62, 152.0), at(74, 140.0)), zones)!!
        assertEquals(8, r.oneMinuteDrop)
        assertNull(r.twoMinuteDrop)
        assertEquals(HeartRateRecovery.Rating.LOW, r.rating)
    }

    @Test
    fun onlyTheTwoMinuteMarkIsEnoughToRate() {
        val r = HeartRateRecovery.calculate(end, listOf(at(-1, 165.0), at(118, 135.0)), zones)!!
        assertNull(r.oneMinuteDrop)
        assertEquals(30, r.twoMinuteDrop)
        assertEquals(HeartRateRecovery.Rating.TYPICAL, r.rating)
    }

    @Test
    fun anEasyWorkoutHasNothingToMeasure() {
        assertNull(HeartRateRecovery.calculate(end, listOf(at(-5, 130.0), at(60, 110.0)), zones))
    }

    @Test
    fun noReadingsAfterTheEndHasNothingToMeasure() {
        // The watch stopped recording with the workout, as many do.
        assertNull(HeartRateRecovery.calculate(end, listOf(at(-10, 170.0), at(-2, 171.0)), zones))
        assertNull(HeartRateRecovery.calculate(end, listOf(at(30, 150.0), at(90, 140.0)), zones))
    }

    @Test
    fun ratingsFollowTheResearchCutOffs() {
        assertEquals(HeartRateRecovery.Rating.LOW, HeartRateRecovery.rateOneMinute(12))
        assertEquals(HeartRateRecovery.Rating.TYPICAL, HeartRateRecovery.rateOneMinute(13))
        assertEquals(HeartRateRecovery.Rating.STRONG, HeartRateRecovery.rateOneMinute(25))
        assertEquals(HeartRateRecovery.Rating.LOW, HeartRateRecovery.rateTwoMinutes(22))
        assertEquals(HeartRateRecovery.Rating.TYPICAL, HeartRateRecovery.rateTwoMinutes(23))
    }

    // endregion

    // region VO2 max levels and alerts

    private val today = LocalDate.of(2026, 10, 28)

    /** Every day from [daysAgo] to today at [value]; later calls overwrite earlier ones. */
    private fun MutableMap<LocalDate, Double>.fill(fromDaysAgo: Long, toDaysAgo: Long, value: Double) = apply {
        (toDaysAgo..fromDaysAgo).forEach { put(today.minusDays(it), value) }
    }

    @Test
    fun levelsAreQuartilesAroundTheTypicalValue() {
        val typical = BodyAgeCalculator.vo2Norm(45.0, BiologicalSex.MALE) // 37
        val r = CardioFitness.ranges(45.0, BiologicalSex.MALE)
        assertEquals(typical, r.typical, 0.001)
        assertEquals(typical * 0.83, r.lowBelow, 0.001)
        assertEquals(CardioFitness.Level.LOW, CardioFitness.level(30.0, 45.0, BiologicalSex.MALE))
        assertEquals(CardioFitness.Level.BELOW_AVERAGE, CardioFitness.level(33.0, 45.0, BiologicalSex.MALE))
        assertEquals(CardioFitness.Level.ABOVE_AVERAGE, CardioFitness.level(40.0, 45.0, BiologicalSex.MALE))
        assertEquals(CardioFitness.Level.HIGH, CardioFitness.level(44.0, 45.0, BiologicalSex.MALE))
    }

    @Test
    fun lowMustLastTwoWeeksBeforeAnAlert() {
        // One sudden low estimate isn't a trend, low or falling.
        val justDropped = mutableMapOf<LocalDate, Double>().fill(120, 0, 36.0).fill(5, 0, 29.0)
        assertNull(CardioFitness.alertDue(justDropped, today, 45.0, BiologicalSex.MALE))
        val stayedLow = mutableMapOf<LocalDate, Double>().fill(120, 0, 29.0)
        assertEquals(CardioFitness.Alert.LOW, CardioFitness.alertDue(stayedLow, today, 45.0, BiologicalSex.MALE))
    }

    @Test
    fun anAlertIsNotRepeatedWithinThreeMonths() {
        val stayedLow = mutableMapOf<LocalDate, Double>().fill(120, 0, 29.0)
        val sentRecently = mapOf(CardioFitness.Alert.LOW to today.minusDays(30))
        assertNull(CardioFitness.alertDue(stayedLow, today, 45.0, BiologicalSex.MALE, sentRecently))
        val sentLongAgo = mapOf(CardioFitness.Alert.LOW to today.minusDays(91))
        assertEquals(CardioFitness.Alert.LOW, CardioFitness.alertDue(stayedLow, today, 45.0, BiologicalSex.MALE, sentLongAgo))
    }

    @Test
    fun aFallOfOneMetOverThreeMonthsIsFlagged() {
        val falling = mutableMapOf<LocalDate, Double>().fill(120, 0, 44.0).fill(30, 0, 40.0)
        assertEquals(-4.0, CardioFitness.change(falling, today)!!, 0.001)
        assertEquals(CardioFitness.Alert.DECLINING, CardioFitness.alertDue(falling, today, 45.0, BiologicalSex.MALE))
        // Small moves are normal noise in watch estimates.
        val wobble = mutableMapOf<LocalDate, Double>().fill(120, 0, 44.0).fill(30, 0, 42.0)
        assertNull(CardioFitness.alertDue(wobble, today, 45.0, BiologicalSex.MALE))
    }

    @Test
    fun withoutAnAgeOnlyAFallCanBeJudged() {
        val stayedLow = mutableMapOf<LocalDate, Double>().fill(120, 0, 20.0)
        assertNull(CardioFitness.alertDue(stayedLow, today, null, null))
        val falling = mutableMapOf<LocalDate, Double>().fill(120, 0, 30.0).fill(30, 0, 25.0)
        assertEquals(CardioFitness.Alert.DECLINING, CardioFitness.alertDue(falling, today, null, null))
    }

    @Test
    fun noRecentReadingMeansNoAlert() {
        assertNull(CardioFitness.alertDue(emptyMap(), today, 45.0, BiologicalSex.MALE))
        val stale = mutableMapOf<LocalDate, Double>().fill(120, 20, 25.0)
        assertNull(CardioFitness.valueOn(stale, today))
    }

    @Test
    fun alertTextsGiveTheNumbers() {
        assertTrue(NudgeText.cardioLow(29.04, 30.7).body.startsWith("Your VO2 max is 29.0, below 31, the low line"))
        assertTrue(NudgeText.cardioDeclining(40.0, 4.0).body.startsWith("Your VO2 max is down 4.0 to 40.0"))
    }

    // endregion
}
