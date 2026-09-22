package com.kevan.hangry.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.repository.CoachRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AiCoachViewModel(
    private val coachRepository: CoachRepository,
    private val userProfileRepository: UserProfileRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        refreshState()
    }

    fun refreshState() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                coachRepository.getMessages(),
                coachRepository.getJournalEntries(),
                userProfileRepository.getProfile()
            ) { messages, journalEntries, profile ->
                val aiEnabled = profile?.aiFeaturesEnabled ?: false
                val hasKey = secureKeyStore.getApiKey() != null
                _uiState.update { current ->
                    current.copy(
                        messages = messages,
                        journalEntries = journalEntries,
                        aiFeaturesEnabled = aiEnabled,
                        hasApiKey = hasKey,
                        isAiConfigured = aiEnabled && hasKey
                    )
                }
            }.collect {}
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            coachRepository.askCoach(trimmed).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    val errorMsg = when (e) {
                        is OpenRouterException -> e.message ?: "Failed to connect to AI Coach."
                        else -> e.localizedMessage ?: "Something went wrong. Please try again."
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            )
        }
    }

    fun deleteJournalEntry(id: Long) {
        viewModelScope.launch {
            coachRepository.deleteJournalEntry(id)
        }
    }

    fun addManualJournalEntry(category: String, summary: String, content: String) {
        if (summary.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            coachRepository.addJournalEntry(
                category = category,
                summary = summary.trim(),
                content = content.trim(),
                sourceMessage = "Manual entry"
            )
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            coachRepository.clearMessages()
        }
    }

    fun setShowJournalSheet(show: Boolean) {
        _uiState.update { it.copy(showJournalSheet = show) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun provideFactory(
            coachRepository: CoachRepository,
            userProfileRepository: UserProfileRepository,
            secureKeyStore: SecureKeyStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiCoachViewModel(
                    coachRepository = coachRepository,
                    userProfileRepository = userProfileRepository,
                    secureKeyStore = secureKeyStore
                ) as T
            }
        }
    }
}
