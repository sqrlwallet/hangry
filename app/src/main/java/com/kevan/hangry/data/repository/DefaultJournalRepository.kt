package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.JournalDao
import com.kevan.hangry.data.local.entity.JournalEntryEntity
import com.kevan.hangry.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultJournalRepository(
    private val dao: JournalDao
) : JournalRepository {
    override fun getEntryForDate(date: LocalDate): Flow<JournalEntryEntity?> = dao.getEntryForDate(date)

    override fun getAllEntries(): Flow<List<JournalEntryEntity>> = dao.getAllEntries()

    override suspend fun saveEntry(entry: JournalEntryEntity) = dao.insertOrReplace(entry)

    override suspend fun deleteForDate(date: LocalDate) = dao.deleteForDate(date)

    override suspend fun deleteAll() = dao.deleteAll()
}
