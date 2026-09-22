package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kevan.hangry.data.local.entity.FoodLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodLogDao {
    @Insert
    suspend fun insert(entry: FoodLogEntity): Long

    @Update
    suspend fun update(entry: FoodLogEntity)

    @Delete
    suspend fun delete(entry: FoodLogEntity)

    @Query("SELECT * FROM food_log WHERE date = :date ORDER BY timestamp DESC")
    fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE date >= :start AND date <= :end ORDER BY date DESC, timestamp DESC")
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE date >= :start AND date <= :end ORDER BY date DESC, timestamp DESC")
    suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<FoodLogEntity>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM food_log WHERE date = :date")
    fun getTotalCaloriesForDate(date: LocalDate): Flow<Int>

    @Query("UPDATE food_log SET healthConnectSynced = 1 WHERE id = :id")
    suspend fun markSyncedToHealthConnect(id: Long)

    @Query("DELETE FROM food_log")
    suspend fun deleteAll()
}
