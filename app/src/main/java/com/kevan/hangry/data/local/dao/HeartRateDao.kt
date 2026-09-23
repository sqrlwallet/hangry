package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(samples: List<HeartRateSampleEntity>): List<Long>

    @Query("SELECT * FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getSamplesBetween(start: Instant, end: Instant): Flow<List<HeartRateSampleEntity>>

    @Query("SELECT * FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getSamplesBetweenList(start: Instant, end: Instant): List<HeartRateSampleEntity>

    @Query("SELECT AVG(bpm) FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getAverageBpmBetween(start: Instant, end: Instant): Double?

    @Query("SELECT MIN(bpm) FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end AND bpm >= 35")
    suspend fun getMinBpmBetween(start: Instant, end: Instant): Double?

    @Query("SELECT MAX(bpm) FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getMaxBpmBetween(start: Instant, end: Instant): Double?

    @Query("SELECT AVG(bpm) FROM (SELECT bpm FROM heart_rate_samples WHERE timestamp >= :start AND timestamp <= :end AND bpm >= 35 ORDER BY bpm ASC LIMIT 30)")
    suspend fun getRestingBpmEstimateBetween(start: Instant, end: Instant): Double?

    @Query("""
        SELECT 
            COUNT(CASE WHEN bpm < 114 THEN 1 END) as zone1Count,
            COUNT(CASE WHEN bpm >= 114 AND bpm < 133 THEN 1 END) as zone2Count,
            COUNT(CASE WHEN bpm >= 133 AND bpm < 152 THEN 1 END) as zone3Count,
            COUNT(CASE WHEN bpm >= 152 AND bpm < 171 THEN 1 END) as zone4Count,
            COUNT(CASE WHEN bpm >= 171 THEN 1 END) as zone5Count
        FROM heart_rate_samples
        WHERE timestamp >= :start AND timestamp <= :end
    """)
    fun getZoneDistribution(start: Instant, end: Instant): Flow<com.kevan.hangry.domain.model.HeartRateZoneDistribution>

    @Query("""
        SELECT 
            COUNT(CASE WHEN bpm < 114 THEN 1 END) as zone1Count,
            COUNT(CASE WHEN bpm >= 114 AND bpm < 133 THEN 1 END) as zone2Count,
            COUNT(CASE WHEN bpm >= 133 AND bpm < 152 THEN 1 END) as zone3Count,
            COUNT(CASE WHEN bpm >= 152 AND bpm < 171 THEN 1 END) as zone4Count,
            COUNT(CASE WHEN bpm >= 171 THEN 1 END) as zone5Count
        FROM heart_rate_samples
        WHERE timestamp >= :start AND timestamp <= :end
    """)
    suspend fun getZoneDistributionSync(start: Instant, end: Instant): com.kevan.hangry.domain.model.HeartRateZoneDistribution

    @Query("SELECT COUNT(*) FROM heart_rate_samples")
    suspend fun getCount(): Int

    @Query("DELETE FROM heart_rate_samples")
    suspend fun deleteAll()
}
