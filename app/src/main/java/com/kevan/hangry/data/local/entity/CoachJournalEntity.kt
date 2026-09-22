package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Stores personal facts, problems, food sensitivities, injuries, lifestyle factors, or health
 * goals shared by the user with the AI Coach. These entries are fed back into the AI Coach's
 * context window so it maintains memory across conversations and days.
 */
@Entity(
    tableName = "coach_journal_entries",
    indices = [Index(value = ["date"])]
)
data class CoachJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val timestamp: Instant = Instant.now(),
    val category: String, // "PROBLEM", "DIET", "INJURY", "HABIT", "GOAL", "HEALTH", "NOTE"
    val summary: String,
    val content: String,
    val sourceMessage: String? = null
)
