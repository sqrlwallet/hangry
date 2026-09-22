package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.SleepSessionDao
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class DefaultSleepRepository(
    private val dao: SleepSessionDao
) : SleepRepository {
    override fun getLatestSession(): Flow<SleepSessionEntity?> = dao.getLatestSession()

    override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>> =
        dao.getSessionsBetween(start, end)

    override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity> =
        dao.getSessionsBetweenList(start, end)

    override suspend fun insertSessions(sessions: List<SleepSessionEntity>): List<Long> =
        dao.insertOrIgnore(sessions)

    override suspend fun deleteAll() = dao.deleteAll()
}
