package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(sessions: List<SleepSessionEntity>): List<Long>

    @Query("SELECT * FROM sleep_sessions WHERE startTime >= :start AND endTime <= :end ORDER BY startTime DESC")
    fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE startTime >= :start AND endTime <= :end ORDER BY startTime DESC")
    suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC LIMIT 1")
    fun getLatestSession(): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime ASC LIMIT 1")
    suspend fun getOldestSession(): SleepSessionEntity?

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getCount(): Int

    /** Sessions starting in a synced window, to find ones that are gone from Health Connect.
     * Only imported rows: sessions logged in Hangry itself are never touched. */
    @Query("SELECT recordFingerprint FROM sleep_sessions WHERE startTime >= :start AND startTime < :end AND sourcePackageName NOT LIKE 'com.kevan.hangry%' AND sourcePackageName != 'manual' AND recordFingerprint NOT LIKE 'manual%'")
    suspend fun getImportedFingerprintsStartingBetween(start: Instant, end: Instant): List<String>

    @Query("DELETE FROM sleep_sessions WHERE recordFingerprint IN (:fingerprints)")
    suspend fun deleteByFingerprints(fingerprints: List<String>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
