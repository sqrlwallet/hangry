package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Stores individual chat messages in the conversation between the user and the AI Coach.
 */
@Entity(
    tableName = "coach_messages",
    indices = [Index(value = ["timestamp"])]
)
data class CoachMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Instant = Instant.now(),
    val role: String, // "user" or "assistant"
    val content: String,
    val journalEntrySummary: String? = null,
    /** Comma-separated paths of photos the user attached to this message. */
    val imagePaths: String? = null,
    /** JSON list of CoachAction Dash proposed with this reply (with each one's status). */
    val actionsJson: String? = null
)
