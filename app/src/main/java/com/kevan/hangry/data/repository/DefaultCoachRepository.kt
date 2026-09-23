package com.kevan.hangry.data.repository

import android.content.Context
import android.net.Uri
import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.CoachMessageDao
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.CoachActionStatus
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.repository.CoachRepository
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.Dispatchers
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
    private val aiCoachService: AiCoachService,
    /** Both optional so the repository still works without photo/action support (e.g. tests). */
    private val context: Context? = null,
    private val actionExecutor: CoachActionExecutor? = null
) : CoachRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val actionsSerializer = ListSerializer(CoachAction.serializer())

    override fun getMessages(): Flow<List<CoachMessageEntity>> = coachMessageDao.getAll()

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
        context?.let { ctx -> withContext(Dispatchers.IO) { File(ctx.filesDir, IMAGE_DIR).listFiles()?.forEach { it.delete() } } }
    }

    override suspend fun askCoach(userMessage: String): Result<CoachResponse> = askCoach(userMessage, emptyList())

    override suspend fun askCoach(userMessage: String, imageUris: List<Uri>): Result<CoachResponse> =
        ask(userMessage, imageUris, onPartialReply = null)

    override suspend fun askCoach(
        userMessage: String,
        imageUris: List<Uri>,
        onPartialReply: (String) -> Unit
    ): Result<CoachResponse> = ask(userMessage, imageUris, onPartialReply)

    private suspend fun ask(userMessage: String, imageUris: List<Uri>, onPartialReply: ((String) -> Unit)?): Result<CoachResponse> {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank() && imageUris.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be blank"))
        }
        val text = trimmed.ifBlank { "Here's a photo." }

        // 1. Fetch prior conversation history before saving current turn
        val priorHistory = coachMessageDao.getRecent(10).reversed()

        // 2. Keep the photos (for the chat bubble) and encode them for the model
        val storedPaths = storeImages(imageUris)
        val imagesBase64 = context?.let { ctx ->
            withContext(Dispatchers.IO) { storedPaths.mapNotNull { ctx.readImageAsBase64Jpeg(Uri.fromFile(File(it))) } }
        }.orEmpty()

        // 3. Save user turn
        val userMessageId = coachMessageDao.insert(
            CoachMessageEntity(
                timestamp = Instant.now(),
                role = "user",
                content = text,
                imagePaths = storedPaths.takeIf { it.isNotEmpty() }?.joinToString(",")
            )
        )

        // 4. Ask Dash
        val result = aiCoachService.askCoach(text, priorHistory, imagesBase64, onPartialReply)

        // A failed send leaves no half-conversation behind; the screen gives the text back to retry.
        result.onFailure {
            coachMessageDao.deleteById(userMessageId)
            withContext(Dispatchers.IO) { storedPaths.forEach { path -> File(path).delete() } }
        }

        return result.onSuccess { response ->
            // 5. Save extracted journal entry if present
            val extracted = response.journalEntry
            if (extracted != null && extracted.summary.isNotBlank()) {
                coachJournalDao.insert(
                    CoachJournalEntity(
                        date = LocalDate.now(),
                        timestamp = Instant.now(),
                        category = extracted.category,
                        summary = extracted.summary,
                        content = extracted.content,
                        sourceMessage = text
                    )
                )
            }

            // 6. Save assistant turn, with any proposed actions waiting for the user's tap
            coachMessageDao.insert(
                CoachMessageEntity(
                    timestamp = Instant.now(),
                    role = "assistant",
                    content = response.reply,
                    journalEntrySummary = extracted?.summary,
                    actionsJson = response.actions.takeIf { it.isNotEmpty() && actionExecutor != null }
                        ?.let { json.encodeToString(actionsSerializer, it) }
                )
            )
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
