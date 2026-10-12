package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** One chat with Dash. Its messages point back to it through CoachMessageEntity.conversationId. */
@Entity(
    tableName = "coach_conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class CoachConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** From the first message; the user can rename it. */
    val title: String,
    val createdAt: Instant = Instant.now(),
    /** Last message time, so the history lists the most recent chats first. */
    val updatedAt: Instant = Instant.now()
)

/** A chat as the history list shows it. */
data class CoachConversationSummary(
    val id: Long,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Int,
    /** The newest message, for a one-line preview. */
    val lastMessage: String?
)
