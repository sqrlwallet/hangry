package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.CoachMessageDao
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.repository.CoachRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class DefaultCoachRepository(
    private val coachJournalDao: CoachJournalDao,
    private val coachMessageDao: CoachMessageDao,
    private val aiCoachService: AiCoachService
) : CoachRepository {

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
    }

    override suspend fun askCoach(userMessage: String): Result<CoachResponse> {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Message cannot be blank"))
        }

        // 1. Fetch prior conversation history before saving current turn
        val priorHistory = coachMessageDao.getRecent(10).reversed()

        // 2. Save user turn
        coachMessageDao.insert(
            CoachMessageEntity(
                timestamp = Instant.now(),
                role = "user",
                content = trimmed
            )
        )

        // 3. Ask AI Coach
        val result = aiCoachService.askCoach(trimmed, priorHistory)

        return result.onSuccess { response ->
            // 4. Save extracted journal entry if present
            val extracted = response.journalEntry
            if (extracted != null && extracted.summary.isNotBlank()) {
                coachJournalDao.insert(
                    CoachJournalEntity(
                        date = LocalDate.now(),
                        timestamp = Instant.now(),
                        category = extracted.category,
                        summary = extracted.summary,
                        content = extracted.content,
                        sourceMessage = trimmed
                    )
                )
            }

            // 5. Save assistant turn
            coachMessageDao.insert(
                CoachMessageEntity(
                    timestamp = Instant.now(),
                    role = "assistant",
                    content = response.reply,
                    journalEntrySummary = extracted?.summary
                )
            )
        }
    }
}
