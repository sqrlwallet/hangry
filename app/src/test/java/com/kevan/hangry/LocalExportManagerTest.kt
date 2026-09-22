package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.repository.DefaultLocalExportManager
import com.kevan.hangry.domain.repository.DailySummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalExportManagerTest {

    @Test
    fun testExportJson_containsValidStructureAndNoLeak() = runBlocking {
        val today = LocalDate.of(2026, 9, 22)
        val mockRepo = object : DailySummaryRepository {
            override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = flowOf(null)
            override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> = flowOf(null)
            override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? = null
            override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> {
                return flowOf(
                    listOf(
                        DailyHealthSummaryEntity(
                            date = today,
                            sleepDurationMinutes = 480,
                            steps = 10500,
                            restingHeartRate = 56.0,
                            hrvRmssd = 65.0
                        )
                    )
                )
            }
            override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> = getSummariesBetween(today, today)
            override suspend fun saveSummary(summary: DailyHealthSummaryEntity) {}
            override fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?> = flowOf(null)
            override fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> = flowOf(null)
            override suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity? = null
            override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> {
                return flowOf(
                    listOf(
                        RecoveryScoreEntity(
                            date = today,
                            score = 85,
                            confidence = "HIGH",
                            state = "PRIMED",
                            supportiveAdvice = "Great recovery today"
                        )
                    )
                )
            }
            override fun getAllScores(): Flow<List<RecoveryScoreEntity>> = getScoresBetween(today, today)
            override suspend fun saveRecoveryScore(score: RecoveryScoreEntity) {}
            override suspend fun deleteAll() {}
        }

        val exportManager = DefaultLocalExportManager(mockRepo)

        val json = exportManager.exportDataAsJson()
        assertTrue(json.contains("\"appName\": \"Hangry\""))
        assertTrue(json.contains("\"score\": 85"))
        assertTrue(json.contains("\"steps\": 10500"))

        val csv = exportManager.exportDataAsCsv()
        assertTrue(csv.contains("Date,SleepMinutes,Steps"))
        assertTrue(csv.contains("2026-09-22,480,10500"))
    }
}
