package com.kevan.hangry.ui.nutrition

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.domain.ai.FoodAnalyzer
import com.kevan.hangry.domain.model.FoodAnalysisResult
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.MealPlanRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Photo/description in, logged entry out - no confirmation tap in between. The AI's estimate is
 * saved (and synced to Health Connect) the moment it comes back; if it's wrong, the snackbar's
 * Edit action or tapping the row in the log corrects it in place rather than blocking the save
 * on a review step every time.
 */
class NutritionViewModel(
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context,
    private val foodLogRepository: FoodLogRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val foodAnalyzer: FoodAnalyzer,
    private val healthConnectDataSource: HealthConnectDataSource,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                foodLogRepository.getForDate(today),
                foodLogRepository.getTotalCaloriesForDate(today),
                mealPlanRepository.getAll(),
                userProfileRepository.getProfile()
            ) { entries, total, plans, profile ->
                _uiState.update {
                    it.copy(
                        todayEntries = entries,
                        totalCaloriesToday = total,
                        mealPlans = plans,
                        aiFeaturesEnabled = profile?.aiFeaturesEnabled ?: false
                    )
                }
            }.collect { }
        }
    }

    fun analyzePhoto(uri: Uri, note: String?) {
        val base64 = context.readImageAsBase64Jpeg(uri)
        if (base64 == null) {
            _uiState.update { it.copy(errorMessage = "Couldn't read that photo. Try again.") }
            return
        }
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            foodAnalyzer.analyzePhoto(base64, note).fold(
                onSuccess = { result -> autoSave(result, FoodLogSource.PHOTO, uri) },
                onFailure = { e ->
                    _uiState.update { it.copy(isAnalyzing = false, errorMessage = e.messageOrDefault()) }
                }
            )
        }
    }

    fun analyzeDescription(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            foodAnalyzer.analyzeDescription(text).fold(
                onSuccess = { result -> autoSave(result, FoodLogSource.MANUAL, photoUri = null) },
                onFailure = { e ->
                    _uiState.update { it.copy(isAnalyzing = false, errorMessage = e.messageOrDefault()) }
                }
            )
        }
    }

    private suspend fun autoSave(analysis: FoodAnalysisResult, source: String, photoUri: Uri?) {
        val permanentPhotoPath = photoUri?.let { movePhotoToPermanentStorage(it) }
        val entry = FoodLogEntity(
            date = today,
            timestamp = Instant.now(),
            source = source,
            foodName = analysis.foodName,
            calories = analysis.calories,
            proteinG = analysis.proteinG,
            carbsG = analysis.carbsG,
            fatG = analysis.fatG,
            fiberG = analysis.fiberG,
            sugarG = analysis.sugarG,
            sodiumMg = analysis.sodiumMg,
            photoPath = permanentPhotoPath
        )
        val id = foodLogRepository.insert(entry)
        val saved = entry.copy(id = id)
        val synced = healthConnectDataSource.writeNutritionRecord(entry)
        if (synced) foodLogRepository.markSyncedToHealthConnect(id)
        context.clearCapturedImageCache()
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                lastSavedEntry = saved,
                errorMessage = if (!synced) "Logged, but couldn't sync to Health Connect." else null
            )
        }
    }

    fun clearLastSaved() {
        _uiState.update { it.copy(lastSavedEntry = null) }
    }

    fun startEdit(entry: FoodLogEntity) {
        _uiState.update { it.copy(editingEntry = entry, lastSavedEntry = null) }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingEntry = null) }
    }

    fun saveEdit(updated: FoodLogEntity) {
        viewModelScope.launch {
            foodLogRepository.update(updated)
            _uiState.update { it.copy(editingEntry = null) }
        }
    }

    fun logFromMealPlan(plan: MealPlanEntity) {
        viewModelScope.launch {
            val entry = FoodLogEntity(
                date = today,
                timestamp = Instant.now(),
                source = FoodLogSource.MEAL_PLAN,
                foodName = plan.name,
                calories = plan.calories,
                proteinG = plan.proteinG,
                carbsG = plan.carbsG,
                fatG = plan.fatG
            )
            val id = foodLogRepository.insert(entry)
            if (healthConnectDataSource.writeNutritionRecord(entry)) {
                foodLogRepository.markSyncedToHealthConnect(id)
            }
            _uiState.update { it.copy(lastSavedEntry = entry.copy(id = id)) }
        }
    }

    fun deleteEntry(entry: FoodLogEntity) {
        viewModelScope.launch {
            foodLogRepository.delete(entry)
            entry.photoPath?.let { File(it).delete() }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun movePhotoToPermanentStorage(sourceUri: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, "food_photos").apply { mkdirs() }
            val dest = File(dir, "food_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        }.getOrNull()
    }

    private fun Throwable.messageOrDefault(): String =
        (this as? OpenRouterException)?.message ?: "Something went wrong analyzing that. Try again."

    companion object {
        fun provideFactory(
            context: Context,
            foodLogRepository: FoodLogRepository,
            mealPlanRepository: MealPlanRepository,
            foodAnalyzer: FoodAnalyzer,
            healthConnectDataSource: HealthConnectDataSource,
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NutritionViewModel(
                    context = context.applicationContext,
                    foodLogRepository = foodLogRepository,
                    mealPlanRepository = mealPlanRepository,
                    foodAnalyzer = foodAnalyzer,
                    healthConnectDataSource = healthConnectDataSource,
                    userProfileRepository = userProfileRepository
                ) as T
            }
        }
    }
}
