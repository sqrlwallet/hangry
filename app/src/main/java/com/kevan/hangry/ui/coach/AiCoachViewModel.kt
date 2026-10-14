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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.kevan.hangry.domain.model.CoachAction
import kotlinx.serialization.builtins.ListSerializer

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    private var searchJob: Job? = null

    /** The open chat; null is a new, empty chat (created when its first message is sent). */
    private val activeConversation = MutableStateFlow<Long?>(null)
    private var resumed = false

    init {
        refreshState()
    }

    /** Clears the screen for a new chat. Nothing is saved until the first message. */
    fun newChat() {
        activeConversation.value = null
        _uiState.update { it.copy(showHistory = false, errorMessage = null, streamingReply = null) }
        refreshSuggestions()
    }

    fun openConversation(id: Long) {
        activeConversation.value = id
        _uiState.update { it.copy(showHistory = false, errorMessage = null) }
    }

    fun setShowHistory(show: Boolean) {
        _uiState.update { it.copy(showHistory = show, historyQuery = if (show) it.historyQuery else "", historyMatches = emptySet()) }
    }

    fun searchHistory(query: String) {
        _uiState.update { it.copy(historyQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            val matches = runCatching { coachRepository.searchConversations(query) }.getOrDefault(emptySet())
            _uiState.update { it.copy(historyMatches = matches) }
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch { coachRepository.renameConversation(id, title) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            coachRepository.deleteConversation(id)
            if (activeConversation.value == id) newChat()
        }
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
            // Reopen the last chat the first time Dash opens, like a messaging app.
            if (!resumed) {
                resumed = true
                activeConversation.value = runCatching { coachRepository.mostRecentConversationId() }.getOrNull()
            }
            val messagesOfActive = activeConversation.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else coachRepository.getMessages(id)
            }
            combine(
                messagesOfActive,
                coachRepository.getConversations(),
                activeConversation,
                coachRepository.getJournalEntries(),
                userProfileRepository.getProfile()
            ) { messages, conversations, activeId, journalEntries, profile ->
                val aiEnabled = profile?.aiFeaturesEnabled ?: false
                val hasKey = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { secureKeyStore.getApiKey() != null }
                _uiState.update { current ->
                    current.copy(
                        messages = messages,
                        activeConversationId = activeId,
                        conversations = conversations,
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
            var startedHere = false
            val result = try {
                // A new chat is created by its first message.
                val conversationId = activeConversation.value
                    ?: coachRepository.startConversation(trimmed).also { activeConversation.value = it; startedHere = true }
                coachRepository.askCoach(conversationId, trimmed, images) { partial ->
                    // Only shown while that chat is the one open.
                    if (activeConversation.value == conversationId) _uiState.update { it.copy(streamingReply = partial) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected becomes the error banner, never an app crash.
                Result.failure(e)
            }
            _uiState.update { it.copy(streamingReply = null) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    refreshSuggestions()
                },
                onFailure = { e ->
                    // The repository drops a new chat whose first message failed; back to a blank one.
                    if (startedHere) activeConversation.value = null
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

    /** Deletes every chat. */
    fun deleteAllChats() {
        viewModelScope.launch {
            coachRepository.clearMessages()
            newChat()
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
        private const val SEARCH_DEBOUNCE_MS = 250L

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
