package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CoachMessageEntity): Long

    @Query("SELECT * FROM coach_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<CoachMessageEntity>>

    @Query("SELECT * FROM coach_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CoachMessageEntity>

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAll()
}
