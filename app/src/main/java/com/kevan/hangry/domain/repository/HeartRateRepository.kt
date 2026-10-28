package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.RestingHeartRateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface HeartRateRepository {
    fun getRestingHeartRates(start: LocalDate, end: LocalDate): Flow<List<RestingHeartRateEntity>>
    suspend fun getRestingHeartRateForDate(date: LocalDate): RestingHeartRateEntity?
    suspend fun getAverageRestingHeartRate(start: LocalDate, end: LocalDate): Double?
    suspend fun insertRestingHeartRates(records: List<RestingHeartRateEntity>): List<Long>
    fun getSamplesBetween(start: Instant, end: Instant): Flow<List<HeartRateSampleEntity>>
    suspend fun getSamplesBetweenSync(start: Instant, end: Instant): List<HeartRateSampleEntity>

    /** How far heart rate fell after a workout that ended at [workoutEnd]; null when it can't be told. */
    suspend fun heartRateRecovery(workoutEnd: Instant, zones: com.kevan.hangry.domain.calculation.HeartRateZones): com.kevan.hangry.domain.calculation.HeartRateRecovery.Result? {
        val window = com.kevan.hangry.domain.calculation.HeartRateRecovery
        val samples = getSamplesBetweenSync(workoutEnd - window.WINDOW_BEFORE_END, workoutEnd + window.WINDOW_AFTER_END)
        return window.calculate(workoutEnd, samples.map { it.timestamp to it.bpm }, zones)
    }
    fun getZoneDistribution(start: Instant, end: Instant, zones: com.kevan.hangry.domain.calculation.HeartRateZones): Flow<com.kevan.hangry.domain.model.HeartRateZoneDistribution>
    suspend fun getZoneDistributionSync(start: Instant, end: Instant, zones: com.kevan.hangry.domain.calculation.HeartRateZones): com.kevan.hangry.domain.model.HeartRateZoneDistribution
    suspend fun insertSamples(samples: List<HeartRateSampleEntity>): List<Long>
    suspend fun deleteAll()
}
