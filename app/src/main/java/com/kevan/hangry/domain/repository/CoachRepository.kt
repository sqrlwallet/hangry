package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.model.CoachResponse
import kotlinx.coroutines.flow.Flow

interface CoachRepository {
    fun getMessages(): Flow<List<CoachMessageEntity>>
    fun getJournalEntries(): Flow<List<CoachJournalEntity>>
    suspend fun deleteJournalEntry(id: Long)
    suspend fun addJournalEntry(category: String, summary: String, content: String, sourceMessage: String? = null): Long
    suspend fun clearMessages()
    suspend fun askCoach(userMessage: String): Result<CoachResponse>
}
