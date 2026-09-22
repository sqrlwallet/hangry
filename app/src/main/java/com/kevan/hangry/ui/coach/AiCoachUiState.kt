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
    val showJournalSheet: Boolean = false
)
