package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    fun getLatestSession(): Flow<SleepSessionEntity?>
    fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>>
    suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity>
    suspend fun insertSessions(sessions: List<SleepSessionEntity>): List<Long>
    suspend fun deleteAll()
}
