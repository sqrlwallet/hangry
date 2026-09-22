package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.MealPlanDao
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow

class DefaultMealPlanRepository(
    private val dao: MealPlanDao
) : MealPlanRepository {
    override fun getAll(): Flow<List<MealPlanEntity>> = dao.getAll()
    override suspend fun upsert(entry: MealPlanEntity): Long = dao.upsert(entry)
    override suspend fun delete(entry: MealPlanEntity) = dao.delete(entry)
    override suspend fun deleteAll() = dao.deleteAll()
}
