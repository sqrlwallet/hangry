package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long>

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC LIMIT 1")
    fun getLatestWeight(): Flow<WeightMeasurementEntity?>

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeightSync(): WeightMeasurementEntity?

    @Query("SELECT * FROM weight_measurements ORDER BY timestamp DESC")
    fun getAllWeights(): Flow<List<WeightMeasurementEntity>>

    @Query("SELECT AVG(weightKg) FROM (SELECT weightKg FROM weight_measurements ORDER BY timestamp DESC LIMIT 7)")
    fun getRollingAverageWeight(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM weight_measurements")
    suspend fun getCount(): Int

    @Query("DELETE FROM weight_measurements")
    suspend fun deleteAll()
}
