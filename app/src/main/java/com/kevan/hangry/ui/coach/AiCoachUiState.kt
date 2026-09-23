package com.kevan.hangry.ui.coach

import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity

data class AiCoachUiState(
    val messages: List<CoachMessageEntity> = emptyList(),
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
)
