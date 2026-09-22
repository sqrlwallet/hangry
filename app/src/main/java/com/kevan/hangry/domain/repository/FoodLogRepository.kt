package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.FoodLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodLogRepository {
    fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>>
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>>
    fun getTotalCaloriesForDate(date: LocalDate): Flow<Int>
    suspend fun insert(entry: FoodLogEntity): Long
    suspend fun update(entry: FoodLogEntity)
    suspend fun delete(entry: FoodLogEntity)
    suspend fun markSyncedToHealthConnect(id: Long)
    suspend fun deleteAll()
}
