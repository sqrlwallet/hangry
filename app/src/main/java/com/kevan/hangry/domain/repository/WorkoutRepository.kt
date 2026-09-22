package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkoutRepository {
    fun getAllSessions(): Flow<List<ExerciseSessionEntity>>
    fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>>
    suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity>
    suspend fun insertSessions(sessions: List<ExerciseSessionEntity>): List<Long>
    suspend fun deleteAll()
}
