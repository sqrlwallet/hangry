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
    @Query("UPDATE exercise_sessions SET steps = :steps WHERE recordFingerprint = :fingerprint AND steps IS NULL")
    suspend fun backfillSteps(fingerprint: String, steps: Long)

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

    @Query("DELETE FROM exercise_sessions")
    suspend fun deleteAll()
}
