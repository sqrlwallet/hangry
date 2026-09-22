package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface JournalRepository {
    fun getEntryForDate(date: LocalDate): Flow<JournalEntryEntity?>
    fun getAllEntries(): Flow<List<JournalEntryEntity>>
    suspend fun saveEntry(entry: JournalEntryEntity)
    suspend fun deleteForDate(date: LocalDate)
    suspend fun deleteAll()
}
