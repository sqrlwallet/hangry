package com.kevan.hangry.domain.report

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.WorkoutText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** What a doctor report is built from; the loader reads it, [DoctorReportBuilder] does the maths. */
data class DoctorReportInputs(
    val now: ZonedDateTime,
    val options: ReportOptions,
    val profile: UserProfileEntity?,
    /** Daily summaries covering the period and the same-length period before it. */
    val summaries: List<DailyHealthSummaryEntity>,
    /** Weights up to the end of the period (earlier ones give the latest weight when the period has none). */
    val weights: List<WeightMeasurementEntity>,
    val records: HealthRecordsSnapshot,
    val supplements: SupplementsSnapshot,
    /** Workouts in the period. */
    val workouts: List<ExerciseSessionEntity>,
    /** Food logged in the period. */
    val food: List<FoodLogEntity>,
    val latestBodyFatPercent: Double?,
    val dataSources: List<String>
)

/**
 * Turns stored health data into the numbers a clinician reads: averages, ranges, the period
 * before for comparison, and trends. Pure - no Android, no I/O - so it is unit tested directly.
 * Missing data stays missing: a vital nobody recorded is left out rather than shown as zero.
 */
object DoctorReportBuilder {

    /** Weekly averages need a few days each to mean anything. */
    private const val MIN_DAYS_PER_WEEK = 2
    /** A series averaging this many days a week counts as daily data. */
    private const val DENSE_DAYS_PER_WEEK = 4
    private const val TOP_WORKOUT_TYPES = 5

    fun build(input: DoctorReportInputs): DoctorReport {
        val zone = input.now.zone
        val options = input.options
        val end = input.now.toLocalDate()
        val start = end.minusDays(options.period.days - 1L)
        val previousStart = start.minusDays(options.period.days.toLong())
        val inPeriod = input.summaries.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val previous = input.summaries.filter { !it.date.isBefore(previousStart) && it.date.isBefore(start) }
        // Today's steps and food are still being counted - averaging them in would drag the numbers down.
        val completeDays = inPeriod.filter { it.date.isBefore(end) }

        val weightByDay = dailyWeights(input.weights, zone)
        val periodWeights = weightByDay.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val previousWeights = weightByDay.filter { !it.date.isBefore(previousStart) && it.date.isBefore(start) }

        val records = input.records
        val bpReadings = records.history(MarkerType.BLOOD_PRESSURE)
        val periodBp = bpReadings.filter { !it.date.isBefore(start) && !it.date.isAfter(end) && it.secondaryValue != null }
        val previousBp = bpReadings.filter { !it.date.isBefore(previousStart) && it.date.isBefore(start) && it.secondaryValue != null }

        val vitals = buildList {
            addAll(
                listOfNotNull(
                    summaryStat(VitalKind.RESTING_HEART_RATE, inPeriod, previous) { it.restingHeartRate },
                    summaryStat(VitalKind.HRV, inPeriod, previous) { it.hrvRmssd },
                    summaryStat(VitalKind.SLEEPING_HEART_RATE, inPeriod, previous) { it.sleepingHeartRate },
                    bloodPressureStat(periodBp, previousBp),
                    summaryStat(VitalKind.SPO2, inPeriod, previous) { it.spo2Percentage },
                    summaryStat(VitalKind.RESPIRATORY_RATE, inPeriod, previous) { it.respiratoryRate },
                    summaryStat(VitalKind.VO2_MAX, inPeriod, previous) { it.vo2Max },
                    stat(VitalKind.WEIGHT, periodWeights, previousWeights),
                    summaryStat(VitalKind.SLEEP_DURATION, inPeriod, previous) { it.sleepDurationMinutes?.toDouble() },
                    summaryStat(VitalKind.STEPS, completeDays, previous) { it.steps?.toDouble() }
                )
            )
        }

        val weekly = options.period.days > ReportPeriod.DAYS_30.days
        val trends = if (ReportSection.VITALS !in options.sections) emptyList() else listOfNotNull(
            trend(VitalKind.RESTING_HEART_RATE, inPeriod.points { it.restingHeartRate }, weekly),
            trend(VitalKind.HRV, inPeriod.points { it.hrvRmssd }, weekly),
            trend(VitalKind.SLEEP_DURATION, inPeriod.points { it.sleepDurationMinutes?.toDouble() }, weekly),
            trend(VitalKind.STEPS, completeDays.points { it.steps?.toDouble() }, weekly),
            trend(VitalKind.WEIGHT, periodWeights, weekly),
            trend(
                VitalKind.BLOOD_PRESSURE,
                periodBp.dailyAverage { it.value },
                weekly,
                secondary = periodBp.dailyAverage { it.secondaryValue!! }
            ),
            trend(VitalKind.SPO2, inPeriod.points { it.spo2Percentage }, weekly)
        )

        val heightCm = input.profile?.heightCm
        val latestWeight = input.weights.filter { !it.timestamp.atZone(zone).toLocalDate().isAfter(end) }
            .maxByOrNull { it.timestamp }?.weightKg ?: input.profile?.currentWeightKg
        val patient = ReportPatient(
            name = options.patientName?.trim()?.takeIf { it.isNotEmpty() },
            dateOfBirth = input.profile?.dateOfBirth,
            age = input.profile?.age,
            sex = input.profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() } ?: records.sex,
            heightCm = heightCm,
            weightKg = latestWeight,
            bmi = if (heightCm != null && heightCm > 0 && latestWeight != null) latestWeight / ((heightCm / 100) * (heightCm / 100)) else null,
            waistCm = input.profile?.waistCircumferenceCm,
            bodyFatPercent = input.latestBodyFatPercent,
            isPregnant = records.isPregnant,
            pregnancyDueDate = records.pregnancyDueDate
        )

        val sexForMarkers = patient.sex
        val markers = MarkerType.entries.filter { it.isVisibleFor(sexForMarkers) }.mapNotNull { type ->
            markerSummary(type, records.history(type), start, end, sexForMarkers)
        }
        val readings = records.readings.filter { !it.date.isBefore(start) && !it.date.isAfter(end) && it.type.isVisibleFor(sexForMarkers) }

        val supplements = input.supplements.active.map { s ->
            ReportSupplement(
                name = s.name,
                brand = s.brand?.takeIf { it.isNotBlank() },
                dose = "${formatAmount(s.doseAmount)} ${s.doseUnit}".trim(),
                times = s.times,
                adherence = if (s.tracked) input.supplements.weekAdherence[s.id]?.takeIf { it.second > 0 } else null,
                ingredients = s.ingredients.takeIf { it.isNotEmpty() }?.joinToString(", ") { ing ->
                    listOfNotNull(ing.name, ing.amount?.let { a -> "${formatAmount(a)}${ing.unit?.let { u -> " $u" }.orEmpty()}" })
                        .joinToString(" ")
                },
                notes = s.notes?.takeIf { it.isNotBlank() }
            )
        }

        val cycle = if (sexForMarkers == BiologicalSex.FEMALE && records.periods.isNotEmpty()) {
            CycleSummary(
                stats = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, end),
                startsInPeriod = records.periods.map { it.startDate }.filter { !it.isBefore(start) && !it.isAfter(end) }.sortedDescending()
            )
        } else null

        return DoctorReport(
            generatedAt = input.now,
            start = start,
            end = end,
            options = options,
            patient = patient,
            vitals = if (ReportSection.VITALS in options.sections) vitals else emptyList(),
            trends = trends,
            markers = if (ReportSection.LABS in options.sections) markers else emptyList(),
            readings = if (ReportSection.LABS in options.sections) readings else emptyList(),
            conditions = if (ReportSection.CONDITIONS in options.sections) records.conditions else emptyList(),
            allergies = if (ReportSection.CONDITIONS in options.sections) records.allergies else emptyList(),
            supplements = if (ReportSection.SUPPLEMENTS in options.sections) supplements else emptyList(),
            sleep = if (ReportSection.SLEEP in options.sections) sleepSummary(inPeriod, input.profile?.sleepGoalMinutes, zone) else null,
            activity = if (ReportSection.ACTIVITY in options.sections) activitySummary(completeDays, input.workouts, options.period.days, input.profile?.dailyStepGoal) else null,
            nutrition = if (ReportSection.NUTRITION in options.sections) nutritionSummary(input.food, end) else null,
            cycle = if (ReportSection.CYCLE in options.sections) cycle else null,
            dataSources = input.dataSources.distinct().sorted()
        )
    }

    private fun List<DailyHealthSummaryEntity>.points(value: (DailyHealthSummaryEntity) -> Double?): List<TrendPoint> =
        mapNotNull { s -> value(s)?.takeIf { it > 0 }?.let { TrendPoint(s.date, it) } }.sortedBy { it.date }

    private fun List<MarkerReading>.dailyAverage(value: (MarkerReading) -> Double): List<TrendPoint> =
        groupBy { it.date }.map { (date, list) -> TrendPoint(date, list.map(value).average()) }.sortedBy { it.date }

    /** One point per day - several weigh-ins on one day are averaged. */
    private fun dailyWeights(weights: List<WeightMeasurementEntity>, zone: ZoneId): List<TrendPoint> =
        weights.groupBy { it.timestamp.atZone(zone).toLocalDate() }
            .map { (date, list) -> TrendPoint(date, list.map { it.weightKg }.average()) }
            .sortedBy { it.date }

    private fun summaryStat(
        kind: VitalKind,
        period: List<DailyHealthSummaryEntity>,
        previous: List<DailyHealthSummaryEntity>,
        value: (DailyHealthSummaryEntity) -> Double?
    ): VitalStat? = stat(kind, period.points(value), previous.points(value))

    private fun stat(kind: VitalKind, points: List<TrendPoint>, previous: List<TrendPoint>): VitalStat? {
        if (points.isEmpty()) return null
        val values = points.map { it.value }
        val latest = points.maxBy { it.date }
        return VitalStat(
            kind = kind,
            average = values.average(),
            min = values.min(),
            max = values.max(),
            latest = latest.value,
            latestDate = latest.date,
            daysWithData = points.size,
            previousAverage = previous.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        )
    }

    private fun bloodPressureStat(period: List<MarkerReading>, previous: List<MarkerReading>): VitalStat? {
        if (period.isEmpty()) return null
        val latest = period.maxBy { it.measuredAt }
        val diastolic = period.mapNotNull { it.secondaryValue }
        return VitalStat(
            kind = VitalKind.BLOOD_PRESSURE,
            average = period.map { it.value }.average(),
            min = period.minOf { it.value },
            max = period.maxOf { it.value },
            latest = latest.value,
            latestDate = latest.date,
            daysWithData = period.map { it.date }.distinct().size,
            previousAverage = previous.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
            secondaryAverage = diastolic.average(),
            secondaryMin = diastolic.min(),
            secondaryMax = diastolic.max(),
            secondaryLatest = latest.secondaryValue,
            previousSecondaryAverage = previous.mapNotNull { it.secondaryValue }.takeIf { it.isNotEmpty() }?.average()
        )
    }

    /** Needs at least two points to draw a line; longer periods average by week (Monday start). */
    private fun trend(kind: VitalKind, points: List<TrendPoint>, weekly: Boolean, secondary: List<TrendPoint> = emptyList()): TrendChart? {
        val primary = if (weekly) weeklyAverages(points) else points
        if (primary.size < 2) return null
        return TrendChart(kind, primary, if (weekly) weeklyAverages(secondary) else secondary, weekly)
    }

    internal fun weeklyAverages(points: List<TrendPoint>): List<TrendPoint> {
        if (points.isEmpty()) return emptyList()
        // A wearable's daily series drops thin weeks; occasional readings (a weekly blood
        // pressure check) are worth keeping even alone in their week.
        val weeks = ChronoUnit.WEEKS.between(points.first().date, points.last().date) + 1
        val minDays = if (points.size >= weeks * DENSE_DAYS_PER_WEEK) MIN_DAYS_PER_WEEK else 1
        return points.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .filterValues { it.size >= minDays }
            .map { (week, list) -> TrendPoint(week, list.map { it.value }.average()) }
            .sortedBy { it.date }
    }

    private fun markerSummary(type: MarkerType, history: List<MarkerReading>, start: LocalDate, end: LocalDate, sex: BiologicalSex?): MarkerSummary? {
        val upToEnd = history.filter { !it.date.isAfter(end) }
        val latest = upToEnd.maxByOrNull { it.measuredAt } ?: return null
        val inPeriod = upToEnd.filter { !it.date.isBefore(start) }
        val category = category(latest, sex)
        return MarkerSummary(
            type = type,
            latest = latest,
            category = category?.first,
            tone = category?.second,
            readingsInPeriod = inPeriod.size,
            periodMin = inPeriod.minOfOrNull { it.value },
            periodMax = inPeriod.maxOfOrNull { it.value },
            periodAverage = inPeriod.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
            periodSecondaryAverage = inPeriod.mapNotNull { it.secondaryValue }.takeIf { it.isNotEmpty() }?.average()
        )
    }

    /** The range a reading falls in, worded as the app's own ranges are (AHA/ADA/NCEP bands). */
    fun category(reading: MarkerReading, sex: BiologicalSex?): Pair<String, MetricTone>? {
        if (reading.type == MarkerType.BLOOD_PRESSURE) {
            val diastolic = reading.secondaryValue ?: return null
            val (label, tone) = HealthMarkerCalculator.bloodPressureCategory(reading.value, diastolic)
            // The app's "seek care" hint is for the user, not the clinician reading this.
            return label.substringBefore(" - ") to tone
        }
        val bands = HealthMarkerCalculator.ranges(reading.type, sex, reading.glucoseContext).bands
        return bands.firstOrNull { band -> band.upTo == null || reading.value < band.upTo }?.let { it.label to it.tone }
    }

    private fun sleepSummary(period: List<DailyHealthSummaryEntity>, goalMinutes: Int?, zone: ZoneId): SleepSummary? {
        val nights = period.filter { (it.sleepDurationMinutes ?: 0) > 0 }
        if (nights.isEmpty()) return null
        val minutes = nights.map { it.sleepDurationMinutes!! }
        val sorted = minutes.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val bedtimes = nights.mapNotNull { it.sleepStartTime?.atZone(zone)?.toLocalTime() }
        val wakeTimes = nights.mapNotNull { it.sleepEndTime?.atZone(zone)?.toLocalTime() }
        val typicalBed = circularMean(bedtimes)
        return SleepSummary(
            nightsRecorded = nights.size,
            averageMinutes = minutes.average(),
            medianMinutes = median,
            shortestMinutes = sorted.first(),
            longestMinutes = sorted.last(),
            nightsUnder6h = minutes.count { it < 6 * 60 },
            nightsOver9h = minutes.count { it > 9 * 60 },
            typicalBedtime = typicalBed,
            typicalWakeTime = circularMean(wakeTimes),
            bedtimeVariabilityMinutes = typicalBed?.let { mean ->
                bedtimes.map { abs(minutesBetween(mean, it)) }.average()
            }?.takeIf { bedtimes.size >= 3 },
            goalMinutes = goalMinutes
        )
    }

    /** Average clock time that treats 23:30 and 00:30 as an hour apart, not 23 hours. */
    internal fun circularMean(times: List<LocalTime>): LocalTime? {
        if (times.isEmpty()) return null
        val angles = times.map { it.toSecondOfDay() / 86_400.0 * 2 * PI }
        val angle = atan2(angles.sumOf { sin(it) }, angles.sumOf { cos(it) })
        val seconds = ((angle / (2 * PI) * 86_400).roundToLong() + 86_400) % 86_400
        // Round to the minute; the seconds would be false precision.
        return LocalTime.ofSecondOfDay(seconds).withSecond(0).withNano(0)
    }

    /** Signed minutes from [a] to [b] the short way round the clock. */
    private fun minutesBetween(a: LocalTime, b: LocalTime): Int {
        var diff = (b.toSecondOfDay() - a.toSecondOfDay()) / 60
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440
        return diff
    }

    private fun activitySummary(
        days: List<DailyHealthSummaryEntity>,
        workouts: List<ExerciseSessionEntity>,
        periodDays: Int,
        stepGoal: Long?
    ): ActivitySummary? {
        val steps = days.mapNotNull { it.steps?.takeIf { s -> s > 0 } }
        val active = days.mapNotNull { it.activeMinutes?.takeIf { m -> m > 0 } }
        if (steps.isEmpty() && active.isEmpty() && workouts.isEmpty()) return null
        val weeks = periodDays / 7.0
        val top = workouts.groupBy { WorkoutText.type(it).label }
            .map { (label, list) -> WorkoutTypeTotal(label, list.size, list.sumOf { it.durationMinutes }) }
            .sortedWith(compareByDescending<WorkoutTypeTotal> { it.minutes }.thenByDescending { it.sessions })
            .take(TOP_WORKOUT_TYPES)
        return ActivitySummary(
            daysRecorded = steps.size,
            averageSteps = steps.takeIf { it.isNotEmpty() }?.average(),
            averageActiveMinutes = active.takeIf { it.isNotEmpty() }?.average(),
            daysMeetingStepGoal = stepGoal?.let { goal -> steps.count { it >= goal } } ?: 0,
            stepGoal = stepGoal,
            workouts = workouts.size,
            workoutsPerWeek = workouts.size / weeks,
            workoutMinutesPerWeek = workouts.sumOf { it.durationMinutes } / weeks,
            topWorkouts = top
        )
    }

    private fun nutritionSummary(food: List<FoodLogEntity>, today: LocalDate): NutritionSummary? {
        val byDay = food.filter { it.date.isBefore(today) }.groupBy { it.date }
        if (byDay.isEmpty()) return null
        fun avg(value: (FoodLogEntity) -> Double) = byDay.values.map { day -> day.sumOf(value) }.average()
        return NutritionSummary(
            daysLogged = byDay.size,
            averageCalories = avg { it.calories.toDouble() },
            averageProteinG = avg { it.proteinG },
            averageCarbsG = avg { it.carbsG },
            averageFatG = avg { it.fatG },
            averageFiberG = avg { it.fiberG },
            averageSugarG = avg { it.sugarG },
            averageSodiumMg = avg { it.sodiumMg }
        )
    }

    /** "2" rather than "2.0"; "0.5" stays. */
    internal fun formatAmount(value: Double): String =
        if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
