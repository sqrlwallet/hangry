package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.MealPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MealPlanEntity): Long

    @Delete
    suspend fun delete(entry: MealPlanEntity)

    @Query("SELECT * FROM meal_plan ORDER BY mealType, name")
    fun getAll(): Flow<List<MealPlanEntity>>

    @Query("DELETE FROM meal_plan")
    suspend fun deleteAll()
}
