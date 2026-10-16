package com.kevan.hangry.ui.coach

import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity

data class AiCoachUiState(
    /** The open chat's messages. */
    val messages: List<CoachMessageEntity> = emptyList(),
    /** The open chat; null for a new chat that has no messages yet. */
    val activeConversationId: Long? = null,
    /** Every chat, most recently used first, for the history sheet. */
    val conversations: List<CoachConversationSummary> = emptyList(),
    val showHistory: Boolean = false,
    val historyQuery: String = "",
    /** Chats whose messages match [historyQuery]; titles are matched on top of these. */
    val historyMatches: Set<Long> = emptySet(),
    val journalEntries: List<CoachJournalEntity> = emptyList(),
    val isAiConfigured: Boolean = false,
    val aiFeaturesEnabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showJournalSheet: Boolean = false,
    /** Photos attached to the message being written (up to 3). */
    val pendingImages: List<android.net.Uri> = emptyList(),
    /** Result of tapping an action card, shown as a snackbar. */
    val actionMessage: String? = null,
    /** Text to put back in the input after a failed send, so nothing typed is lost. */
    val draftToRestore: String? = null,
    /** Chips tailored to the user's current data; empty until loaded. */
    val suggestions: List<String> = emptyList(),
    /** OPEN_SCREEN action the user just tapped: (screen, breathing pattern). Consumed by the screen. */
    val openScreen: Pair<String, String?>? = null,
    /** Dash's reply as it streams in; null when nothing is streaming. */
    val streamingReply: String? = null
) {
    /** The open chat's title, for the top bar; null for a new chat. */
    val activeTitle: String? get() = conversations.firstOrNull { it.id == activeConversationId }?.title

    /** True when the last thing in the chat is Dash's reply, so it can be regenerated. */
    val canRegenerate: Boolean get() = !isLoading && messages.lastOrNull()?.role == "assistant"

    /** Chats the history sheet shows: all, or those whose title or messages match the search. */
    val visibleConversations: List<CoachConversationSummary>
        get() {
            val q = historyQuery.trim()
            if (q.isEmpty()) return conversations
            return conversations.filter { it.id in historyMatches || it.title.contains(q, ignoreCase = true) }
        }
}
