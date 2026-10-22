package com.kevan.hangry.domain.report

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.CycleStats
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** How far back a doctor report looks. */
enum class ReportPeriod(val days: Int) {
    DAYS_30(30),
    DAYS_90(90),
    MONTHS_6(182),
    YEAR(365)
}

/** Parts of the report the user can leave out. */
enum class ReportSection {
    VITALS,
    LABS,
    CONDITIONS,
    SUPPLEMENTS,
    SLEEP,
    ACTIVITY,
    NUTRITION,
    CYCLE
}

data class ReportOptions(
    val period: ReportPeriod = ReportPeriod.DAYS_90,
    val sections: Set<ReportSection> = ReportSection.entries.toSet(),
    /** Printed at the top; the app keeps no name of its own. */
    val patientName: String? = null,
    /** "Questions for my visit" - printed on the first page. */
    val visitNotes: String? = null
)

data class ReportPatient(
    val name: String?,
    val dateOfBirth: LocalDate?,
    val age: Int?,
    val sex: BiologicalSex?,
    val heightCm: Double?,
    /** Latest weight on record, at or before the end of the period. */
    val weightKg: Double?,
    val bmi: Double?,
    val waistCm: Double?,
    val bodyFatPercent: Double?,
    val isPregnant: Boolean,
    val pregnancyDueDate: LocalDate?
)

/** Which vital a [VitalStat] or [TrendChart] describes; the renderer owns labels and units. */
enum class VitalKind {
    RESTING_HEART_RATE,
    HRV,
    SLEEPING_HEART_RATE,
    BLOOD_PRESSURE,
    SPO2,
    RESPIRATORY_RATE,
    VO2_MAX,
    WEIGHT,
    SLEEP_DURATION,
    STEPS
}

/**
 * One vital over the period. For blood pressure every number is systolic and the `secondary`
 * values are diastolic. [previousAverage] is the same-length period just before, for context.
 */
data class VitalStat(
    val kind: VitalKind,
    val average: Double,
    val min: Double,
    val max: Double,
    val latest: Double,
    val latestDate: LocalDate,
    val daysWithData: Int,
    val previousAverage: Double?,
    val secondaryAverage: Double? = null,
    val secondaryMin: Double? = null,
    val secondaryMax: Double? = null,
    val secondaryLatest: Double? = null,
    val previousSecondaryAverage: Double? = null
)

data class TrendPoint(val date: LocalDate, val value: Double)

/** A chart of one vital: daily points over a month, weekly averages for longer periods. */
data class TrendChart(
    val kind: VitalKind,
    val points: List<TrendPoint>,
    /** Diastolic for blood pressure. */
    val secondary: List<TrendPoint> = emptyList(),
    val weekly: Boolean
)

/** A lab/vital marker's latest result (which may predate the period) and its readings in it. */
data class MarkerSummary(
    val type: MarkerType,
    val latest: MarkerReading,
    /** e.g. "Normal", "Prediabetes range" - from the same ranges the app shows. */
    val category: String?,
    val tone: MetricTone?,
    val readingsInPeriod: Int,
    val periodMin: Double?,
    val periodMax: Double?,
    val periodAverage: Double?,
    val periodSecondaryAverage: Double?
)

data class ReportSupplement(
    val name: String,
    val brand: String?,
    val dose: String,
    val times: List<LocalTime>,
    /** Taken / scheduled over the last 7 full days; null when the app doesn't track it. */
    val adherence: Pair<Int, Int>?,
    val ingredients: String?,
    val notes: String?
)

data class SleepSummary(
    val nightsRecorded: Int,
    val averageMinutes: Double,
    val medianMinutes: Double,
    val shortestMinutes: Int,
    val longestMinutes: Int,
    val nightsUnder6h: Int,
    val nightsOver9h: Int,
    /** Typical (circular mean) bed and wake times of the main sleep. */
    val typicalBedtime: LocalTime?,
    val typicalWakeTime: LocalTime?,
    /** Typical spread of bedtimes around [typicalBedtime], minutes. */
    val bedtimeVariabilityMinutes: Double?,
    val goalMinutes: Int?
)

data class WorkoutTypeTotal(val label: String, val sessions: Int, val minutes: Int)

data class ActivitySummary(
    val daysRecorded: Int,
    val averageSteps: Double?,
    val averageActiveMinutes: Double?,
    val daysMeetingStepGoal: Int,
    val stepGoal: Long?,
    val workouts: Int,
    val workoutsPerWeek: Double,
    val workoutMinutesPerWeek: Double,
    val topWorkouts: List<WorkoutTypeTotal>
)

data class NutritionSummary(
    val daysLogged: Int,
    val averageCalories: Double,
    val averageProteinG: Double,
    val averageCarbsG: Double,
    val averageFatG: Double,
    val averageFiberG: Double,
    val averageSugarG: Double,
    val averageSodiumMg: Double
)

data class CycleSummary(
    val stats: CycleStats,
    /** Period start dates within the report period, newest first. */
    val startsInPeriod: List<LocalDate>
)

/** Everything printed in a doctor report. Sections the user left out, or with no data, are null/empty. */
data class DoctorReport(
    val generatedAt: ZonedDateTime,
    val start: LocalDate,
    val end: LocalDate,
    val options: ReportOptions,
    val patient: ReportPatient,
    val vitals: List<VitalStat>,
    val trends: List<TrendChart>,
    val markers: List<MarkerSummary>,
    /** Readings in the period, newest first. */
    val readings: List<MarkerReading>,
    val conditions: List<HealthProfileItem>,
    val allergies: List<HealthProfileItem>,
    val supplements: List<ReportSupplement>,
    val sleep: SleepSummary?,
    val activity: ActivitySummary?,
    val nutrition: NutritionSummary?,
    val cycle: CycleSummary?,
    /** Apps the data came from, e.g. "Fitbit", "Samsung Health". */
    val dataSources: List<String>
) {
    val days: Int get() = options.period.days
    fun includes(section: ReportSection): Boolean = section in options.sections
}
