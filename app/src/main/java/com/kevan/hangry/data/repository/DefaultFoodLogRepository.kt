package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.domain.repository.FoodLogRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultFoodLogRepository(
    private val dao: FoodLogDao
) : FoodLogRepository {
    override fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>> = dao.getForDate(date)

    override fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>> =
        dao.getBetween(start, end)

    override fun getTotalCaloriesForDate(date: LocalDate): Flow<Int> = dao.getTotalCaloriesForDate(date)

    override suspend fun insert(entry: FoodLogEntity): Long = dao.insert(entry)

    override suspend fun update(entry: FoodLogEntity) = dao.update(entry)

    override suspend fun delete(entry: FoodLogEntity) = dao.delete(entry)

    override suspend fun markSyncedToHealthConnect(id: Long) = dao.markSyncedToHealthConnect(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
