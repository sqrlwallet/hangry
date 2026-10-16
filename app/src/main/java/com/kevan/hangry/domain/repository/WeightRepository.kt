package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain repository abstraction for weight tracking data.
 */
interface WeightRepository {
    suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long>
    fun getLatestWeight(): Flow<WeightMeasurementEntity?>
    suspend fun getLatestWeightSync(): WeightMeasurementEntity?
    fun getAllWeights(): Flow<List<WeightMeasurementEntity>>
    fun getWeightsBetween(start: Instant, end: Instant): Flow<List<WeightMeasurementEntity>>
    suspend fun getWeightsBetweenList(start: Instant, end: Instant): List<WeightMeasurementEntity>
    fun getRollingAverageWeight(): Flow<Double?>
    suspend fun getCount(): Int
    suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String>
    suspend fun deleteByFingerprints(fingerprints: List<String>)
    suspend fun deleteAll()
}
