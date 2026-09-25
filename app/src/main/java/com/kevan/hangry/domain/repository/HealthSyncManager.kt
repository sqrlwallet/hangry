package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.HrvFeeling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

interface HealthSyncManager {
    fun syncHistorical(days: Int): Flow<SyncProgress>
    fun syncRecent(): Flow<SyncProgress>
    fun recalculateAllBaselines(): Flow<SyncProgress>
    fun getSyncStates(): Flow<List<SyncStateEntity>>
    suspend fun clearAllData()

    /** Recomputes stored summaries/scores once when the scoring algorithm has changed since they were saved. */
    suspend fun recalculateIfScoringChanged() {}

    /** The user's feeling pick for a day without HRV, or null if they haven't changed the default. */
    fun observeHrvFeeling(date: LocalDate): Flow<HrvFeeling?> = flowOf(null)

    /** Saves the pick and immediately recomputes that day's recovery score with it. */
    suspend fun setHrvFeeling(date: LocalDate, feeling: HrvFeeling) {}

    /**
     * Quietly pulls today's heart rate, workouts and steps and recomputes today, so strain and
     * activity keep up as the day goes on. Skipped (false) while a full sync is running.
     */
    suspend fun refreshToday(): Boolean = false
}
