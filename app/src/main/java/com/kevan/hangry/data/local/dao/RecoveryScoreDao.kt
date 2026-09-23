package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecoveryScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(score: RecoveryScoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(scores: List<RecoveryScoreEntity>)

    @Query("SELECT * FROM recovery_scores WHERE date = :date LIMIT 1")
    fun getScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?>

    @Query("SELECT * FROM recovery_scores WHERE date = :date LIMIT 1")
    suspend fun getScoreForDateSync(date: LocalDate): RecoveryScoreEntity?

    @Query("SELECT * FROM recovery_scores ORDER BY date DESC LIMIT 1")
    fun getLatestScore(): Flow<RecoveryScoreEntity?>

    @Query("SELECT * FROM recovery_scores ORDER BY date DESC LIMIT 1")
    suspend fun getLatestScoreSync(): RecoveryScoreEntity?

    @Query("SELECT * FROM recovery_scores WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>>

    @Query("SELECT * FROM recovery_scores ORDER BY date DESC")
    fun getAllScores(): Flow<List<RecoveryScoreEntity>>

    @Query("SELECT * FROM recovery_scores WHERE date >= :start AND date <= :end ORDER BY date DESC")
    suspend fun getScoresBetweenList(start: LocalDate, end: LocalDate): List<RecoveryScoreEntity>

    @Query("DELETE FROM recovery_scores WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM recovery_scores")
    suspend fun deleteAll()
}
