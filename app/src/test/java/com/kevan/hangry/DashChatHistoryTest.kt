package com.kevan.hangry

import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.domain.model.ChatTitle
import com.kevan.hangry.ui.coach.AiCoachUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Chat titles and what the history sheet shows. */
class DashChatHistoryTest {

    @Test
    fun `short first messages become the title as they are`() {
        assertEquals("How did I sleep?", ChatTitle.from("  How did I sleep?  "))
        assertEquals("Plan my week", ChatTitle.from("\n\nPlan my week\nwith three runs"))
    }

    @Test
    fun `long first messages are cut at a word`() {
        val title = ChatTitle.from("I want to lose about five kilos before my sister's wedding in March without losing strength")
        assertTrue(title, title.length <= ChatTitle.MAX_LENGTH + 1)
        assertTrue(title.endsWith("…"))
        assertTrue(title.removeSuffix("…").let { "I want to lose about five kilos before my sister's wedding in March".startsWith(it) })
    }

    @Test
    fun `a photo with no text is a photo chat`() {
        assertEquals("Photo chat", ChatTitle.from("   "))
    }

    private fun chat(id: Long, title: String) = CoachConversationSummary(id, title, Instant.EPOCH, Instant.EPOCH, 2, "hi")

    @Test
    fun `search matches titles and message text`() {
        val state = AiCoachUiState(
            conversations = listOf(chat(1, "Knee pain after runs"), chat(2, "Protein ideas"), chat(3, "Sleep tips")),
            historyQuery = "knee",
            historyMatches = setOf(3L) // "knee" said inside the sleep chat
        )
        assertEquals(listOf(1L, 3L), state.visibleConversations.map { it.id })
        assertEquals(3, state.copy(historyQuery = " ").visibleConversations.size)
    }

    @Test
    fun `the open chat's title shows, a new chat has none`() {
        val state = AiCoachUiState(conversations = listOf(chat(7, "Protein ideas")), activeConversationId = 7)
        assertEquals("Protein ideas", state.activeTitle)
        assertNull(state.copy(activeConversationId = null).activeTitle)
    }
}
