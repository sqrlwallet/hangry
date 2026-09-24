package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.MealPlanEntity
import kotlinx.coroutines.flow.Flow

interface MealPlanRepository {
    fun getAll(): Flow<List<MealPlanEntity>>

    /** Adds a meal, or replaces the saved meal with the same name. */
    suspend fun upsert(entry: MealPlanEntity): Long
    suspend fun delete(entry: MealPlanEntity)
    suspend fun deleteAll()

    /**
     * Keeps the saved-meals library in step with what the user logs: a new food is saved, a
     * known one takes the latest numbers. [countAsUse] is false for corrections to an entry
     * that was already counted when it was logged.
     */
    suspend fun rememberFromLog(entry: FoodLogEntity, countAsUse: Boolean = true)
}
