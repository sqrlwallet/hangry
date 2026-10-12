package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kevan.hangry.data.local.entity.CoachConversationEntity
import com.kevan.hangry.data.local.entity.CoachConversationSummary
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CoachConversationDao {
    @Insert
    suspend fun insert(conversation: CoachConversationEntity): Long

    @Query("SELECT * FROM coach_conversations WHERE id = :id")
    suspend fun getById(id: Long): CoachConversationEntity?

    @Query("UPDATE coach_conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE coach_conversations SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Instant)

    @Query("DELETE FROM coach_conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM coach_conversations")
    suspend fun deleteAll()

    /** Newest first, with a message count and the latest message for the preview. */
    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt,
            (SELECT COUNT(*) FROM coach_messages m WHERE m.conversationId = c.id) AS messageCount,
            (SELECT m.content FROM coach_messages m WHERE m.conversationId = c.id ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage
        FROM coach_conversations c
        ORDER BY c.updatedAt DESC, c.id DESC
        """
    )
    fun observeSummaries(): Flow<List<CoachConversationSummary>>

    @Query("SELECT id FROM coach_conversations ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getMostRecentId(): Long?
}
