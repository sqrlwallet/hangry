package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(sessions: List<ExerciseSessionEntity>): List<Long>

    /** Fills in workout steps on sessions imported before steps were tracked (insertOrIgnore skips them). */
    /** Brings an already-saved workout up to date with everything the current import reads. */
    @Query(
        """UPDATE exercise_sessions SET exerciseType = :exerciseType, title = :title, notes = :notes,
        activeCalories = :activeCalories, totalCalories = :totalCalories, steps = :steps,
        distanceMeters = :distanceMeters, elevationGainMeters = :elevationGainMeters, avgPowerWatts = :avgPowerWatts,
        setCount = :setCount, repCount = :repCount, segmentSummary = :segmentSummary, lapCount = :lapCount,
        detailVersion = :detailVersion WHERE recordFingerprint = :fingerprint"""
    )
    suspend fun updateDetails(
        fingerprint: String, exerciseType: String, title: String?, notes: String?,
        activeCalories: Double?, totalCalories: Double?, steps: Long?,
        distanceMeters: Double?, elevationGainMeters: Double?, avgPowerWatts: Double?,
        setCount: Int?, repCount: Int?, segmentSummary: String?, lapCount: Int?, detailVersion: Int
    )

    suspend fun updateDetails(s: ExerciseSessionEntity) = updateDetails(
        s.recordFingerprint, s.exerciseType, s.title, s.notes, s.activeCalories, s.totalCalories, s.steps,
        s.distanceMeters, s.elevationGainMeters, s.avgPowerWatts, s.setCount, s.repCount, s.segmentSummary,
        s.lapCount, s.detailVersion
    )

    @Query("UPDATE exercise_sessions SET avgHeartRate = :avg, maxHeartRate = :max WHERE recordFingerprint = :fingerprint")
    suspend fun updateHeartRate(fingerprint: String, avg: Double?, max: Double?)

    @Query("UPDATE exercise_sessions SET detailVersion = :version WHERE detailVersion < :version")
    suspend fun markDetailVersion(version: Int)

    @Query("SELECT MIN(startTime) FROM exercise_sessions WHERE detailVersion < :version")
    suspend fun getOldestStartNeedingDetails(version: Int): Instant?

    // Attributed by startTime alone - a session that starts before `end` but runs past it
    // (e.g. crosses midnight) still belongs to the day it started, instead of being excluded
    // from every window because it isn't fully contained by any single one.
    @Query("SELECT * FROM exercise_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity>

    @Query("SELECT * FROM exercise_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT COUNT(*) FROM exercise_sessions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM exercise_sessions ORDER BY startTime ASC LIMIT 1")
    suspend fun getOldestSession(): ExerciseSessionEntity?

    /** Workouts starting in a synced window, to find ones that are gone from Health Connect.
     * Only imported rows: sessions logged in Hangry itself are never touched. */
    @Query("SELECT recordFingerprint FROM exercise_sessions WHERE startTime >= :start AND startTime < :end AND sourcePackageName NOT LIKE 'com.kevan.hangry%' AND sourcePackageName != 'manual' AND recordFingerprint NOT LIKE 'manual%'")
    suspend fun getImportedFingerprintsStartingBetween(start: Instant, end: Instant): List<String>

    @Query("DELETE FROM exercise_sessions WHERE recordFingerprint IN (:fingerprints)")
    suspend fun deleteByFingerprints(fingerprints: List<String>)

    @Query("DELETE FROM exercise_sessions")
    suspend fun deleteAll()
}
