package com.kevan.hangry.ui.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.breathing.BreathingSessionController
import com.kevan.hangry.data.breathing.BreathingSessionState
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.BreathingStats
import com.kevan.hangry.domain.repository.BreathingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HealthConnectLogStatus { CHECKING, UNAVAILABLE, NEEDS_PERMISSION, CONNECTED }

data class BreathingSetupState(
    val pattern: BreathingPattern,
    val minutes: Int = 5,
    val soundEnabled: Boolean = true,
    val healthConnect: HealthConnectLogStatus = HealthConnectLogStatus.CHECKING,
    val permissionsToRequest: Set<String> = emptySet()
)

class BreathingViewModel(
    initialPattern: BreathingPattern,
    private val controller: BreathingSessionController,
    private val repository: BreathingRepository,
    private val healthConnectDataSource: HealthConnectDataSource
) : ViewModel() {

    private val _setup = MutableStateFlow(BreathingSetupState(pattern = initialPattern))
    val setup: StateFlow<BreathingSetupState> = _setup.asStateFlow()

    val session: StateFlow<BreathingSessionState> = controller.state

    val stats: StateFlow<BreathingStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreathingStats())

    val recentSessions: StateFlow<List<BreathingSessionEntity>> = repository.getRecentSessions(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshHealthConnect()
    }

    fun selectPattern(pattern: BreathingPattern) = _setup.update { it.copy(pattern = pattern) }

    fun selectMinutes(minutes: Int) = _setup.update { it.copy(minutes = minutes) }

    fun setSoundEnabled(enabled: Boolean) {
        _setup.update { it.copy(soundEnabled = enabled) }
        controller.setSoundEnabled(enabled)
    }

    fun start() {
        val setup = _setup.value
        controller.start(setup.pattern, setup.minutes, setup.soundEnabled)
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
    fun dismissSummary() = controller.dismissSummary()

    /** Called after the Health Connect permission dialog closes, whatever the outcome. */
    fun onPermissionResult() {
        refreshHealthConnect(syncPending = true)
    }

    private fun refreshHealthConnect(syncPending: Boolean = false) {
        viewModelScope.launch {
            val status = when {
                !healthConnectDataSource.isAvailable() -> HealthConnectLogStatus.UNAVAILABLE
                healthConnectDataSource.hasBreathingWritePermissions() -> HealthConnectLogStatus.CONNECTED
                else -> HealthConnectLogStatus.NEEDS_PERMISSION
            }
            _setup.update {
                it.copy(
                    healthConnect = status,
                    permissionsToRequest = if (status == HealthConnectLogStatus.NEEDS_PERMISSION) {
                        healthConnectDataSource.breathingWritePermissions()
                    } else emptySet()
                )
            }
            // Sessions finished before access was granted get written now.
            if (syncPending && status == HealthConnectLogStatus.CONNECTED) repository.syncPendingSessions()
        }
    }

    companion object {
        fun provideFactory(
            initialPattern: BreathingPattern,
            controller: BreathingSessionController,
            repository: BreathingRepository,
            healthConnectDataSource: HealthConnectDataSource
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BreathingViewModel(initialPattern, controller, repository, healthConnectDataSource) as T
        }
    }
}
