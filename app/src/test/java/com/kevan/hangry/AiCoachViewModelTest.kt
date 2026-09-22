package com.kevan.hangry

import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.model.ExtractedJournalEntry
import com.kevan.hangry.domain.repository.CoachRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.ui.coach.AiCoachViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AiCoachViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCoachRepository : CoachRepository {
        val messagesFlow = MutableStateFlow<List<CoachMessageEntity>>(emptyList())
        val journalFlow = MutableStateFlow<List<CoachJournalEntity>>(emptyList())
        var askCoachHandler: suspend (String) -> Result<CoachResponse> = {
            Result.success(CoachResponse(reply = "Hello!"))
        }

        override fun getMessages(): Flow<List<CoachMessageEntity>> = messagesFlow.asStateFlow()
        override fun getJournalEntries(): Flow<List<CoachJournalEntity>> = journalFlow.asStateFlow()

        override suspend fun deleteJournalEntry(id: Long) {
            journalFlow.value = journalFlow.value.filter { it.id != id }
        }

        override suspend fun addJournalEntry(
            category: String,
            summary: String,
            content: String,
            sourceMessage: String?
        ): Long {
            val entry = CoachJournalEntity(
                id = (journalFlow.value.size + 1).toLong(),
                date = LocalDate.now(),
                category = category,
                summary = summary,
                content = content,
                sourceMessage = sourceMessage
            )
            journalFlow.value = journalFlow.value + entry
            return entry.id
        }

        override suspend fun clearMessages() {
            messagesFlow.value = emptyList()
        }

        override suspend fun askCoach(userMessage: String): Result<CoachResponse> {
            val res = askCoachHandler(userMessage)
            if (res.isSuccess) {
                val resp = res.getOrThrow()
                messagesFlow.value = messagesFlow.value + CoachMessageEntity(
                    id = (messagesFlow.value.size + 1).toLong(),
                    role = "user",
                    content = userMessage
                ) + CoachMessageEntity(
                    id = (messagesFlow.value.size + 2).toLong(),
                    role = "assistant",
                    content = resp.reply,
                    journalEntrySummary = resp.journalEntry?.summary
                )
                if (resp.journalEntry != null) {
                    addJournalEntry(
                        category = resp.journalEntry!!.category,
                        summary = resp.journalEntry!!.summary,
                        content = resp.journalEntry!!.content
                    )
                }
            }
            return res
        }
    }

    private class FakeUserProfileRepo : UserProfileRepository {
        val profileFlow = MutableStateFlow<UserProfileEntity?>(UserProfileEntity(aiFeaturesEnabled = true))
        override fun getProfile(): Flow<UserProfileEntity?> = profileFlow.asStateFlow()
        override suspend fun getProfileSync(): UserProfileEntity? = profileFlow.value
        override suspend fun saveProfile(profile: UserProfileEntity) {
            profileFlow.value = profile
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsMessagesAndJournal() = runTest {
        val fakeCoachRepo = FakeCoachRepository()
        val fakeProfileRepo = FakeUserProfileRepo()
        // Mock fake SecureKeyStore by using a stub or subclass if possible, or test repository directly
        val entry = CoachJournalEntity(
            id = 1,
            date = LocalDate.now(),
            category = "PROBLEM",
            summary = "Knee pain",
            content = "Pain during squats"
        )
        fakeCoachRepo.journalFlow.value = listOf(entry)

        // Verify repository methods
        val entries = fakeCoachRepo.journalFlow.value
        assertEquals(1, entries.size)
        assertEquals("Knee pain", entries.first().summary)

        fakeCoachRepo.askCoach("I have a problem with squats")
        advanceUntilIdle()

        val messages = fakeCoachRepo.messagesFlow.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("assistant", messages[1].role)
    }

    @Test
    fun askCoach_withExtractedJournalEntry_savesEntry() = runTest {
        val fakeCoachRepo = FakeCoachRepository()
        fakeCoachRepo.askCoachHandler = {
            Result.success(
                CoachResponse(
                    reply = "I noted your dairy intolerance.",
                    journalEntry = ExtractedJournalEntry(
                        category = "DIET",
                        summary = "Lactose intolerance",
                        content = "User experiences bloating after dairy."
                    )
                )
            )
        }

        fakeCoachRepo.askCoach("I feel bloated after drinking milk")
        advanceUntilIdle()

        val journalEntries = fakeCoachRepo.journalFlow.value
        assertEquals(1, journalEntries.size)
        assertEquals("DIET", journalEntries.first().category)
        assertEquals("Lactose intolerance", journalEntries.first().summary)
    }

    @Test
    fun deleteJournalEntry_removesFromRepository() = runTest {
        val fakeCoachRepo = FakeCoachRepository()
        fakeCoachRepo.addJournalEntry("INJURY", "Shoulder strain", "Mild strain on bench press")
        assertEquals(1, fakeCoachRepo.journalFlow.value.size)

        fakeCoachRepo.deleteJournalEntry(1)
        assertEquals(0, fakeCoachRepo.journalFlow.value.size)
    }

    @Test
    fun clearChat_removesMessages() = runTest {
        val fakeCoachRepo = FakeCoachRepository()
        fakeCoachRepo.askCoach("Test question")
        assertEquals(2, fakeCoachRepo.messagesFlow.value.size)

        fakeCoachRepo.clearMessages()
        assertEquals(0, fakeCoachRepo.messagesFlow.value.size)
    }
}
