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

    @Query("DELETE FROM coach_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM coach_messages WHERE id = :id")
    suspend fun getById(id: Long): CoachMessageEntity?

    @Query("UPDATE coach_messages SET actionsJson = :actionsJson WHERE id = :id")
    suspend fun updateActions(id: Long, actionsJson: String)

    @Query("SELECT * FROM coach_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<CoachMessageEntity>>

    @Query("SELECT * FROM coach_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CoachMessageEntity>

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAll()
}
