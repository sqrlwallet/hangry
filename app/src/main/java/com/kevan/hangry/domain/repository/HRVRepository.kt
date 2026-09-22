package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HRVRepository {
    fun getHrvBetween(start: LocalDate, end: LocalDate): Flow<List<HrvMeasurementEntity>>
    suspend fun getHrvForDate(date: LocalDate): HrvMeasurementEntity?
    suspend fun getAverageHrvBetween(start: LocalDate, end: LocalDate): Double?
    suspend fun insertHrvMeasurements(records: List<HrvMeasurementEntity>): List<Long>
    suspend fun deleteAll()
}
