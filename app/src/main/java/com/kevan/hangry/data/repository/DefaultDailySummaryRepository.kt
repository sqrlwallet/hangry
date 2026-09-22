package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.RecoveryScoreDao
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.repository.DailySummaryRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultDailySummaryRepository(
    private val summaryDao: DailyHealthSummaryDao,
    private val recoveryDao: RecoveryScoreDao
) : DailySummaryRepository {
    override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = summaryDao.getLatestSummary()

    override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> =
        summaryDao.getSummaryForDate(date)

    override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? =
        summaryDao.getSummaryForDateSync(date)

    override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> =
        summaryDao.getSummariesBetween(start, end)

    override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> =
        summaryDao.getAllSummaries()

    override suspend fun saveSummary(summary: DailyHealthSummaryEntity) =
        summaryDao.insertOrReplace(summary)

    override fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?> = recoveryDao.getLatestScore()

    override fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> =
        recoveryDao.getScoreForDate(date)

    override suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity? =
        recoveryDao.getScoreForDateSync(date)

    override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> =
        recoveryDao.getScoresBetween(start, end)

    override fun getAllScores(): Flow<List<RecoveryScoreEntity>> =
        recoveryDao.getAllScores()

    override suspend fun saveRecoveryScore(score: RecoveryScoreEntity) =
        recoveryDao.insertOrReplace(score)

    override suspend fun deleteAll() {
        summaryDao.deleteAll()
        recoveryDao.deleteAll()
    }
}
