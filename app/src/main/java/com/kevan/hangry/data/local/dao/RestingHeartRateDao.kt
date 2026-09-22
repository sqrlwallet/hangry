package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.RestingHeartRateEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RestingHeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<RestingHeartRateEntity>): List<Long>

    // Multiple sources (phone + watch, or a re-sync that supersedes an earlier import) can
    // legitimately produce more than one row for the same date - deterministically prefer the
    // most recently imported one instead of whichever row SQLite happens to return first.
    @Query("SELECT * FROM resting_heart_rate WHERE recordDate = :date ORDER BY importTimestamp DESC LIMIT 1")
    suspend fun getForDate(date: LocalDate): RestingHeartRateEntity?

    @Query("SELECT * FROM resting_heart_rate WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<RestingHeartRateEntity>>

    @Query("SELECT * FROM resting_heart_rate WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<RestingHeartRateEntity>

    @Query("SELECT AVG(restingBpm) FROM resting_heart_rate WHERE recordDate >= :start AND recordDate <= :end AND dataQualityState = 'VALID'")
    suspend fun getAverageBetween(start: LocalDate, end: LocalDate): Double?

    @Query("SELECT COUNT(*) FROM resting_heart_rate")
    suspend fun getCount(): Int

    @Query("DELETE FROM resting_heart_rate")
    suspend fun deleteAll()
}
