package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DailySummaryRepository {
    fun getLatestSummary(): Flow<DailyHealthSummaryEntity?>
    fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?>
    suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity?
    fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>>
    fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>>
    suspend fun saveSummary(summary: DailyHealthSummaryEntity)

    fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?>
    fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?>
    suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity?
    fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>>
    fun getAllScores(): Flow<List<RecoveryScoreEntity>>
    suspend fun saveRecoveryScore(score: RecoveryScoreEntity)

    suspend fun deleteAll()
}
