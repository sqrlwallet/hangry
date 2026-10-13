package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain repository abstraction for height measurement data.
 */
interface HeightRepository {
    suspend fun insertOrIgnore(records: List<HeightMeasurementEntity>): List<Long>
    fun getLatestHeight(): Flow<HeightMeasurementEntity?>
    suspend fun getLatestHeightSync(): HeightMeasurementEntity?
    suspend fun getCount(): Int
    suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String>
    suspend fun deleteByFingerprints(fingerprints: List<String>)
    suspend fun deleteAll()
}
