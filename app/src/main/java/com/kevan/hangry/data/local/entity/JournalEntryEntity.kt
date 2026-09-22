package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    val date: LocalDate,
    val perceivedRecovery: Int = 3, // 1 (exhausted) to 5 (energized)
    val stressLevel: Int? = null,
    val muscleSoreness: Int? = null,
    val notes: String? = null,
    val updatedAt: Instant = Instant.now()
)
