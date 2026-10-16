package com.kevan.hangry.data.repository

import android.content.Context
import android.net.Uri
import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.CoachConversationDao
import com.kevan.hangry.data.local.dao.CoachMessageDao
import com.kevan.hangry.data.local.entity.CoachConversationEntity
import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.ai.StreamingReply
import com.kevan.hangry.domain.model.ChatTitle
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.CoachActionStatus
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.repository.CoachRepository
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate

class DefaultCoachRepository(
    private val coachJournalDao: CoachJournalDao,
    private val coachMessageDao: CoachMessageDao,
    private val coachConversationDao: CoachConversationDao,
    private val aiCoachService: AiCoachService,
    /** Both optional so the repository still works without photo/action support (e.g. tests). */
    private val context: Context? = null,
    private val actionExecutor: CoachActionExecutor? = null
) : CoachRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val actionsSerializer = ListSerializer(CoachAction.serializer())

    override fun getMessages(conversationId: Long): Flow<List<CoachMessageEntity>> = coachMessageDao.observeConversation(conversationId)

    override fun getConversations(): Flow<List<CoachConversationSummary>> = coachConversationDao.observeSummaries()

    override suspend fun mostRecentConversationId(): Long? = coachConversationDao.getMostRecentId()

    override suspend fun startConversation(firstMessage: String): Long =
        coachConversationDao.insert(CoachConversationEntity(title = ChatTitle.from(firstMessage)))

    override suspend fun renameConversation(id: Long, title: String) {
        title.trim().take(ChatTitle.MAX_LENGTH).takeIf { it.isNotBlank() }?.let { coachConversationDao.rename(id, it) }
    }

    override suspend fun deleteConversation(id: Long) {
        val photos = coachMessageDao.getForConversation(id).flatMap { it.imagePaths?.split(",").orEmpty() }
        coachMessageDao.deleteConversation(id)
        coachConversationDao.delete(id)
        withContext(Dispatchers.IO) { photos.filter { it.isNotBlank() }.forEach { File(it).delete() } }
    }

    override suspend fun searchConversations(query: String): Set<Long> {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return emptySet()
        // LIKE wildcards in what the user typed are matched literally enough for chat search.
        return coachMessageDao.searchConversationIds(q).toSet()
    }

    override fun getJournalEntries(): Flow<List<CoachJournalEntity>> = coachJournalDao.getAll()

    override suspend fun deleteJournalEntry(id: Long) {
        coachJournalDao.delete(id)
    }

    override suspend fun addJournalEntry(
        category: String,
        summary: String,
        content: String,
        sourceMessage: String?
    ): Long {
        val entry = CoachJournalEntity(
            date = LocalDate.now(),
            timestamp = Instant.now(),
            category = category,
            summary = summary,
            content = content,
            sourceMessage = sourceMessage
        )
        return coachJournalDao.insert(entry)
    }

    override suspend fun clearMessages() {
        coachMessageDao.deleteAll()
        coachConversationDao.deleteAll()
        context?.let { ctx -> withContext(Dispatchers.IO) { File(ctx.filesDir, IMAGE_DIR).listFiles()?.forEach { it.delete() } } }
    }

    /** Without a chat id: continues the most recent chat, or starts one. */
    override suspend fun askCoach(userMessage: String): Result<CoachResponse> = askCoach(userMessage, emptyList())

    override suspend fun askCoach(userMessage: String, imageUris: List<Uri>): Result<CoachResponse> =
        ask(currentOrNewConversation(userMessage), userMessage, imageUris, onPartialReply = null)

    override suspend fun askCoach(
        userMessage: String,
        imageUris: List<Uri>,
        onPartialReply: (String) -> Unit
    ): Result<CoachResponse> = ask(currentOrNewConversation(userMessage), userMessage, imageUris, onPartialReply)

    override suspend fun askCoach(
        conversationId: Long,
        userMessage: String,
        imageUris: List<Uri>,
        onPartialReply: (String) -> Unit
    ): Result<CoachResponse> = ask(conversationId, userMessage, imageUris, onPartialReply)

    private suspend fun currentOrNewConversation(firstMessage: String): Long =
        coachConversationDao.getMostRecentId() ?: startConversation(firstMessage)

    private suspend fun ask(conversationId: Long, userMessage: String, imageUris: List<Uri>, onPartialReply: ((String) -> Unit)?): Result<CoachResponse> {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank() && imageUris.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be blank"))
        }
        val text = trimmed.ifBlank { "Here's a photo." }

        // 1. This chat's recent messages, before saving the current turn. Other chats stay out
        //    of it; what Dash should always remember lives in the journal.
        val priorHistory = coachMessageDao.getRecent(conversationId, 10).reversed()

        // 2. Keep the photos (for the chat bubble) and encode them for the model
        val storedPaths = storeImages(imageUris)
        val imagesBase64 = context?.let { ctx ->
            withContext(Dispatchers.IO) { storedPaths.mapNotNull { ctx.readImageAsBase64Jpeg(Uri.fromFile(File(it))) } }
        }.orEmpty()

        // 3. Save user turn
        val userMessageId = coachMessageDao.insert(
            CoachMessageEntity(
                conversationId = conversationId,
                timestamp = Instant.now(),
                role = "user",
                content = text,
                imagePaths = storedPaths.takeIf { it.isNotEmpty() }?.joinToString(",")
            )
        )

        // 4. Ask Dash, remembering what it has written so far so a Stop or a dropped connection
        //    can still keep the partial reply instead of losing the whole turn.
        var partial = ""
        val streamingPartial: ((String) -> Unit)? = onPartialReply?.let { deliver ->
            { seen -> partial = seen; deliver(seen) }
        }
        val result = try {
            aiCoachService.askCoach(text, priorHistory, imagesBase64, streamingPartial)
        } catch (e: CancellationException) {
            // The user tapped Stop or left the screen mid-reply.
            withContext(NonCancellable + Dispatchers.IO) { settleInterruptedTurn(conversationId, userMessageId, storedPaths, partial) }
            throw e
        }

        return result.fold(
            onSuccess = { response ->
                saveAssistantReply(conversationId, text, response)
                result
            },
            onFailure = {
                // A dropped connection after some text arrived: keep it rather than throw it away.
                val visible = StreamingReply.visibleText(partial).trim()
                if (visible.isNotBlank()) {
                    coachMessageDao.insert(assistantMessage(conversationId, visible))
                    coachConversationDao.touch(conversationId, Instant.now())
                    Result.success(CoachResponse(reply = visible))
                } else {
                    // A failed send leaves no half-conversation behind; the screen restores the text.
                    rollbackTurn(conversationId, userMessageId, storedPaths)
                    result
                }
            }
        )
    }

    override suspend fun regenerateLastReply(conversationId: Long, onPartialReply: (String) -> Unit): Result<CoachResponse> {
        val all = coachMessageDao.getForConversation(conversationId)
        val lastUserIndex = all.indexOfLast { it.role == "user" }
        if (lastUserIndex < 0) return Result.failure(IllegalArgumentException("There's nothing to regenerate."))
        val userMessage = all[lastUserIndex]
        // Replace the reply: drop it and anything after, keeping the user's own message. If the
        // new answer never arrives, the old one is put back rather than leaving a bare question.
        val replaced = all.drop(lastUserIndex + 1)
        replaced.forEach { coachMessageDao.deleteById(it.id) }

        val priorHistory = all.take(lastUserIndex).takeLast(10)
        val storedPaths = userMessage.imagePaths?.split(',').orEmpty().filter { it.isNotBlank() }
        val imagesBase64 = context?.let { ctx ->
            withContext(Dispatchers.IO) { storedPaths.mapNotNull { ctx.readImageAsBase64Jpeg(Uri.fromFile(File(it))) } }
        }.orEmpty()

        var partial = ""
        val result = try {
            aiCoachService.askCoach(userMessage.content, priorHistory, imagesBase64) { seen -> partial = seen; onPartialReply(seen) }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { settleRegenerate(conversationId, partial, replaced) }
            throw e
        }

        return result.fold(
            onSuccess = { response ->
                // No journal memory here: the answer being replaced already saved whatever it found.
                coachMessageDao.insert(assistantMessage(conversationId, response.reply))
                coachConversationDao.touch(conversationId, Instant.now())
                result
            },
            onFailure = {
                val visible = StreamingReply.visibleText(partial).trim()
                if (visible.isNotBlank()) {
                    coachMessageDao.insert(assistantMessage(conversationId, visible))
                    coachConversationDao.touch(conversationId, Instant.now())
                    Result.success(CoachResponse(reply = visible))
                } else {
                    // Couldn't write a new answer: keep the old reply instead of a dead end.
                    replaced.forEach { coachMessageDao.insert(it) }
                    result
                }
            }
        )
    }

    /** Saves Dash's reply plus any journal memory it extracted from the user's message. */
    private suspend fun saveAssistantReply(conversationId: Long, sourceText: String, response: CoachResponse) {
        val extracted = response.journalEntry
        if (extracted != null && extracted.summary.isNotBlank()) {
            coachJournalDao.insert(
                CoachJournalEntity(
                    date = LocalDate.now(),
                    timestamp = Instant.now(),
                    category = extracted.category,
                    summary = extracted.summary,
                    content = extracted.content,
                    sourceMessage = sourceText
                )
            )
        }
        coachMessageDao.insert(
            assistantMessage(
                conversationId = conversationId,
                content = response.reply,
                journalEntrySummary = extracted?.summary,
                actionsJson = response.actions.takeIf { it.isNotEmpty() && actionExecutor != null }
                    ?.let { json.encodeToString(actionsSerializer, it) }
            )
        )
        coachConversationDao.touch(conversationId, Instant.now())
    }

    private fun assistantMessage(
        conversationId: Long,
        content: String,
        journalEntrySummary: String? = null,
        actionsJson: String? = null
    ) = CoachMessageEntity(
        conversationId = conversationId,
        timestamp = Instant.now(),
        role = "assistant",
        content = content,
        journalEntrySummary = journalEntrySummary,
        actionsJson = actionsJson
    )

    /** Undoes a turn that produced nothing: no dangling user message, no orphaned photo. */
    private suspend fun rollbackTurn(conversationId: Long, userMessageId: Long, storedPaths: List<String>) {
        coachMessageDao.deleteById(userMessageId)
        withContext(Dispatchers.IO) { storedPaths.forEach { path -> File(path).delete() } }
        // A brand-new chat whose first message failed isn't kept as an empty chat.
        if (coachMessageDao.countForConversation(conversationId) == 0) coachConversationDao.delete(conversationId)
    }

    /** After Stop or leaving mid-reply: keep a partial answer if there is one, otherwise undo the turn. */
    private suspend fun settleInterruptedTurn(conversationId: Long, userMessageId: Long, storedPaths: List<String>, partial: String) {
        val visible = StreamingReply.visibleText(partial).trim()
        if (visible.isNotBlank()) {
            coachMessageDao.insert(assistantMessage(conversationId, visible))
            coachConversationDao.touch(conversationId, Instant.now())
        } else {
            rollbackTurn(conversationId, userMessageId, storedPaths)
        }
    }

    /**
     * After an interrupted regenerate: keep what Dash managed to write, or put the replaced
     * reply back if it never started.
     */
    private suspend fun settleRegenerate(conversationId: Long, partial: String, replaced: List<CoachMessageEntity>) {
        val visible = StreamingReply.visibleText(partial).trim()
        if (visible.isNotBlank()) {
            coachMessageDao.insert(assistantMessage(conversationId, visible))
            coachConversationDao.touch(conversationId, Instant.now())
        } else {
            replaced.forEach { coachMessageDao.insert(it) }
        }
    }

    override suspend fun executeAction(messageId: Long, index: Int): Result<String> {
        val executor = actionExecutor ?: return Result.failure(IllegalStateException("Actions unavailable."))
        val (message, actions) = loadActions(messageId) ?: return Result.failure(IllegalArgumentException("Action not found."))
        val action = actions.getOrNull(index) ?: return Result.failure(IllegalArgumentException("Action not found."))
        if (action.status == CoachActionStatus.DONE) return Result.success(action.resultMessage ?: "Already done")
        val result = executor.execute(action)
        val updated = action.copy(
            status = if (result.isSuccess) CoachActionStatus.DONE else CoachActionStatus.FAILED,
            resultMessage = result.fold({ it }, { it.message ?: "Couldn't do that." })
        )
        saveActions(message.id, actions.toMutableList().also { it[index] = updated })
        return result
    }

    override suspend fun dismissAction(messageId: Long, index: Int) {
        val (message, actions) = loadActions(messageId) ?: return
        val action = actions.getOrNull(index)?.takeIf { it.status != CoachActionStatus.DONE } ?: return
        saveActions(message.id, actions.toMutableList().also { it[index] = action.copy(status = CoachActionStatus.DISMISSED) })
    }

    private suspend fun loadActions(messageId: Long): Pair<CoachMessageEntity, List<CoachAction>>? {
        val message = coachMessageDao.getById(messageId) ?: return null
        val actions = message.actionsJson?.let { runCatching { json.decodeFromString(actionsSerializer, it) }.getOrNull() } ?: return null
        return message to actions
    }

    private suspend fun saveActions(messageId: Long, actions: List<CoachAction>) {
        coachMessageDao.updateActions(messageId, json.encodeToString(actionsSerializer, actions))
    }

    private suspend fun storeImages(uris: List<Uri>): List<String> {
        val ctx = context ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val dir = File(ctx.filesDir, IMAGE_DIR).apply { mkdirs() }
            uris.mapIndexedNotNull { i, uri ->
                runCatching {
                    val file = File(dir, "chat_${System.currentTimeMillis()}_$i.jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.absolutePath.takeIf { file.length() > 0 }
                }.getOrNull()
            }
        }
    }

    private companion object {
        const val IMAGE_DIR = "coach_images"
    }
}
