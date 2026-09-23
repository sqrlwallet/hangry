package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HealthMarkerEntity
import com.kevan.hangry.data.local.entity.HealthProfileItemEntity
import com.kevan.hangry.data.local.entity.MarkerGoalEntity
import com.kevan.hangry.data.local.entity.MenstrualPeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordsDao {
    // Markers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: HealthMarkerEntity): Long

    /** Imports: an already-imported record id is skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarkersIgnoringDuplicates(markers: List<HealthMarkerEntity>): List<Long>

    @Query("SELECT * FROM health_markers ORDER BY measuredAt DESC")
    fun observeMarkers(): Flow<List<HealthMarkerEntity>>

    @Query("SELECT * FROM health_markers ORDER BY measuredAt DESC")
    suspend fun getMarkers(): List<HealthMarkerEntity>

    @Query("DELETE FROM health_markers WHERE id = :id")
    suspend fun deleteMarker(id: Long)

    @Query("SELECT * FROM health_markers WHERE id = :id")
    suspend fun getMarker(id: Long): HealthMarkerEntity?

    @Query("UPDATE health_markers SET healthConnectSynced = 1 WHERE id = :id")
    suspend fun markMarkerSynced(id: Long)

    /** Manual BP/glucose readings saved before Health Connect write access was granted. */
    @Query("SELECT * FROM health_markers WHERE source = 'MANUAL' AND healthConnectSynced = 0 AND type IN ('blood_pressure', 'blood_glucose')")
    suspend fun getUnsyncedManualVitals(): List<HealthMarkerEntity>

    // Goals
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: MarkerGoalEntity)

    @Query("SELECT * FROM marker_goals")
    fun observeGoals(): Flow<List<MarkerGoalEntity>>

    @Query("SELECT * FROM marker_goals")
    suspend fun getGoals(): List<MarkerGoalEntity>

    @Query("DELETE FROM marker_goals WHERE type = :type")
    suspend fun deleteGoal(type: String)

    // Allergies & conditions
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfileItems(items: List<HealthProfileItemEntity>): List<Long>

    @Query("SELECT * FROM health_profile_items ORDER BY kind, name COLLATE NOCASE")
    fun observeProfileItems(): Flow<List<HealthProfileItemEntity>>

    @Query("SELECT * FROM health_profile_items ORDER BY kind, name COLLATE NOCASE")
    suspend fun getProfileItems(): List<HealthProfileItemEntity>

    @Query("DELETE FROM health_profile_items WHERE id = :id")
    suspend fun deleteProfileItem(id: Long)

    // Menstrual cycle
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeriods(periods: List<MenstrualPeriodEntity>): List<Long>

    @Query("SELECT * FROM menstrual_periods ORDER BY startDate DESC")
    fun observePeriods(): Flow<List<MenstrualPeriodEntity>>

    @Query("SELECT * FROM menstrual_periods ORDER BY startDate DESC")
    suspend fun getPeriods(): List<MenstrualPeriodEntity>

    @Query("DELETE FROM menstrual_periods WHERE id = :id")
    suspend fun deletePeriod(id: Long)

    @Query("DELETE FROM health_markers")
    suspend fun deleteAllMarkers()

    @Query("DELETE FROM marker_goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM health_profile_items")
    suspend fun deleteAllProfileItems()

    @Query("DELETE FROM menstrual_periods")
    suspend fun deleteAllPeriods()
}
