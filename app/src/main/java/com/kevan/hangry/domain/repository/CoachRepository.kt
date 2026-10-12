package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.model.CoachResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface CoachRepository {
    /** One chat's messages, oldest first. */
    fun getMessages(conversationId: Long): Flow<List<CoachMessageEntity>>
    fun getJournalEntries(): Flow<List<CoachJournalEntity>>
    suspend fun deleteJournalEntry(id: Long)
    suspend fun addJournalEntry(category: String, summary: String, content: String, sourceMessage: String? = null): Long
    /** Deletes every chat. */
    suspend fun clearMessages()
    suspend fun askCoach(userMessage: String): Result<CoachResponse>

    /** Every chat, most recently used first. */
    fun getConversations(): Flow<List<CoachConversationSummary>> = flowOf(emptyList())

    /** The chat to reopen when Dash is opened, or null when there are none. */
    suspend fun mostRecentConversationId(): Long? = null

    /** Starts a chat titled from its first message; returns its id. */
    suspend fun startConversation(firstMessage: String): Long = 0

    suspend fun renameConversation(id: Long, title: String) {}

    /** Deletes a chat, its messages and their photos. Memories it saved in the journal stay. */
    suspend fun deleteConversation(id: Long) {}

    /** Ids of chats with [query] in the title or any message. */
    suspend fun searchConversations(query: String): Set<Long> = emptySet()

    /** Sends a message in chat [conversationId], streaming the reply. Dash sees that chat's recent messages. */
    suspend fun askCoach(
        conversationId: Long,
        userMessage: String,
        imageUris: List<android.net.Uri>,
        onPartialReply: (String) -> Unit
    ): Result<CoachResponse> = askCoach(userMessage, imageUris, onPartialReply)

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
