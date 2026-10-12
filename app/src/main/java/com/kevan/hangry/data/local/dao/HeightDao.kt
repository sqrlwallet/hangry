package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeightDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<HeightMeasurementEntity>): List<Long>

    @Query("SELECT * FROM height_measurements ORDER BY timestamp DESC LIMIT 1")
    fun getLatestHeight(): Flow<HeightMeasurementEntity?>

    @Query("SELECT * FROM height_measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHeightSync(): HeightMeasurementEntity?

    @Query("SELECT COUNT(*) FROM height_measurements")
    suspend fun getCount(): Int

    /** Heights imported from Health Connect in a synced window, to find ones gone from it. */
    @Query("SELECT recordFingerprint FROM height_measurements WHERE timestamp >= :start AND timestamp < :end AND sourcePackageName NOT LIKE 'com.kevan.hangry%' AND sourcePackageName != 'manual' AND recordFingerprint NOT LIKE 'manual%'")
    suspend fun getImportedFingerprintsBetween(start: java.time.Instant, end: java.time.Instant): List<String>

    @Query("DELETE FROM height_measurements WHERE recordFingerprint IN (:fingerprints)")
    suspend fun deleteByFingerprints(fingerprints: List<String>)

    @Query("DELETE FROM height_measurements")
    suspend fun deleteAll()
}
