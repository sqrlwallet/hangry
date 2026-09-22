package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class DefaultWorkoutRepository(
    private val dao: ExerciseSessionDao
) : WorkoutRepository {
    override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = dao.getAllSessions()

    override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> =
        dao.getSessionsBetween(start, end)

    override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> =
        dao.getSessionsBetweenList(start, end)

    override suspend fun insertSessions(sessions: List<ExerciseSessionEntity>): List<Long> =
        dao.insertOrIgnore(sessions)

    override suspend fun deleteAll() = dao.deleteAll()
}
