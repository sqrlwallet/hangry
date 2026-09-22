package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeightDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<HeightMeasurementEntity>): List<Long>

    @Query("SELECT * FROM height_measurements ORDER BY timestamp DESC LIMIT 1")
    fun getLatestHeight(): Flow<HeightMeasurementEntity?>

    @Query("SELECT * FROM height_measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHeightSync(): HeightMeasurementEntity?

    @Query("SELECT COUNT(*) FROM height_measurements")
    suspend fun getCount(): Int

    @Query("DELETE FROM height_measurements")
    suspend fun deleteAll()
}
