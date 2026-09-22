package com.kevan.hangry.data.datasource

import com.kevan.hangry.data.local.entity.*
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
    suspend fun fetchVo2Max(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun fetchOxygenSaturation(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun fetchRespiratoryRate(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Double, Double>>

    /** Writes one AI-logged (or manually-logged) food entry to Health Connect. Returns false on any failure. */
    suspend fun writeNutritionRecord(entry: FoodLogEntity): Boolean
}
