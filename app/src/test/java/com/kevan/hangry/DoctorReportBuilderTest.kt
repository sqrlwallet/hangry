package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MenstrualPeriod
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.report.DoctorReportBuilder
import com.kevan.hangry.domain.report.DoctorReportInputs
import com.kevan.hangry.domain.report.ReportOptions
import com.kevan.hangry.domain.report.ReportPeriod
import com.kevan.hangry.domain.report.ReportSection
import com.kevan.hangry.domain.report.TrendPoint
import com.kevan.hangry.domain.report.VitalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DoctorReportBuilderTest {

    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 22, 9, 30, 0, 0, zone)
    private val today = now.toLocalDate()

    @Test
    fun vitalsAverageOnlyDaysWithDataAndCompareWithThePeriodBefore() {
        val summaries = (0 until 60).map { back ->
            val date = today.minusDays(back.toLong())
            // Last 30 days: 55 bpm, every other day missing. The 30 before: 60 bpm.
            val rhr = if (back < 30) (if (back % 2 == 0) 55.0 else null) else 60.0
            DailyHealthSummaryEntity(date = date, restingHeartRate = rhr)
        }
        val report = DoctorReportBuilder.build(inputs(ReportPeriod.DAYS_30, summaries = summaries))
        val rhr = report.vitals.single { it.kind == VitalKind.RESTING_HEART_RATE }
        assertEquals(55.0, rhr.average, 0.001)
        assertEquals(15, rhr.daysWithData)
        assertEquals(60.0, rhr.previousAverage!!, 0.001)
        assertEquals(today.minusDays(29), report.start)
    }

    @Test
    fun missingVitalsAreLeftOutNotShownAsZero() {
        val summaries = listOf(DailyHealthSummaryEntity(date = today, restingHeartRate = 58.0))
        val report = DoctorReportBuilder.build(inputs(summaries = summaries))
        assertEquals(listOf(VitalKind.RESTING_HEART_RATE), report.vitals.map { it.kind })
        assertTrue(report.trends.isEmpty())
    }

    @Test
    fun todaysStepsAreLeftOutOfTheAverage() {
        val summaries = listOf(
            DailyHealthSummaryEntity(date = today, steps = 500),
            DailyHealthSummaryEntity(date = today.minusDays(1), steps = 9000),
            DailyHealthSummaryEntity(date = today.minusDays(2), steps = 7000)
        )
        val report = DoctorReportBuilder.build(inputs(summaries = summaries))
        assertEquals(8000.0, report.vitals.single { it.kind == VitalKind.STEPS }.average, 0.001)
        assertEquals(8000.0, report.activity!!.averageSteps!!, 0.001)
    }

    @Test
    fun longPeriodsChartWeeklyAveragesAndAMonthChartsDays() {
        val summaries = (0 until 90).map { DailyHealthSummaryEntity(date = today.minusDays(it.toLong()), hrvRmssd = 40.0 + it % 7) }
        val quarter = DoctorReportBuilder.build(inputs(ReportPeriod.DAYS_90, summaries = summaries))
        val weekly = quarter.trends.single { it.kind == VitalKind.HRV }
        assertTrue(weekly.weekly)
        assertTrue(weekly.points.size in 13..14)

        val month = DoctorReportBuilder.build(inputs(ReportPeriod.DAYS_30, summaries = summaries))
        val daily = month.trends.single { it.kind == VitalKind.HRV }
        assertEquals(30, daily.points.size)
    }

    @Test
    fun weeklyAveragesStartOnMonday() {
        val monday = LocalDate.of(2026, 10, 19)
        val points = listOf(
            TrendPoint(monday.minusDays(1), 10.0), // Sunday - previous week
            TrendPoint(monday, 20.0),
            TrendPoint(monday.plusDays(2), 30.0)
        )
        val weeks = DoctorReportBuilder.weeklyAverages(points)
        assertEquals(listOf(monday.minusDays(7), monday), weeks.map { it.date })
        assertEquals(25.0, weeks.last().value, 0.001)
    }

    @Test
    fun occasionalReadingsKeepEveryWeekButDailyDataDropsThinWeeks() {
        val start = LocalDate.of(2026, 1, 5) // a Monday
        val everySixDays = (0 until 60).map { TrendPoint(start.plusDays(it * 6L), 130.0) }
        assertEquals(everySixDays.map { it.date.with(java.time.DayOfWeek.MONDAY) }.distinct().size, DoctorReportBuilder.weeklyAverages(everySixDays).size)

        // Daily data with one week where the watch was off but for a single day.
        val daily = (0 until 28).filter { it !in 8..13 }.map { TrendPoint(start.plusDays(it.toLong()), 60.0) }
        assertEquals(3, DoctorReportBuilder.weeklyAverages(daily).size)
    }

    @Test
    fun bedtimesAveragedAcrossMidnight() {
        val mean = DoctorReportBuilder.circularMean(listOf(LocalTime.of(23, 30), LocalTime.of(0, 30)))
        assertEquals(LocalTime.MIDNIGHT, mean)
    }

    @Test
    fun sleepSummaryCountsShortNightsAndTypicalTimes() {
        val summaries = (0 until 4).map { back ->
            val date = today.minusDays(back.toLong())
            val minutes = if (back == 0) 300 else 450
            val end = date.atTime(7, 0).atZone(zone).toInstant()
            DailyHealthSummaryEntity(
                date = date,
                sleepDurationMinutes = minutes,
                sleepStartTime = end.minusSeconds(minutes * 60L),
                sleepEndTime = end
            )
        }
        val sleep = DoctorReportBuilder.build(inputs(summaries = summaries)).sleep!!
        assertEquals(4, sleep.nightsRecorded)
        assertEquals(1, sleep.nightsUnder6h)
        assertEquals(300, sleep.shortestMinutes)
        assertEquals(LocalTime.of(7, 0), sleep.typicalWakeTime)
    }

    @Test
    fun latestLabResultIsKeptEvenWhenOlderThanThePeriod() {
        val hba1c = reading(1, MarkerType.HBA1C, 6.1, date = today.minusDays(200))
        val report = DoctorReportBuilder.build(inputs(records = HealthRecordsSnapshot(readings = listOf(hba1c))))
        val summary = report.markers.single()
        assertEquals(MarkerType.HBA1C, summary.type)
        assertEquals("Prediabetes range", summary.category)
        assertEquals(MetricTone.CAUTION, summary.tone)
        assertEquals(0, summary.readingsInPeriod)
        assertTrue(report.readings.isEmpty())
    }

    @Test
    fun bloodPressureBecomesAVitalAndKeepsTheClinicalCategory() {
        val readings = listOf(
            reading(1, MarkerType.BLOOD_PRESSURE, 185.0, 95.0, today.minusDays(1)),
            reading(2, MarkerType.BLOOD_PRESSURE, 125.0, 79.0, today.minusDays(5))
        )
        val report = DoctorReportBuilder.build(inputs(records = HealthRecordsSnapshot(readings = readings)))
        val bp = report.vitals.single { it.kind == VitalKind.BLOOD_PRESSURE }
        assertEquals(155.0, bp.average, 0.001)
        assertEquals(87.0, bp.secondaryAverage!!, 0.001)
        assertEquals(185.0, bp.latest, 0.001)
        val marker = report.markers.single()
        // The app's own "seek care" hint is meant for the user, not the clinician.
        assertEquals("Very high", marker.category)
        assertEquals(MetricTone.RISK, marker.tone)
        assertEquals(2, report.readings.size)
    }

    @Test
    fun glucoseRangeFollowsWhenItWasMeasured() {
        val fasting = reading(1, MarkerType.BLOOD_GLUCOSE, 110.0, date = today, context = GlucoseContext.FASTING)
        val afterMeal = fasting.copy(glucoseContext = GlucoseContext.AFTER_MEAL)
        assertEquals("Prediabetes range", DoctorReportBuilder.category(fasting, null)?.first)
        assertEquals("Normal", DoctorReportBuilder.category(afterMeal, null)?.first)
    }

    @Test
    fun leftOutSectionsAreEmpty() {
        val summaries = listOf(DailyHealthSummaryEntity(date = today, restingHeartRate = 58.0, sleepDurationMinutes = 420))
        val options = ReportOptions(sections = setOf(ReportSection.CONDITIONS))
        val records = HealthRecordsSnapshot(
            readings = listOf(reading(1, MarkerType.LDL, 120.0, date = today)),
            profileItems = listOf(HealthProfileItem(1, HealthProfileKind.ALLERGY, "Penicillin", null, RecordSource.MANUAL))
        )
        val report = DoctorReportBuilder.build(inputs(options = options, summaries = summaries, records = records))
        assertTrue(report.vitals.isEmpty())
        assertTrue(report.trends.isEmpty())
        assertTrue(report.markers.isEmpty())
        assertNull(report.sleep)
        assertEquals("Penicillin", report.allergies.single().name)
    }

    @Test
    fun nutritionAveragesLoggedDaysAndSkipsToday() {
        val food = listOf(
            food(today, 400),
            food(today.minusDays(1), 1000),
            food(today.minusDays(1), 1000),
            food(today.minusDays(3), 1600)
        )
        val nutrition = DoctorReportBuilder.build(inputs(food = food)).nutrition!!
        assertEquals(2, nutrition.daysLogged)
        assertEquals(1800.0, nutrition.averageCalories, 0.001)
    }

    @Test
    fun patientDetailsIncludeBmiFromLatestWeight() {
        val profile = UserProfileEntity(heightCm = 180.0, age = 36, biologicalSex = "MALE")
        val weights = listOf(
            weight(today.minusDays(10), 85.0),
            weight(today.minusDays(1), 81.0)
        )
        val report = DoctorReportBuilder.build(inputs(profile = profile, weights = weights))
        assertEquals(81.0, report.patient.weightKg!!, 0.001)
        assertEquals(25.0, report.patient.bmi!!, 0.01)
        assertEquals(BiologicalSex.MALE, report.patient.sex)
        val weightStat = report.vitals.single { it.kind == VitalKind.WEIGHT }
        assertEquals(81.0, weightStat.latest, 0.001)
    }

    @Test
    fun cycleOnlyForWomenWithPeriodsLogged() {
        val periods = listOf(
            MenstrualPeriod(1, today.minusDays(60), today.minusDays(56), RecordSource.MANUAL),
            MenstrualPeriod(2, today.minusDays(32), today.minusDays(28), RecordSource.MANUAL),
            MenstrualPeriod(3, today.minusDays(4), today.minusDays(1), RecordSource.MANUAL)
        )
        val female = DoctorReportBuilder.build(inputs(records = HealthRecordsSnapshot(periods = periods, sex = BiologicalSex.FEMALE)))
        val cycle = assertNotNullAndGet(female.cycle)
        assertEquals(28.0, cycle.stats.averageCycleDays!!, 0.001)
        assertEquals(3, cycle.startsInPeriod.size)

        val male = DoctorReportBuilder.build(inputs(records = HealthRecordsSnapshot(periods = periods, sex = BiologicalSex.MALE)))
        assertNull(male.cycle)
    }

    @Test
    fun supplementsShowDoseScheduleAndAdherenceWhenTracked() {
        val vitaminD = Supplement(
            id = 7, name = "Vitamin D3", brand = "Acme", form = "softgel", doseAmount = 1.0, doseUnit = "softgel",
            times = listOf(LocalTime.of(8, 0)), ingredients = listOf(SupplementIngredient("Vitamin D3", 25.0, "mcg")),
            remindersEnabled = true, tracked = true, notes = null, photoPath = null, active = true
        )
        val retired = vitaminD.copy(id = 8, name = "Iron", active = false)
        val snapshot = SupplementsSnapshot(supplements = listOf(vitaminD, retired), weekAdherence = mapOf(7L to (6 to 7)))
        val supplement = DoctorReportBuilder.build(inputs(supplements = snapshot)).supplements.single()
        assertEquals("1 softgel", supplement.dose)
        assertEquals(6 to 7, supplement.adherence)
        assertEquals("Vitamin D3 25 mcg", supplement.ingredients)
    }

    @Test
    fun workoutsSummariseByType() {
        val workouts = listOf(
            workout("RUNNING", 30, today.minusDays(2)),
            workout("RUNNING", 40, today.minusDays(9)),
            workout("YOGA", 60, today.minusDays(4))
        )
        val activity = DoctorReportBuilder.build(inputs(ReportPeriod.DAYS_30, workouts = workouts)).activity!!
        assertEquals(3, activity.workouts)
        assertEquals(70, activity.topWorkouts.first().minutes)
        assertEquals(2, activity.topWorkouts.first().sessions)
        assertEquals(130 / (30 / 7.0), activity.workoutMinutesPerWeek, 0.001)
    }

    @Test
    fun amountsDropTrailingZeros() {
        assertEquals("2", DoctorReportBuilder.formatAmount(2.0))
        assertEquals("0.5", DoctorReportBuilder.formatAmount(0.5))
        assertEquals("1.25", DoctorReportBuilder.formatAmount(1.25))
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private fun inputs(
        period: ReportPeriod = ReportPeriod.DAYS_90,
        options: ReportOptions = ReportOptions(period = period),
        profile: UserProfileEntity? = null,
        summaries: List<DailyHealthSummaryEntity> = emptyList(),
        weights: List<WeightMeasurementEntity> = emptyList(),
        records: HealthRecordsSnapshot = HealthRecordsSnapshot(),
        supplements: SupplementsSnapshot = SupplementsSnapshot(),
        workouts: List<ExerciseSessionEntity> = emptyList(),
        food: List<FoodLogEntity> = emptyList()
    ) = DoctorReportInputs(
        now = now,
        options = options,
        profile = profile,
        summaries = summaries,
        weights = weights,
        records = records,
        supplements = supplements,
        workouts = workouts,
        food = food,
        latestBodyFatPercent = null,
        dataSources = emptyList()
    )

    private fun reading(
        id: Long,
        type: MarkerType,
        value: Double,
        secondary: Double? = null,
        date: LocalDate,
        context: GlucoseContext? = null
    ) = MarkerReading(
        id = id, type = type, value = value, secondaryValue = secondary,
        measuredAt = date.atTime(8, 0).atZone(zone).toInstant(), date = date,
        glucoseContext = context, source = RecordSource.MANUAL, note = null
    )

    private fun food(date: LocalDate, calories: Int) =
        FoodLogEntity(date = date, source = "MANUAL", foodName = "Meal", calories = calories)

    private fun weight(date: LocalDate, kg: Double) = WeightMeasurementEntity(
        recordFingerprint = "$date-$kg", timestamp = date.atTime(7, 0).atZone(zone).toInstant(), weightKg = kg
    )

    private fun workout(type: String, minutes: Int, date: LocalDate): ExerciseSessionEntity {
        val start = date.atTime(18, 0).atZone(zone).toInstant()
        return ExerciseSessionEntity(
            recordFingerprint = "$type-$date", exerciseType = type, startTime = start,
            endTime = start.plusSeconds(minutes * 60L), durationMinutes = minutes
        )
    }
}
