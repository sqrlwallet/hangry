package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.StepsDao
import com.kevan.hangry.data.local.entity.StepsSummaryEntity
import com.kevan.hangry.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultActivityRepository(
    private val dao: StepsDao
) : ActivityRepository {
    override fun getStepsBetween(start: LocalDate, end: LocalDate): Flow<List<StepsSummaryEntity>> =
        dao.getBetween(start, end)

    override suspend fun getStepsForDate(date: LocalDate): StepsSummaryEntity? =
        dao.getForDate(date)

    override suspend fun insertStepsSummaries(records: List<StepsSummaryEntity>): List<Long> =
        dao.insertOrIgnore(records)

    override suspend fun deleteAll() = dao.deleteAll()
}
