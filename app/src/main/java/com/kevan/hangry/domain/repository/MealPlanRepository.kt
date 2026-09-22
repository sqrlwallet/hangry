package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.MealPlanEntity
import kotlinx.coroutines.flow.Flow

interface MealPlanRepository {
    fun getAll(): Flow<List<MealPlanEntity>>
    suspend fun upsert(entry: MealPlanEntity): Long
    suspend fun delete(entry: MealPlanEntity)
    suspend fun deleteAll()
}
