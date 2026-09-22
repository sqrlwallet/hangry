package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.domain.model.SyncProgress
import kotlinx.coroutines.flow.Flow

interface HealthSyncManager {
    fun syncHistorical(days: Int): Flow<SyncProgress>
    fun syncRecent(): Flow<SyncProgress>
    fun recalculateAllBaselines(): Flow<SyncProgress>
    fun getSyncStates(): Flow<List<SyncStateEntity>>
    suspend fun clearAllData()
}
