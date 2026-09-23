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
import com.kevan.hangry.domain.model.CoachAction
import kotlinx.serialization.builtins.ListSerializer

class AiCoachViewModel(
    private val coachRepository: CoachRepository,
    private val userProfileRepository: UserProfileRepository,
    private val secureKeyStore: SecureKeyStore,
    /** Builds data-aware suggestion chips; null falls back to the fixed starters. */
    private val suggestionSource: (suspend () -> List<String>)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        refreshState()
    }

    fun refreshSuggestions() {
        val source = suggestionSource ?: return
        viewModelScope.launch {
            val chips = runCatching { source() }.getOrDefault(emptyList())
            _uiState.update { it.copy(suggestions = chips) }
        }
    }

    fun refreshState() {
        refreshSuggestions()
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
        val images = _uiState.value.pendingImages
        if ((trimmed.isBlank() && images.isEmpty()) || _uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null, pendingImages = emptyList()) }
        viewModelScope.launch {
            val result = coachRepository.askCoach(trimmed, images) { partial ->
                _uiState.update { it.copy(streamingReply = partial) }
            }
            _uiState.update { it.copy(streamingReply = null) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    refreshSuggestions()
                },
                onFailure = { e ->
                    val errorMsg = when (e) {
                        is OpenRouterException -> e.message ?: "Couldn't reach Dash. Try again."
                        else -> e.localizedMessage ?: "Something went wrong. Please try again."
                    }
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = errorMsg, draftToRestore = trimmed, pendingImages = images)
                    }
                }
            )
        }
    }

    fun attachImages(uris: List<android.net.Uri>) {
        _uiState.update { it.copy(pendingImages = (it.pendingImages + uris).distinct().take(MAX_IMAGES)) }
    }

    fun removeImage(uri: android.net.Uri) {
        _uiState.update { it.copy(pendingImages = it.pendingImages - uri) }
    }

    /** The user tapped an action card Dash proposed - this is the only way it runs. */
    fun executeAction(messageId: Long, index: Int) {
        val action = _uiState.value.messages.firstOrNull { it.id == messageId }?.actionsJson
            ?.let { raw -> runCatching { actionsJson.decodeFromString(ListSerializer(CoachAction.serializer()), raw) }.getOrNull() }
            ?.getOrNull(index)
        viewModelScope.launch {
            val result = coachRepository.executeAction(messageId, index)
            if (result.isSuccess && action?.type == CoachAction.OPEN_SCREEN && action.screen != null) {
                _uiState.update { it.copy(openScreen = action.screen to action.breathingPattern) }
            } else {
                _uiState.update { it.copy(actionMessage = result.fold({ msg -> msg }, { e -> e.message ?: "Couldn't do that." })) }
                refreshSuggestions()
            }
        }
    }

    fun consumeOpenScreen() = _uiState.update { it.copy(openScreen = null) }

    fun consumeDraft() = _uiState.update { it.copy(draftToRestore = null) }

    fun dismissAction(messageId: Long, index: Int) {
        viewModelScope.launch { coachRepository.dismissAction(messageId, index) }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
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
        const val MAX_IMAGES = 3

        private val actionsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun provideFactory(
            coachRepository: CoachRepository,
            userProfileRepository: UserProfileRepository,
            secureKeyStore: SecureKeyStore,
            suggestionSource: (suspend () -> List<String>)? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiCoachViewModel(
                    coachRepository = coachRepository,
                    userProfileRepository = userProfileRepository,
                    secureKeyStore = secureKeyStore,
                    suggestionSource = suggestionSource
                ) as T
            }
        }
    }
}
