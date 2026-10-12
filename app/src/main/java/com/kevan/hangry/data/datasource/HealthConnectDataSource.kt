package com.kevan.hangry.data.datasource

import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.domain.calculation.NightlyVitals
import java.time.Instant
import java.time.LocalDate

interface HealthConnectDataSource {
    suspend fun isAvailable(): Boolean
    suspend fun hasPermissions(): Boolean
    suspend fun fetchSleepSessions(start: Instant, end: Instant): List<SleepSessionEntity>
    suspend fun fetchExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionEntity>
    suspend fun fetchRestingHeartRates(start: LocalDate, end: LocalDate): List<RestingHeartRateEntity>
    suspend fun fetchHrvMeasurements(start: LocalDate, end: LocalDate): List<HrvMeasurementEntity>
    suspend fun fetchStepsSummaries(start: LocalDate, end: LocalDate): List<StepsSummaryEntity>
    suspend fun fetchHeartRateSamples(start: Instant, end: Instant): List<HeartRateSampleEntity>
    suspend fun fetchWeightMeasurements(start: Instant, end: Instant): List<WeightMeasurementEntity>
    suspend fun fetchHeightMeasurements(start: Instant, end: Instant): List<HeightMeasurementEntity>
    suspend fun fetchVo2Max(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    /** Every SpO2 reading in [start]..[end], percent - placed overnight by the sync. */
    suspend fun fetchOxygenSaturationReadings(start: Instant, end: Instant): List<NightlyVitals.Reading>
    /** Every breathing-rate reading in [start]..[end], breaths/min. */
    suspend fun fetchRespiratoryRateReadings(start: Instant, end: Instant): List<NightlyVitals.Reading>
    /** Average overnight skin temperature change from the device's own baseline, °C, by the morning it ends on. */
    suspend fun fetchSkinTemperatureDeltas(start: LocalDate, end: LocalDate): Map<LocalDate, Double> = emptyMap()
    suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Double, Double>>
    suspend fun fetchBasalMetabolicRates(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun fetchHydration(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun fetchBodyFat(start: Instant, end: Instant): Map<LocalDate, Double>
    suspend fun fetchNutritionRecords(start: Instant, end: Instant): List<FoodLogEntity>

    /** Probes Health Connect to find the earliest record date across all primary types, or null if no records exist. */
    suspend fun findEarliestDataDate(): LocalDate?

    /** Writes one AI-logged (or manually-logged) food entry to Health Connect. Returns false on any failure. */
    suspend fun writeNutritionRecord(entry: FoodLogEntity): Boolean

    /** Removes the record Hangry wrote for [entry]. Returns false on any failure. */
    suspend fun deleteNutritionRecord(entry: FoodLogEntity): Boolean

    /**
     * Write permissions needed to log breathing sessions on this device: a Mindfulness session
     * where Health Connect supports it, otherwise a Guided Breathing exercise session.
     */
    suspend fun breathingWritePermissions(): Set<String>

    /** True when every permission from [breathingWritePermissions] is granted. */
    suspend fun hasBreathingWritePermissions(): Boolean

    /** Writes one completed breathing session to Health Connect. Returns false on any failure. */
    suspend fun writeBreathingSession(session: BreathingSessionEntity, title: String): Boolean

    /** Every permission Hangry has been granted. */
    suspend fun grantedPermissions(): Set<String>

    /**
     * Read permissions for health records: blood pressure, blood glucose, menstruation (only
     * when [includeCycle]) and - where the device supports it - medical records.
     */
    suspend fun healthRecordPermissions(includeCycle: Boolean): Set<String>

    /** True when Health Connect on this device supports medical records (Android 16+). */
    suspend fun supportsMedicalRecords(): Boolean

    /** Writes a manual blood pressure or blood glucose reading (other types are ignored). */
    suspend fun writeHealthMarker(marker: HealthMarkerEntity): Boolean

    /** Removes a reading this app wrote earlier, e.g. when the user deletes it. */
    suspend fun deleteHealthMarker(marker: HealthMarkerEntity): Boolean

    /** Reads whatever health-record data is granted. Never throws; missing access just yields less. */
    suspend fun fetchHealthRecords(since: Instant, includeCycle: Boolean): ImportedHealthRecords

    /** Which apps wrote each kind of data since [since], for the Data sources screen. Never throws. */
    suspend fun fetchDataSources(since: Instant): List<DataSourceInfo> = emptyList()

    /**
     * A workout's GPS route, read while the user is looking at it. Routes from other apps need
     * the user's say-so per workout unless they chose "always allow" - see [RouteResult.ConsentRequired].
     */
    suspend fun readExerciseRoute(recordId: String): RouteResult = RouteResult.None
}

/** One kind of Health Connect data and the apps that wrote it, newest first. */
data class DataSourceInfo(
    val dataType: DataType,
    val packages: List<String>,
    val latest: Instant?
) {
    enum class DataType { SLEEP, STEPS, HEART_RATE, RESTING_HEART_RATE, HRV, SPO2, BREATHING, SKIN_TEMP, VO2_MAX, WORKOUTS, CALORIES, WEIGHT, NUTRITION }
}

sealed interface RouteResult {
    data class Points(val points: List<RoutePoint>) : RouteResult
    data object ConsentRequired : RouteResult
    data object None : RouteResult
}

data class RoutePoint(val time: Instant, val latitude: Double, val longitude: Double, val altitudeMeters: Double?)

data class ImportedHealthRecords(
    val markers: List<HealthMarkerEntity> = emptyList(),
    val profileItems: List<HealthProfileItemEntity> = emptyList(),
    val periods: List<MenstrualPeriodEntity> = emptyList(),
    /** From medical records (female users only); applied by the repository. */
    val pregnancy: List<com.kevan.hangry.data.healthrecords.FhirHealthRecordParser.PregnancyInfo> = emptyList()
)
