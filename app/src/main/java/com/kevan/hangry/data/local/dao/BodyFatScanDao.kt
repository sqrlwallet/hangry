package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BodyFatScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: BodyFatScanEntity): Long

    @Query("SELECT * FROM body_fat_scans ORDER BY timestamp DESC LIMIT 1")
    fun getLatestScan(): Flow<BodyFatScanEntity?>

    @Query("SELECT * FROM body_fat_scans ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestScanSync(): BodyFatScanEntity?

    @Query("SELECT * FROM body_fat_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<BodyFatScanEntity>>

    @Query("SELECT * FROM body_fat_scans WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp ASC")
    fun getScansBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BodyFatScanEntity>>

    @Query("DELETE FROM body_fat_scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_fat_scans")
    suspend fun deleteAll()
}
