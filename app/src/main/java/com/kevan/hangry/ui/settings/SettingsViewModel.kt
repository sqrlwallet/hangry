package com.kevan.hangry.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterClient
import com.kevan.hangry.data.backup.HangryBackupManager
import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.HeightRepository
import com.kevan.hangry.domain.repository.LocalExportManager
import com.kevan.hangry.domain.repository.LocalStorageManager
import com.kevan.hangry.domain.repository.StorageBreakdown
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.util.AppDispatchers
import com.kevan.hangry.util.DefaultAppDispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsEvent {
    data object JsonExportSaved : SettingsEvent
    data object CsvExportSaved : SettingsEvent
    data object SyncCompleted : SettingsEvent
    data object RecalculateDone : SettingsEvent
    data object GoalsSaved : SettingsEvent
    data object DatabaseOptimized : SettingsEvent
    data class CleanOrphansDone(val cleanedFiles: Int, val freedCacheBytes: Long) : SettingsEvent
    data object HealthDataDeleted : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

data class SettingsUiState(
    val profile: UserProfileEntity? = null,
    val latestWeight: WeightMeasurementEntity? = null,
    val latestHeight: HeightMeasurementEntity? = null,
    val storageBreakdown: StorageBreakdown? = null,
    val isBusy: Boolean = false,
    val isOptimizingDb: Boolean = false,
    val isCleaningOrphans: Boolean = false,
    val isDeletingHealth: Boolean = false,
    val isResetting: Boolean = false
) {
    val effectiveHeightCm: Double?
        get() = latestHeight?.heightCm ?: profile?.heightCm
}

class SettingsViewModel(
    val syncManager: HealthSyncManager,
    val exportManager: LocalExportManager,
    val backupManager: HangryBackupManager? = null,
    val localStorageManager: LocalStorageManager,
    val userProfileRepository: UserProfileRepository,
    val bodyMetricsRepository: BodyMetricsRepository,
    val weightRepository: WeightRepository,
    val heightRepository: HeightRepository,
    val calorieCalculator: CalorieCalculator? = null,
    val secureKeyStore: SecureKeyStore? = null,
    val openRouterClient: OpenRouterClient? = null,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers()
) : ViewModel() {

    private val _storageBreakdown = MutableStateFlow<StorageBreakdown?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _isOptimizingDb = MutableStateFlow(false)
    private val _isCleaningOrphans = MutableStateFlow(false)
    private val _isDeletingHealth = MutableStateFlow(false)
    private val _isResetting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            userProfileRepository.getProfile(),
            weightRepository.getLatestWeight(),
            heightRepository.getLatestHeight()
        ) { profile, weight, height -> Triple(profile, weight, height) },
        combine(
            combine(_storageBreakdown, _isBusy, _isOptimizingDb) { storage, isBusy, isOpt ->
                Triple(storage, isBusy, isOpt)
            },
            combine(_isCleaningOrphans, _isDeletingHealth, _isResetting) { isClean, isDel, isReset ->
                Triple(isClean, isDel, isReset)
            }
        ) { (storage, isBusy, isOpt), (isClean, isDel, isReset) ->
            StorageAndFlags(storage, isBusy, isOpt, isClean, isDel, isReset)
        }
    ) { (profile, weight, height), flags ->
        SettingsUiState(
            profile = profile,
            latestWeight = weight,
            latestHeight = height,
            storageBreakdown = flags.storage,
            isBusy = flags.isBusy,
            isOptimizingDb = flags.isOpt,
            isCleaningOrphans = flags.isClean,
            isDeletingHealth = flags.isDel,
            isResetting = flags.isReset
        )
    }.flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState()
        )

    private data class StorageAndFlags(
        val storage: StorageBreakdown?,
        val isBusy: Boolean,
        val isOpt: Boolean,
        val isClean: Boolean,
        val isDel: Boolean,
        val isReset: Boolean
    )

    init {
        refreshStorageBreakdown()
    }

    fun refreshStorageBreakdown() {
        viewModelScope.launch(dispatchers.io) {
            _storageBreakdown.value = localStorageManager.getStorageBreakdown()
        }
    }

    fun exportJson(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(dispatchers.io) {
            _isBusy.value = true
            try {
                val json = exportManager.exportDataAsJson()
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                _events.emit(SettingsEvent.JsonExportSaved)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun exportCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(dispatchers.io) {
            _isBusy.value = true
            try {
                val csv = exportManager.exportDataAsCsv()
                contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                _events.emit(SettingsEvent.CsvExportSaved)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun syncRecent() {
        viewModelScope.launch(dispatchers.io) {
            _isBusy.value = true
            try {
                syncManager.syncRecent().collect { }
                _events.emit(SettingsEvent.SyncCompleted)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recalculateAllBaselines() {
        viewModelScope.launch(dispatchers.io) {
            _isBusy.value = true
            try {
                syncManager.recalculateAllBaselines().collect { }
                _events.emit(SettingsEvent.RecalculateDone)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun optimizeDatabase() {
        viewModelScope.launch(dispatchers.io) {
            _isOptimizingDb.value = true
            try {
                localStorageManager.optimizeDatabase()
                _storageBreakdown.value = localStorageManager.getStorageBreakdown()
                _events.emit(SettingsEvent.DatabaseOptimized)
            } finally {
                _isOptimizingDb.value = false
            }
        }
    }

    fun cleanOrphanedFiles() {
        viewModelScope.launch(dispatchers.io) {
            _isCleaningOrphans.value = true
            try {
                val cleanedFiles = localStorageManager.cleanOrphanedFiles()
                val freedCacheBytes = localStorageManager.clearCache()
                _storageBreakdown.value = localStorageManager.getStorageBreakdown()
                _events.emit(SettingsEvent.CleanOrphansDone(cleanedFiles, freedCacheBytes))
            } finally {
                _isCleaningOrphans.value = false
            }
        }
    }

    fun clearAllHealthData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(dispatchers.io) {
            _isDeletingHealth.value = true
            try {
                syncManager.clearAllData()
                localStorageManager.cleanOrphanedFiles()
                localStorageManager.clearCache()
                localStorageManager.optimizeDatabase()
                _storageBreakdown.value = localStorageManager.getStorageBreakdown()
                _events.emit(SettingsEvent.HealthDataDeleted)
                onComplete?.invoke()
            } finally {
                _isDeletingHealth.value = false
            }
        }
    }

    fun resetApplication(onComplete: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            _isResetting.value = true
            try {
                syncManager.clearAllData()
                withContext(dispatchers.main) {
                    onComplete()
                }
            } finally {
                _isResetting.value = false
            }
        }
    }

    fun saveProfileAndGoals(updated: UserProfileEntity, newWeightKg: Double?, onSaved: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            val current = userProfileRepository.getProfileSync()
            val scoringInputsChanged = updated.maxHeartRate != current?.maxHeartRate ||
                updated.sleepGoalMinutes != current?.sleepGoalMinutes || updated.age != current?.age
            userProfileRepository.saveProfile(updated)
            if (newWeightKg != null && newWeightKg > 0.0) {
                bodyMetricsRepository.logWeight(newWeightKg)
            }
            withContext(dispatchers.main) {
                onSaved()
            }
            _events.emit(SettingsEvent.GoalsSaved)
            if (scoringInputsChanged) {
                syncManager.recalculateAllBaselines().collect { }
            }
        }
    }

    companion object {
        fun provideFactory(
            syncManager: HealthSyncManager,
            exportManager: LocalExportManager,
            backupManager: HangryBackupManager? = null,
            localStorageManager: LocalStorageManager,
            userProfileRepository: UserProfileRepository,
            bodyMetricsRepository: BodyMetricsRepository,
            weightRepository: WeightRepository,
            heightRepository: HeightRepository,
            calorieCalculator: CalorieCalculator? = null,
            secureKeyStore: SecureKeyStore? = null,
            openRouterClient: OpenRouterClient? = null,
            dispatchers: AppDispatchers = DefaultAppDispatchers()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    syncManager = syncManager,
                    exportManager = exportManager,
                    backupManager = backupManager,
                    localStorageManager = localStorageManager,
                    userProfileRepository = userProfileRepository,
                    bodyMetricsRepository = bodyMetricsRepository,
                    weightRepository = weightRepository,
                    heightRepository = heightRepository,
                    calorieCalculator = calorieCalculator,
                    secureKeyStore = secureKeyStore,
                    openRouterClient = openRouterClient,
                    dispatchers = dispatchers
                ) as T
            }
        }
    }
}
