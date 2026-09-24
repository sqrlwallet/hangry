package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kevan.hangry.data.local.entity.MealPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MealPlanEntity): Long

    @Update
    suspend fun update(entry: MealPlanEntity)

    @Delete
    suspend fun delete(entry: MealPlanEntity)

    /** Most recently eaten first, so the foods someone has most days sit at the top. */
    @Query("SELECT * FROM meal_plan ORDER BY lastUsedAt IS NULL, lastUsedAt DESC, useCount DESC, name COLLATE NOCASE")
    fun getAll(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByKey(nameKey: String): MealPlanEntity?

    @Query("DELETE FROM meal_plan")
    suspend fun deleteAll()
}
