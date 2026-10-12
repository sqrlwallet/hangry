package com.kevan.hangry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.data.repository.DefaultCoachRepository
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.model.CoachResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Separate Dash chats: each keeps its own messages and context; deleting one leaves the rest. */
@RunWith(AndroidJUnit4::class)
class DashChatsInstrumentedTest {

    private lateinit var db: HangryDatabase
    private lateinit var repo: DefaultCoachRepository
    private val seenHistory = mutableListOf<List<CoachMessageEntity>>()
    private var fail = false

    private val service = object : AiCoachService {
        override suspend fun askCoach(
            userMessage: String,
            recentMessages: List<CoachMessageEntity>,
            imagesBase64: List<String>,
            onPartialReply: ((String) -> Unit)?
        ): Result<CoachResponse> {
            seenHistory += recentMessages
            return if (fail) Result.failure(IllegalStateException("offline")) else Result.success(CoachResponse(reply = "Reply to $userMessage"))
        }
    }

    @Before
    fun setUp() {
        db = HangryDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext<Context>())
        repo = DefaultCoachRepository(db.coachJournalDao(), db.coachMessageDao(), db.coachConversationDao(), service)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun chatsKeepTheirOwnMessagesAndContext() = runBlocking {
        val sleep = repo.startConversation("How did I sleep?")
        repo.askCoach(sleep, "How did I sleep?", emptyList()) {}
        val food = repo.startConversation("Protein ideas")
        repo.askCoach(food, "Protein ideas", emptyList()) {}
        repo.askCoach(food, "Something vegetarian", emptyList()) {}

        assertEquals(2, repo.getMessages(sleep).first().size)
        assertEquals(4, repo.getMessages(food).first().size)
        // The food chat's second question only carried the food chat's history.
        assertTrue(seenHistory.last().all { it.conversationId == food })
        assertEquals(2, seenHistory.last().size)

        val list = repo.getConversations().first()
        assertEquals(listOf(food, sleep), list.map { it.id }) // most recent first
        assertEquals("Protein ideas", list.first().title)
        assertEquals("Reply to Something vegetarian", list.first().lastMessage)
        assertEquals(setOf(food), repo.searchConversations("vegetarian"))
    }

    @Test
    fun aFailedFirstMessageLeavesNoEmptyChat() = runBlocking {
        fail = true
        val id = repo.startConversation("Hello")
        assertTrue(repo.askCoach(id, "Hello", emptyList()) {}.isFailure)
        assertTrue(repo.getConversations().first().isEmpty())
        assertNull(repo.mostRecentConversationId())
    }

    @Test
    fun deletingAChatKeepsTheOthers() = runBlocking {
        val a = repo.startConversation("A")
        repo.askCoach(a, "A", emptyList()) {}
        val b = repo.startConversation("B")
        repo.askCoach(b, "B", emptyList()) {}

        repo.deleteConversation(a)
        assertEquals(listOf(b), repo.getConversations().first().map { it.id })
        assertTrue(repo.getMessages(a).first().isEmpty())
        assertEquals(2, repo.getMessages(b).first().size)

        repo.renameConversation(b, "  Renamed  ")
        assertEquals("Renamed", repo.getConversations().first().single().title)
    }
}
