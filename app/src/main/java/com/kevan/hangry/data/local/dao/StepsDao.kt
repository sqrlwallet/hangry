package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.StepsSummaryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StepsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<StepsSummaryEntity>): List<Long>

    // See RestingHeartRateDao.getForDate - deterministically prefer the most recently
    // imported row (e.g. a re-sync with a more complete daily total) for the same date.
    @Query("SELECT * FROM steps_summaries WHERE recordDate = :date ORDER BY importTimestamp DESC LIMIT 1")
    suspend fun getForDate(date: LocalDate): StepsSummaryEntity?

    @Query("SELECT * FROM steps_summaries WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<StepsSummaryEntity>>

    @Query("SELECT * FROM steps_summaries WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<StepsSummaryEntity>

    @Query("SELECT COUNT(*) FROM steps_summaries")
    suspend fun getCount(): Int

    @Query("SELECT MIN(recordDate) FROM steps_summaries")
    suspend fun getEarliestDate(): LocalDate?

    @Query("DELETE FROM steps_summaries")
    suspend fun deleteAll()
}
