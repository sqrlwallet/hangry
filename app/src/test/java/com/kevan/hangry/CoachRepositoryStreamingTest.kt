package com.kevan.hangry

import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.ai.isRetryable
import com.kevan.hangry.data.local.dao.CoachConversationDao
import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.CoachMessageDao
import com.kevan.hangry.data.local.entity.CoachConversationEntity
import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.data.repository.DefaultCoachRepository
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.model.CoachResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The streaming/stop/regenerate behaviour of the coach repository: a reply that is interrupted
 * (Stop, or a dropped connection) keeps whatever Dash managed to say, and regenerate swaps the
 * last reply without duplicating the user's message.
 */
class CoachRepositoryStreamingTest {

    private class FakeMessageDao : CoachMessageDao {
        val messages = mutableListOf<CoachMessageEntity>()
        private var nextId = 1L

        override suspend fun insert(message: CoachMessageEntity): Long {
            val id = if (message.id != 0L) message.id else nextId++
            messages.removeAll { it.id == id }
            messages.add(message.copy(id = id))
            return id
        }

        override suspend fun deleteById(id: Long) { messages.removeAll { it.id == id } }
        override suspend fun getById(id: Long) = messages.firstOrNull { it.id == id }
        override suspend fun updateActions(id: Long, actionsJson: String) {
            val i = messages.indexOfFirst { it.id == id }
            if (i >= 0) messages[i] = messages[i].copy(actionsJson = actionsJson)
        }

        override fun observeConversation(conversationId: Long): Flow<List<CoachMessageEntity>> =
            MutableStateFlow(getForConv(conversationId)).asStateFlow()

        override suspend fun getRecent(conversationId: Long, limit: Int) =
            getForConv(conversationId).takeLast(limit)

        override suspend fun getForConversation(conversationId: Long) = getForConv(conversationId)
        override suspend fun countForConversation(conversationId: Long) = messages.count { it.conversationId == conversationId }
        override suspend fun deleteConversation(conversationId: Long) { messages.removeAll { it.conversationId == conversationId } }
        override suspend fun searchConversationIds(query: String) =
            messages.filter { it.content.contains(query, ignoreCase = true) }.map { it.conversationId }.distinct()
        override suspend fun deleteAll() { messages.clear() }

        private fun getForConv(id: Long) = messages.filter { it.conversationId == id }.sortedBy { it.timestamp }
    }

    private class FakeConversationDao : CoachConversationDao {
        val conversations = mutableMapOf<Long, CoachConversationEntity>()
        private var nextId = 1L

        override suspend fun insert(conversation: CoachConversationEntity): Long {
            val id = if (conversation.id != 0L) conversation.id else nextId++
            conversations[id] = conversation.copy(id = id)
            return id
        }

        override suspend fun getById(id: Long) = conversations[id]
        override suspend fun rename(id: Long, title: String) { conversations[id]?.let { conversations[id] = it.copy(title = title) } }
        override suspend fun touch(id: Long, at: Instant) { conversations[id]?.let { conversations[id] = it.copy(updatedAt = at) } }
        override suspend fun delete(id: Long) { conversations.remove(id) }
        override suspend fun deleteAll() { conversations.clear() }
        override fun observeSummaries(): Flow<List<CoachConversationSummary>> =
            MutableStateFlow<List<CoachConversationSummary>>(emptyList()).asStateFlow()
        override suspend fun getMostRecentId(): Long? = conversations.keys.maxOrNull()
    }

    private class FakeJournalDao : CoachJournalDao {
        val entries = mutableListOf<CoachJournalEntity>()
        private var nextId = 1L
        override suspend fun insert(entry: CoachJournalEntity): Long {
            val id = nextId++
            entries.add(entry.copy(id = id))
            return id
        }
        override suspend fun delete(id: Long) { entries.removeAll { it.id == id } }
        override fun getAll(): Flow<List<CoachJournalEntity>> = MutableStateFlow(entries.toList()).asStateFlow()
        override suspend fun getAllSync() = entries.toList()
        override suspend fun deleteAll() { entries.clear() }
    }

    private class FakeCoachService : AiCoachService {
        var behavior: suspend (String, ((String) -> Unit)?) -> Result<CoachResponse> =
            { _: String, _: ((String) -> Unit)? -> Result.success(CoachResponse(reply = "ok")) }

        override suspend fun askCoach(
            userMessage: String,
            recentMessages: List<CoachMessageEntity>,
            imagesBase64: List<String>,
            onPartialReply: ((String) -> Unit)?
        ): Result<CoachResponse> = behavior(userMessage, onPartialReply)
    }

    private fun repository(
        messageDao: FakeMessageDao = FakeMessageDao(),
        conversationDao: FakeConversationDao = FakeConversationDao(),
        journalDao: FakeJournalDao = FakeJournalDao(),
        service: FakeCoachService = FakeCoachService()
    ) = DefaultCoachRepository(journalDao, messageDao, conversationDao, service)

    private fun message(conversationId: Long, role: String, content: String, seconds: Long) = CoachMessageEntity(
        conversationId = conversationId,
        timestamp = Instant.ofEpochSecond(seconds),
        role = role,
        content = content
    )

    @Test
    fun stopMidStream_keepsPartialReplyAndUserMessage() = runTest {
        val messageDao = FakeMessageDao()
        val conversationDao = FakeConversationDao()
        conversationDao.insert(CoachConversationEntity(id = 4, title = "Chat"))
        val service = FakeCoachService().apply {
            behavior = { _, onPartial ->
                onPartial?.invoke("{\"reply\":\"Half an answer")
                throw CancellationException("stopped")
            }
        }
        val repo = repository(messageDao, conversationDao, service = service)

        val result = runCatching { repo.askCoach(4L, "help me", emptyList()) {} }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(2, messageDao.messages.size)
        assertEquals("user", messageDao.messages[0].role)
        assertEquals("Half an answer", messageDao.messages[1].content)
        assertEquals("assistant", messageDao.messages[1].role)
    }

    @Test
    fun stopBeforeAnyText_rollsBackTheTurn() = runTest {
        val messageDao = FakeMessageDao()
        val service = FakeCoachService().apply { behavior = { _, _ -> throw CancellationException("stopped") } }
        val repo = repository(messageDao, service = service)

        runCatching { repo.askCoach(7L, "hello", emptyList()) {} }

        assertTrue(messageDao.messages.isEmpty())
    }

    @Test
    fun droppedConnectionAfterText_keepsPartialReplyAsSuccess() = runTest {
        val messageDao = FakeMessageDao()
        val service = FakeCoachService().apply {
            behavior = { _, onPartial ->
                onPartial?.invoke("{\"reply\":\"Almost there")
                Result.failure(OpenRouterException.NoNetwork(IOException("boom")))
            }
        }
        val repo = repository(messageDao, service = service)

        val result = repo.askCoach(2L, "status?", emptyList()) {}

        assertTrue(result.isSuccess)
        assertEquals("Almost there", result.getOrThrow().reply)
        assertEquals(2, messageDao.messages.size)
        assertEquals("Almost there", messageDao.messages[1].content)
    }

    @Test
    fun dropBeforeAnyText_rollsBackAndFails() = runTest {
        val messageDao = FakeMessageDao()
        val service = FakeCoachService().apply {
            behavior = { _, _ -> Result.failure(OpenRouterException.NoNetwork(IOException("boom"))) }
        }
        val repo = repository(messageDao, service = service)

        val result = repo.askCoach(3L, "hello", emptyList()) {}

        assertTrue(result.isFailure)
        assertTrue(messageDao.messages.isEmpty())
    }

    @Test
    fun regenerate_replacesLastReplyWithoutDuplicatingUserMessage() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.insert(message(5L, "user", "How am I doing?", 100))
        messageDao.insert(message(5L, "assistant", "Old answer", 101))
        val service = FakeCoachService().apply {
            behavior = { _, _ -> Result.success(CoachResponse(reply = "New answer")) }
        }
        val repo = repository(messageDao, service = service)

        val result = repo.regenerateLastReply(5L) {}

        assertTrue(result.isSuccess)
        assertEquals("New answer", result.getOrThrow().reply)
        assertEquals(2, messageDao.messages.size)
        assertEquals("user", messageDao.messages[0].role)
        assertEquals("How am I doing?", messageDao.messages[0].content)
        assertEquals("New answer", messageDao.messages[1].content)
    }

    @Test
    fun regenerate_failureWithoutText_restoresOldReply() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.insert(message(8L, "user", "Question", 200))
        messageDao.insert(message(8L, "assistant", "Old answer", 201))
        val service = FakeCoachService().apply {
            behavior = { _, _ -> Result.failure(OpenRouterException.ServerError(500)) }
        }
        val repo = repository(messageDao, service = service)

        val result = repo.regenerateLastReply(8L) {}

        assertTrue(result.isFailure)
        assertEquals(2, messageDao.messages.size)
        assertEquals("Old answer", messageDao.messages[1].content)
    }

    @Test
    fun regenerate_withNoUserMessage_fails() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.insert(message(6L, "assistant", "orphan", 1))
        val repo = repository(messageDao)

        val result = repo.regenerateLastReply(6L) {}

        assertTrue(result.isFailure)
    }

    @Test
    fun retryableClassification() {
        assertTrue(isRetryable(OpenRouterException.RateLimited()))
        assertTrue(isRetryable(OpenRouterException.NoNetwork(IOException("x"))))
        assertTrue(isRetryable(OpenRouterException.ServerError(503)))
        assertFalse(isRetryable(OpenRouterException.ServerError(400)))
        assertFalse(isRetryable(OpenRouterException.InvalidApiKey()))
        assertFalse(isRetryable(OpenRouterException.EmptyResponse()))
        assertFalse(isRetryable(IllegalStateException("nope")))
    }
}
