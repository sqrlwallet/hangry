package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyHealthSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(summary: DailyHealthSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(summaries: List<DailyHealthSummaryEntity>)

    @Query("SELECT * FROM daily_health_summaries WHERE date = :date LIMIT 1")
    fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?>

    @Query("SELECT * FROM daily_health_summaries WHERE date = :date LIMIT 1")
    suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity?

    @Query("SELECT * FROM daily_health_summaries WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>>

    @Query("SELECT * FROM daily_health_summaries ORDER BY date DESC")
    fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>>

    @Query("SELECT * FROM daily_health_summaries WHERE date >= :start AND date <= :end ORDER BY date DESC")
    suspend fun getSummariesBetweenList(start: LocalDate, end: LocalDate): List<DailyHealthSummaryEntity>

    @Query("SELECT * FROM daily_health_summaries ORDER BY date DESC LIMIT 1")
    fun getLatestSummary(): Flow<DailyHealthSummaryEntity?>

    @Query("SELECT * FROM daily_health_summaries ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSummarySync(): DailyHealthSummaryEntity?

    @Query("SELECT * FROM daily_health_summaries ORDER BY date ASC LIMIT 1")
    suspend fun getOldestSummary(): DailyHealthSummaryEntity?

    @Query("SELECT COUNT(*) FROM daily_health_summaries")
    suspend fun getCount(): Int

    @Query("DELETE FROM daily_health_summaries WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM daily_health_summaries")
    suspend fun deleteAll()
}
