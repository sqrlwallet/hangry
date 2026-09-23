package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long>

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightMeasurementEntity?>

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeightSync(): WeightMeasurementEntity?

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC")
    fun getAllWeights(): Flow<List<WeightMeasurementEntity>>

    @Query("SELECT AVG(weightKg) FROM (SELECT weightKg FROM weight_measurements ORDER BY timestamp DESC LIMIT 7)")
    fun getRollingAverageWeight(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM weight_measurements")
    suspend fun getCount(): Int

    /** Weigh-ins imported from Health Connect in a synced window - never ones entered in Hangry. */
    @Query("SELECT recordFingerprint FROM weight_measurements WHERE timestamp >= :start AND timestamp < :end AND sourcePackageName NOT LIKE 'com.kevan.hangry%' AND sourcePackageName != 'manual' AND recordFingerprint NOT LIKE 'manual%'")
    suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String>

    @Query("DELETE FROM weight_measurements WHERE recordFingerprint IN (:fingerprints)")
    suspend fun deleteByFingerprints(fingerprints: List<String>)

    @Query("DELETE FROM weight_measurements")
    suspend fun deleteAll()
}
