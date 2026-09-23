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

    /** Sends a message with photos attached (supplement labels, meals, lab reports...). */
    suspend fun askCoach(userMessage: String, imageUris: List<android.net.Uri>): Result<CoachResponse> = askCoach(userMessage)

    /** Streams the reply: [onPartialReply] gets the readable text so far as Dash writes it. */
    suspend fun askCoach(
        userMessage: String,
        imageUris: List<android.net.Uri>,
        onPartialReply: (String) -> Unit
    ): Result<CoachResponse> = askCoach(userMessage, imageUris)

    /** Runs action [index] of assistant message [messageId] after the user taps it. */
    suspend fun executeAction(messageId: Long, index: Int): Result<String> =
        Result.failure(UnsupportedOperationException("Actions aren't supported here."))

    suspend fun dismissAction(messageId: Long, index: Int) {}
}
