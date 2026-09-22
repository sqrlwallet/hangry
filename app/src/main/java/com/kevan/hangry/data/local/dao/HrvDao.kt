package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HrvDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<HrvMeasurementEntity>): List<Long>

    // See RestingHeartRateDao.getForDate - deterministically prefer the most recently
    // imported row when more than one exists for the same date.
    @Query("SELECT * FROM hrv_measurements WHERE recordDate = :date ORDER BY importTimestamp DESC LIMIT 1")
    suspend fun getForDate(date: LocalDate): HrvMeasurementEntity?

    @Query("SELECT * FROM hrv_measurements WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<HrvMeasurementEntity>>

    @Query("SELECT * FROM hrv_measurements WHERE recordDate >= :start AND recordDate <= :end ORDER BY recordDate DESC")
    suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<HrvMeasurementEntity>

    @Query("SELECT AVG(rmssd) FROM hrv_measurements WHERE recordDate >= :start AND recordDate <= :end AND dataQualityState = 'VALID'")
    suspend fun getAverageBetween(start: LocalDate, end: LocalDate): Double?

    @Query("SELECT COUNT(*) FROM hrv_measurements")
    suspend fun getCount(): Int

    @Query("DELETE FROM hrv_measurements")
    suspend fun deleteAll()
}
