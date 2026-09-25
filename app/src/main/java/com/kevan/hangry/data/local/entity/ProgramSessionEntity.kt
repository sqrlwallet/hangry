package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** A finished session of one of the guided programs: the level done and how it felt. */
@Entity(tableName = "program_sessions", indices = [Index(value = ["programId", "date"]), Index(value = ["date"])])
data class ProgramSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Program id, see ProgramCatalog. */
    val programId: String,
    val date: LocalDate,
    val level: Int,
    /** ProgramFeel name. */
    val feel: String,
    val completedAt: Instant = Instant.now()
)
