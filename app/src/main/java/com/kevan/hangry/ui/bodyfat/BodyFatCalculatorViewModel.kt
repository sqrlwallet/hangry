package com.kevan.hangry.ui.bodyfat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.local.dao.HeightDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.BodyFatAnalyzer
import com.kevan.hangry.domain.calculation.BodyFatCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatCalculationResult
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BodyFatCalculatorViewModel(
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context,
    private val userProfileRepository: UserProfileRepository,
    private val weightDao: WeightDao,
    private val heightDao: HeightDao,
    private val bodyFatCalculator: BodyFatCalculator,
    private val bodyFatAnalyzer: BodyFatAnalyzer,
    private val bodyFatRepository: BodyFatRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyFatCalculatorUiState())
    val uiState: StateFlow<BodyFatCalculatorUiState> = _uiState.asStateFlow()

    private var hasInitializedInputs = false

    init {
        viewModelScope.launch {
            combine(
                userProfileRepository.getProfile(),
                weightDao.getLatestWeight(),
                heightDao.getLatestHeight(),
                bodyFatRepository.getAllScans()
            ) { profile, latestWeight, latestHeight, allScans ->
                val hasKey = secureKeyStore.getApiKey() != null
                val isAiEnabled = profile?.aiFeaturesEnabled ?: false
                val latestScan = allScans.firstOrNull()

                _uiState.update { current ->
                    val effectiveHeight = (latestHeight?.heightCm ?: profile?.heightCm)?.toInt()?.toString() ?: current.heightCm
                    val effectiveWeight = (latestWeight?.weightKg ?: profile?.currentWeightKg)?.toString() ?: current.weightKg
                    val effectiveAge = profile?.age?.toString() ?: current.age
                    val effectiveSex = profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() } ?: current.biologicalSex
                    val effectiveNeck = profile?.neckCircumferenceCm?.toString() ?: current.neckCm
                    val effectiveChest = profile?.chestCircumferenceCm?.toString() ?: current.chestCm
                    val effectiveWaist = profile?.waistCircumferenceCm?.toString() ?: current.waistCm
                    val effectiveHip = profile?.hipCircumferenceCm?.toString() ?: current.hipCm

                    val updated = if (!hasInitializedInputs) {
                        hasInitializedInputs = true
                        current.copy(
                            heightCm = effectiveHeight,
                            weightKg = effectiveWeight,
                            age = effectiveAge,
                            biologicalSex = effectiveSex,
                            neckCm = effectiveNeck,
                            chestCm = effectiveChest,
                            waistCm = effectiveWaist,
                            hipCm = effectiveHip,
                            hasApiKey = hasKey,
                            isAiEnabled = isAiEnabled,
                            savedScans = allScans,
                            latestSavedScan = latestScan
                        )
                    } else {
                        current.copy(
                            hasApiKey = hasKey,
                            isAiEnabled = isAiEnabled,
                            savedScans = allScans,
                            latestSavedScan = latestScan
                        )
                    }
                    updateCalculatedResults(updated)
                }
            }.collect { }
        }
    }

    fun onHeightChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(heightCm = filtered)) }
    }

    fun onWeightChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(weightKg = filtered)) }
    }

    fun onAgeChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { state -> updateCalculatedResults(state.copy(age = filtered)) }
    }

    fun onSexSelected(sex: BiologicalSex) {
        _uiState.update { state -> updateCalculatedResults(state.copy(biologicalSex = sex)) }
    }

    fun onNeckChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(neckCm = filtered)) }
    }

    fun onChestChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(chestCm = filtered)) }
    }

    fun onWaistChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(waistCm = filtered)) }
    }

    fun onHipChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { state -> updateCalculatedResults(state.copy(hipCm = filtered)) }
    }

    fun addPhoto(uri: Uri) {
        _uiState.update {
            if (it.photos.size >= 3) it
            else it.copy(photos = it.photos + uri)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        _uiState.update { current ->
            val remaining = 3 - current.photos.size
            if (remaining <= 0) current
            else {
                val toAdd = uris.take(remaining)
                current.copy(photos = current.photos + toAdd)
            }
        }
    }

    fun removePhoto(index: Int) {
        _uiState.update {
            val list = it.photos.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            it.copy(photos = list)
        }
    }

    private fun updateCalculatedResults(state: BodyFatCalculatorUiState): BodyFatCalculatorUiState {
        val navy = calculateNavyResult(
            state.heightCm,
            state.neckCm,
            state.waistCm,
            state.hipCm,
            state.weightKg,
            state.chestCm,
            state.biologicalSex
        )
        val history = calculateHistoryResult(
            state.heightCm,
            state.weightKg,
            state.age,
            state.biologicalSex
        )
        return state.copy(
            algorithmicResult = navy,
            historyResult = history
        )
    }

    private fun calculateHistoryResult(
        heightStr: String,
        weightStr: String,
        ageStr: String,
        sex: BiologicalSex?
    ): BodyFatCalculationResult? {
        if (sex == null) return null
        val height = heightStr.toDoubleOrNull() ?: return null
        val weight = weightStr.toDoubleOrNull() ?: return null
        val age = ageStr.toIntOrNull() ?: return null
        return bodyFatCalculator.calculateBiometricHistoryBodyFat(
            heightCm = height,
            weightKg = weight,
            age = age,
            biologicalSex = sex
        )
    }

    private fun calculateNavyResult(
        heightStr: String,
        neckStr: String,
        waistStr: String,
        hipStr: String,
        weightStr: String,
        chestStr: String,
        sex: BiologicalSex?
    ) = if (sex != null) {
        val height = heightStr.toDoubleOrNull()
        val neck = neckStr.toDoubleOrNull()
        val waist = waistStr.toDoubleOrNull()
        val hip = hipStr.toDoubleOrNull()
        val weight = weightStr.toDoubleOrNull()
        val chest = chestStr.toDoubleOrNull()
        if (height != null && neck != null && waist != null) {
            bodyFatCalculator.calculateNavyBodyFat(
                heightCm = height,
                neckCm = neck,
                waistCm = waist,
                hipCm = hip,
                weightKg = weight,
                chestCm = chest,
                biologicalSex = sex
            )
        } else null
    } else null

    fun analyzeWithAi() {
        val state = _uiState.value
        if (state.photos.isEmpty()) {
            _uiState.update { it.copy(aiErrorMessage = "Please add at least 1 photo for AI vision analysis.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiAnalyzing = true, aiErrorMessage = null) }

            val imagesBase64 = state.photos.mapNotNull { uri ->
                context.readImageAsBase64Jpeg(uri)
            }

            if (imagesBase64.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isAiAnalyzing = false,
                        aiErrorMessage = "Could not process selected image files."
                    )
                }
                return@launch
            }

            val result = bodyFatAnalyzer.analyzeBodyFat(
                imagesBase64 = imagesBase64,
                heightCm = state.heightCm.toDoubleOrNull(),
                weightKg = state.weightKg.toDoubleOrNull(),
                age = state.age.toIntOrNull(),
                biologicalSex = state.biologicalSex,
                neckCm = state.neckCm.toDoubleOrNull(),
                chestCm = state.chestCm.toDoubleOrNull(),
                waistCm = state.waistCm.toDoubleOrNull(),
                hipCm = state.hipCm.toDoubleOrNull(),
                calculatedNavyBf = state.algorithmicResult?.bodyFatPercentage
            )

            result.onSuccess { analysis ->
                if (!analysis.isValid) {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiErrorMessage = analysis.rejectionReason ?: "Physique could not be analyzed."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAiAnalyzing = false,
                            aiAnalysisResult = analysis,
                            aiErrorMessage = null
                        )
                    }
                }
            }.onFailure { error ->
                val message = when (error) {
                    is OpenRouterException.InvalidApiKey -> "OpenRouter API key is invalid or not set in Settings."
                    is OpenRouterException.RateLimited -> "Rate limit reached. Please retry in a few moments."
                    is OpenRouterException.NoNetwork -> "Network error connecting to OpenRouter. Check your connection."
                    is OpenRouterException.ServerError -> "OpenRouter returned a server error. Try again shortly."
                    is OpenRouterException.MalformedResponse -> "Unexpected AI response format."
                    else -> error.localizedMessage ?: "Failed to analyze body fat."
                }
                _uiState.update {
                    it.copy(
                        isAiAnalyzing = false,
                        aiErrorMessage = message
                    )
                }
            }
        }
    }

    fun saveAssessment(useAiResult: Boolean, useHistoryResult: Boolean = false) {
        val state = _uiState.value
        val today = LocalDate.now(ZoneId.systemDefault())

        val (bfPercentage, category, minRange, maxRange, method, leanMass, fatMass, observations, insights, note) =
            if (useAiResult && state.aiAnalysisResult?.isValid == true && state.aiAnalysisResult.bodyFatPercentage != null) {
                val ai = state.aiAnalysisResult
                val observationsJson = Json.encodeToString(ai.visualObservations)
                val insightsJson = Json.encodeToString(ai.healthInsights)
                AnalysisTuple(
                    ai.bodyFatPercentage!!,
                    ai.category?.displayName ?: "Unknown",
                    ai.confidenceRangeMin,
                    ai.confidenceRangeMax,
                    "AI_MULTIMODAL",
                    ai.leanMassKg,
                    ai.fatMassKg,
                    observationsJson,
                    insightsJson,
                    ai.circumferenceConsistencyNote
                )
            } else if (useHistoryResult && state.historyResult != null) {
                val hist = state.historyResult
                val insightsJson = Json.encodeToString(listOf("Calculated from recorded biometric history (BMI & age demographics)."))
                AnalysisTuple(
                    hist.bodyFatPercentage,
                    hist.category.displayName,
                    null,
                    null,
                    "BIOMETRIC_HISTORY",
                    hist.leanMassKg,
                    hist.fatMassKg,
                    "[]",
                    insightsJson,
                    hist.methodDescription
                )
            } else if (state.algorithmicResult != null) {
                val alg = state.algorithmicResult
                AnalysisTuple(
                    alg.bodyFatPercentage,
                    alg.category.displayName,
                    null,
                    null,
                    "NAVY_CIRCUMFERENCE",
                    alg.leanMassKg,
                    alg.fatMassKg,
                    "[]",
                    "[]",
                    alg.methodDescription
                )
            } else {
                return
            }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val parsedWeight = state.weightKg.toDoubleOrNull()
            val parsedNeck = state.neckCm.toDoubleOrNull()
            val parsedChest = state.chestCm.toDoubleOrNull()
            val parsedWaist = state.waistCm.toDoubleOrNull()
            val parsedHip = state.hipCm.toDoubleOrNull()
            val parsedHeight = state.heightCm.toDoubleOrNull()
            val parsedAge = state.age.toIntOrNull()

            val scanEntity = BodyFatScanEntity(
                date = today,
                timestamp = Instant.now(),
                bodyFatPercentage = bfPercentage,
                confidenceMin = minRange,
                confidenceMax = maxRange,
                category = category,
                method = method,
                weightKg = parsedWeight,
                neckCm = parsedNeck,
                chestCm = parsedChest,
                waistCm = parsedWaist,
                hipCm = parsedHip,
                leanMassKg = leanMass,
                fatMassKg = fatMass,
                visualObservationsJson = observations,
                healthInsightsJson = insights,
                consistencyNote = note
            )

            bodyFatRepository.saveScan(scanEntity)

            // Update user profile circumferences & current weight
            val currentProfile = userProfileRepository.getProfileSync()
            val updatedProfile = (currentProfile ?: com.kevan.hangry.data.local.entity.UserProfileEntity()).copy(
                currentWeightKg = parsedWeight ?: currentProfile?.currentWeightKg,
                heightCm = parsedHeight ?: currentProfile?.heightCm,
                age = parsedAge ?: currentProfile?.age,
                biologicalSex = state.biologicalSex?.name ?: currentProfile?.biologicalSex,
                neckCircumferenceCm = parsedNeck ?: currentProfile?.neckCircumferenceCm,
                chestCircumferenceCm = parsedChest ?: currentProfile?.chestCircumferenceCm,
                waistCircumferenceCm = parsedWaist ?: currentProfile?.waistCircumferenceCm,
                hipCircumferenceCm = parsedHip ?: currentProfile?.hipCircumferenceCm,
                updatedAt = Instant.now()
            )
            userProfileRepository.saveProfile(updatedProfile)

            // Record weight measurement into weightDao
            if (parsedWeight != null && parsedWeight > 0.0) {
                val weightEntry = WeightMeasurementEntity(
                    recordFingerprint = "bodyfat_entry_${Instant.now().toEpochMilli()}",
                    timestamp = Instant.now(),
                    weightKg = parsedWeight,
                    sourcePackageName = "com.kevan.hangry.manual"
                )
                weightDao.insertOrIgnore(listOf(weightEntry))
            }

            context.clearCapturedImageCache()

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccessMessage = "Body composition scan saved to your health profile."
                )
            }
        }
    }

    fun deleteScan(id: Long) {
        viewModelScope.launch {
            bodyFatRepository.deleteScan(id)
        }
    }

    fun dismissSaveMessage() {
        _uiState.update { it.copy(saveSuccessMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(aiErrorMessage = null) }
    }

    private data class AnalysisTuple(
        val bodyFatPercentage: Double,
        val category: String,
        val minRange: Double?,
        val maxRange: Double?,
        val method: String,
        val leanMass: Double?,
        val fatMass: Double?,
        val observations: String,
        val insights: String,
        val note: String?
    )

    companion object {
        fun provideFactory(
            context: Context,
            userProfileRepository: UserProfileRepository,
            weightDao: WeightDao,
            heightDao: HeightDao,
            bodyFatCalculator: BodyFatCalculator,
            bodyFatAnalyzer: BodyFatAnalyzer,
            bodyFatRepository: BodyFatRepository,
            secureKeyStore: SecureKeyStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BodyFatCalculatorViewModel(
                    context = context.applicationContext,
                    userProfileRepository = userProfileRepository,
                    weightDao = weightDao,
                    heightDao = heightDao,
                    bodyFatCalculator = bodyFatCalculator,
                    bodyFatAnalyzer = bodyFatAnalyzer,
                    bodyFatRepository = bodyFatRepository,
                    secureKeyStore = secureKeyStore
                ) as T
            }
        }
    }
}
