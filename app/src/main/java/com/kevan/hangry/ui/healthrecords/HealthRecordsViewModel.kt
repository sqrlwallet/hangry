package com.kevan.hangry.ui.healthrecords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class HealthConnectImportState(
    val available: Boolean = false,
    val medicalRecordsSupported: Boolean = false,
    /** Not yet granted; empty when everything we'd ask for is already granted. */
    val permissionsToRequest: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null
)

class HealthRecordsViewModel(
    private val repository: HealthRecordsRepository,
    private val healthConnect: HealthConnectDataSource
) : ViewModel() {

    val records: StateFlow<HealthRecordsSnapshot> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthRecordsSnapshot())

    private val _import = MutableStateFlow(HealthConnectImportState())
    val import: StateFlow<HealthConnectImportState> = _import.asStateFlow()

    init {
        viewModelScope.launch {
            refreshPermissions()
            // Quietly pull anything new if access was granted earlier.
            if (_import.value.available && _import.value.permissionsToRequest.isEmpty()) importNow(quiet = true)
        }
    }

    fun addReading(type: MarkerType, value: Double, secondary: Double?, measuredAt: Instant, context: GlucoseContext?, note: String?) =
        viewModelScope.launch { repository.addReading(type, value, secondary, measuredAt, context, note) }

    fun deleteReading(id: Long) = viewModelScope.launch { repository.deleteReading(id) }

    fun setGoal(type: MarkerType, target: Double, secondary: Double?, date: LocalDate?) =
        viewModelScope.launch { repository.setGoal(type, target, secondary, date) }

    fun clearGoal(type: MarkerType) = viewModelScope.launch { repository.clearGoal(type) }

    fun addProfileItem(kind: HealthProfileKind, name: String, note: String?) =
        viewModelScope.launch { repository.addProfileItem(kind, name, note) }

    fun deleteProfileItem(id: Long) = viewModelScope.launch { repository.deleteProfileItem(id) }

    fun logPeriod(start: LocalDate, end: LocalDate?) = viewModelScope.launch { repository.logPeriod(start, end) }

    fun deletePeriod(id: Long) = viewModelScope.launch { repository.deletePeriod(id) }

    fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?) =
        viewModelScope.launch { repository.setPregnancy(isPregnant, dueDate) }

    /** After the Health Connect permission dialog closes, whatever was granted is imported. */
    fun onPermissionResult() = viewModelScope.launch {
        refreshPermissions()
        importNow(quiet = false)
    }

    fun importFromHealthConnect() = viewModelScope.launch { importNow(quiet = false) }

    fun clearImportMessage() = _import.update { it.copy(message = null) }

    private suspend fun refreshPermissions() {
        val available = healthConnect.isAvailable()
        if (!available) {
            _import.update { it.copy(available = false) }
            return
        }
        val includeCycle = repository.current().showsFemaleHealth
        val wanted = healthConnect.healthRecordPermissions(includeCycle)
        val granted = healthConnect.grantedPermissions()
        _import.update {
            it.copy(
                available = true,
                medicalRecordsSupported = healthConnect.supportsMedicalRecords(),
                permissionsToRequest = wanted - granted
            )
        }
    }

    private suspend fun importNow(quiet: Boolean) {
        _import.update { it.copy(isImporting = true) }
        val result = runCatching { repository.importFromHealthConnect() }.getOrNull()
        _import.update {
            it.copy(
                isImporting = false,
                message = when {
                    result == null -> if (quiet) null else "Couldn't read from Health Connect."
                    result.total > 0 -> "Imported ${result.total} new record${if (result.total == 1) "" else "s"} from Health Connect."
                    quiet -> null
                    else -> "Up to date - nothing new in Health Connect."
                }
            )
        }
    }

    companion object {
        fun provideFactory(repository: HealthRecordsRepository, healthConnect: HealthConnectDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthRecordsViewModel(repository, healthConnect) as T
            }
    }
}
