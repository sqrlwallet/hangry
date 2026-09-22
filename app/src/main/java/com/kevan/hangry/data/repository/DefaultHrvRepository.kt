package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.HrvDao
import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import com.kevan.hangry.domain.repository.HRVRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultHrvRepository(
    private val dao: HrvDao
) : HRVRepository {
    override fun getHrvBetween(start: LocalDate, end: LocalDate): Flow<List<HrvMeasurementEntity>> =
        dao.getBetween(start, end)

    override suspend fun getHrvForDate(date: LocalDate): HrvMeasurementEntity? =
        dao.getForDate(date)

    override suspend fun getAverageHrvBetween(start: LocalDate, end: LocalDate): Double? =
        dao.getAverageBetween(start, end)

    override suspend fun insertHrvMeasurements(records: List<HrvMeasurementEntity>): List<Long> =
        dao.insertOrIgnore(records)

    override suspend fun deleteAll() = dao.deleteAll()
}
