package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Every food the user logs or corrects is also remembered in [savedMeals] (when given), so it can
 * be logged again from Saved Meals without retyping or another AI call.
 */
class DefaultFoodLogRepository(
    private val dao: FoodLogDao,
    private val savedMeals: MealPlanRepository? = null
) : FoodLogRepository {
    override fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>> = dao.getForDate(date)

    override fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>> =
        dao.getBetween(start, end)

    override fun getTotalCaloriesForDate(date: LocalDate): Flow<Int> = dao.getTotalCaloriesForDate(date)

    override suspend fun insert(entry: FoodLogEntity): Long {
        val id = dao.insert(entry)
        runCatching { savedMeals?.rememberFromLog(entry) }
        return id
    }

    override suspend fun update(entry: FoodLogEntity) {
        dao.update(entry)
        runCatching { savedMeals?.rememberFromLog(entry, countAsUse = false) }
    }

    override suspend fun delete(entry: FoodLogEntity) = dao.delete(entry)

    override suspend fun markSyncedToHealthConnect(id: Long) = dao.markSyncedToHealthConnect(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
