package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.StepsSummaryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ActivityRepository {
    fun getStepsBetween(start: LocalDate, end: LocalDate): Flow<List<StepsSummaryEntity>>
    suspend fun getStepsForDate(date: LocalDate): StepsSummaryEntity?
    suspend fun insertStepsSummaries(records: List<StepsSummaryEntity>): List<Long>
    suspend fun deleteAll()
}
