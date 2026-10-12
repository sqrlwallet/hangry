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

    @Query("SELECT * FROM coach_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeConversation(conversationId: Long): Flow<List<CoachMessageEntity>>

    /** The latest [limit] messages of one chat, newest first - what Dash is given as context. */
    @Query("SELECT * FROM coach_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(conversationId: Long, limit: Int): List<CoachMessageEntity>

    @Query("SELECT * FROM coach_messages WHERE conversationId = :conversationId")
    suspend fun getForConversation(conversationId: Long): List<CoachMessageEntity>

    @Query("SELECT COUNT(*) FROM coach_messages WHERE conversationId = :conversationId")
    suspend fun countForConversation(conversationId: Long): Int

    @Query("DELETE FROM coach_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    /** Chats with a message containing [query] (case-insensitive for ASCII). */
    @Query("SELECT DISTINCT conversationId FROM coach_messages WHERE content LIKE '%' || :query || '%'")
    suspend fun searchConversationIds(query: String): List<Long>

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAll()
}
