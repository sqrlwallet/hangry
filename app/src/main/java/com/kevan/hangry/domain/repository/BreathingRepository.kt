package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import com.kevan.hangry.domain.model.BreathingStats
import kotlinx.coroutines.flow.Flow

interface BreathingRepository {
    /** Today's and the last 7 days' breathing totals. */
    fun observeStats(): Flow<BreathingStats>
    fun getRecentSessions(limit: Int = 10): Flow<List<BreathingSessionEntity>>

    /**
     * Saves the session locally, then tries to write it to Health Connect.
     * Returns the stored session, with [BreathingSessionEntity.healthConnectSynced] reflecting the write.
     */
    suspend fun saveSession(session: BreathingSessionEntity): BreathingSessionEntity

    /** Retries the Health Connect write for sessions saved before write access was granted. */
    suspend fun syncPendingSessions(): Int
}
