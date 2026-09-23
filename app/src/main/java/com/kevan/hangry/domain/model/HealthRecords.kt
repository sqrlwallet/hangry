package com.kevan.hangry.domain.model

import java.time.Instant
import java.time.LocalDate

/** A unit a marker can be entered in, and the factor that converts it to the canonical unit. */
data class MarkerUnit(val label: String, val toCanonical: Double)

enum class GoalDirection { LOWER, RAISE }

/**
 * Lab and vital markers the user can log, import from Health Connect, and set goals for.
 * Values are always stored in [canonicalUnit]; [units] lists what the user may type in.
 * [id] is persisted - never rename one.
 */
enum class MarkerType(
    val id: String,
    val label: String,
    val canonicalUnit: String,
    val units: List<MarkerUnit>,
    val decimals: Int,
    val defaultGoalDirection: GoalDirection,
    /** Only blood pressure has a second value (diastolic). */
    val hasSecondaryValue: Boolean = false
) {
    BLOOD_PRESSURE("blood_pressure", "Blood pressure", "mmHg", listOf(MarkerUnit("mmHg", 1.0)), 0, GoalDirection.LOWER, hasSecondaryValue = true),
    BLOOD_GLUCOSE("blood_glucose", "Blood sugar", "mg/dL", listOf(MarkerUnit("mg/dL", 1.0), MarkerUnit("mmol/L", 18.016)), 0, GoalDirection.LOWER),
    HBA1C("hba1c", "HbA1c", "%", listOf(MarkerUnit("%", 1.0)), 1, GoalDirection.LOWER),
    TOTAL_CHOLESTEROL("total_cholesterol", "Total cholesterol", "mg/dL", listOf(MarkerUnit("mg/dL", 1.0), MarkerUnit("mmol/L", 38.67)), 0, GoalDirection.LOWER),
    LDL("ldl", "LDL cholesterol", "mg/dL", listOf(MarkerUnit("mg/dL", 1.0), MarkerUnit("mmol/L", 38.67)), 0, GoalDirection.LOWER),
    HDL("hdl", "HDL cholesterol", "mg/dL", listOf(MarkerUnit("mg/dL", 1.0), MarkerUnit("mmol/L", 38.67)), 0, GoalDirection.RAISE),
    TRIGLYCERIDES("triglycerides", "Triglycerides", "mg/dL", listOf(MarkerUnit("mg/dL", 1.0), MarkerUnit("mmol/L", 88.57)), 0, GoalDirection.LOWER),
    TESTOSTERONE("testosterone", "Testosterone (total)", "ng/dL", listOf(MarkerUnit("ng/dL", 1.0), MarkerUnit("nmol/L", 28.84)), 0, GoalDirection.RAISE);

    /** Testosterone is only tracked for users whose sex is set to male. */
    fun isVisibleFor(sex: BiologicalSex?): Boolean = this != TESTOSTERONE || sex == BiologicalSex.MALE

    companion object {
        fun fromId(id: String): MarkerType? = entries.firstOrNull { it.id == id }
    }
}

/** When a blood sugar reading was taken - the healthy range depends on it. */
enum class GlucoseContext(val label: String) {
    FASTING("Fasting"),
    BEFORE_MEAL("Before a meal"),
    AFTER_MEAL("After a meal"),
    RANDOM("Random");

    companion object {
        fun fromName(name: String?): GlucoseContext? = entries.firstOrNull { it.name == name }
    }
}

enum class RecordSource(val label: String) {
    MANUAL("Entered by you"),
    HEALTH_CONNECT("Health Connect"),
    MEDICAL_RECORD("Medical record");

    companion object {
        fun fromName(name: String?): RecordSource = entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

enum class HealthProfileKind(val label: String) { ALLERGY("Allergy"), CONDITION("Condition") }

data class MarkerReading(
    val id: Long,
    val type: MarkerType,
    /** Canonical unit; systolic for blood pressure. */
    val value: Double,
    /** Diastolic for blood pressure, otherwise null. */
    val secondaryValue: Double?,
    val measuredAt: Instant,
    val date: LocalDate,
    val glucoseContext: GlucoseContext?,
    val source: RecordSource,
    val note: String?
)

data class MarkerGoal(
    val type: MarkerType,
    val targetValue: Double,
    val targetSecondary: Double?,
    val direction: GoalDirection,
    val startValue: Double?,
    val startDate: LocalDate,
    val targetDate: LocalDate?
)

/** Where the latest reading stands against a goal. */
data class GoalProgress(
    val goal: MarkerGoal,
    val latest: MarkerReading?,
    /** 0..1 of the way from the starting value to the target; null without enough data. */
    val fraction: Double?,
    val reached: Boolean,
    /** Canonical-unit distance still to go (always ≥ 0). */
    val remaining: Double?
)

data class CycleStats(
    val periodCount: Int,
    val averageCycleDays: Double?,
    val averagePeriodDays: Double?,
    val lastPeriodStart: LocalDate?,
    val predictedNextStart: LocalDate?,
    /** Day 1 = first day of the most recent period. */
    val currentCycleDay: Int?,
    val inPeriodNow: Boolean
)

data class HealthProfileItem(
    val id: Long,
    val kind: HealthProfileKind,
    val name: String,
    val note: String?,
    val source: RecordSource
)

data class MenstrualPeriod(
    val id: Long,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val source: RecordSource
)

/** Everything on the Health Records screen, and what Ask Dash is given. */
data class HealthRecordsSnapshot(
    /** Newest first. */
    val readings: List<MarkerReading> = emptyList(),
    val goals: List<MarkerGoal> = emptyList(),
    val profileItems: List<HealthProfileItem> = emptyList(),
    val periods: List<MenstrualPeriod> = emptyList(),
    val sex: BiologicalSex? = null,
    val isPregnant: Boolean = false,
    val pregnancyDueDate: LocalDate? = null
) {
    /** Pregnancy and cycle tracking are only offered when sex is set to female. */
    val showsFemaleHealth: Boolean get() = sex == BiologicalSex.FEMALE

    /** Markers offered to this user - testosterone only when sex is set to male. */
    val visibleMarkers: List<MarkerType> get() = MarkerType.entries.filter { it.isVisibleFor(sex) }

    fun latest(type: MarkerType): MarkerReading? = readings.firstOrNull { it.type == type }
    fun history(type: MarkerType): List<MarkerReading> = readings.filter { it.type == type }
    fun goal(type: MarkerType): MarkerGoal? = goals.firstOrNull { it.type == type }
    val allergies: List<HealthProfileItem> get() = profileItems.filter { it.kind == HealthProfileKind.ALLERGY }
    val conditions: List<HealthProfileItem> get() = profileItems.filter { it.kind == HealthProfileKind.CONDITION }
}

data class HealthRecordsImportResult(
    val newReadings: Int,
    val newProfileItems: Int,
    val newPeriods: Int,
    val medicalRecordsSupported: Boolean,
    /** True when a current pregnancy (or its due date) was taken from medical records. */
    val pregnancyUpdated: Boolean = false
) {
    val total: Int get() = newReadings + newProfileItems + newPeriods + if (pregnancyUpdated) 1 else 0
}
