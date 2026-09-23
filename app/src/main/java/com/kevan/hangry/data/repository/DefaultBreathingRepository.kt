package com.kevan.hangry.data.repository

import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.dao.BreathingSessionDao
import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.BreathingStats
import com.kevan.hangry.domain.repository.BreathingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DefaultBreathingRepository(
    private val breathingSessionDao: BreathingSessionDao,
    private val healthConnectDataSource: HealthConnectDataSource
) : BreathingRepository {

    override fun observeStats(): Flow<BreathingStats> {
        val today = LocalDate.now()
        return breathingSessionDao.getSessionsBetween(today.minusDays(6), today).map { sessions ->
            val todaySessions = sessions.filter { it.date == today }
            BreathingStats(
                minutesToday = todaySessions.sumOf { it.durationSeconds } / 60,
                sessionsToday = todaySessions.size,
                minutesThisWeek = sessions.sumOf { it.durationSeconds } / 60
            )
        }
    }

    override fun getRecentSessions(limit: Int): Flow<List<BreathingSessionEntity>> {
        return breathingSessionDao.getRecentSessions(limit)
    }

    override suspend fun saveSession(session: BreathingSessionEntity): BreathingSessionEntity {
        val id = breathingSessionDao.insert(session.copy(healthConnectSynced = false))
        val stored = session.copy(id = id, healthConnectSynced = false)
        val synced = writeToHealthConnect(stored)
        return stored.copy(healthConnectSynced = synced)
    }

    override suspend fun syncPendingSessions(): Int {
        if (!healthConnectDataSource.hasBreathingWritePermissions()) return 0
        return breathingSessionDao.getUnsyncedSessions().count { writeToHealthConnect(it) }
    }

    private suspend fun writeToHealthConnect(session: BreathingSessionEntity): Boolean {
        val title = BreathingPattern.fromId(session.patternId).title
        val written = healthConnectDataSource.writeBreathingSession(session, title)
        if (written) breathingSessionDao.setHealthConnectSynced(session.id, true)
        return written
    }
}
