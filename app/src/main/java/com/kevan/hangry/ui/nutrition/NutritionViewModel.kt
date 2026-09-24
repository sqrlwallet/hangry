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
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.portionedName
import com.kevan.hangry.data.local.entity.savedMealKey
import com.kevan.hangry.domain.model.CommonFood
import com.kevan.hangry.domain.ai.FoodAnalyzer
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.model.FoodAnalysisResult
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.MealPlanRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Photo/description in, logged entry out - no confirmation tap in between. The AI's estimate is
 * saved (and synced to Health Connect) the moment it comes back; if it's wrong, the snackbar's
 * Edit action or tapping the row in the log corrects it in place rather than blocking the save
 * on a review step every time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context,
    private val foodLogRepository: FoodLogRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val foodAnalyzer: FoodAnalyzer,
    private val healthConnectDataSource: HealthConnectDataSource,
    private val userProfileRepository: UserProfileRepository,
    /** Source of the user's allergies for meal allergen alerts; none recorded means no check. */
    private val healthRecordsRepository: HealthRecordsRepository? = null
) : ViewModel() {

    private suspend fun allergies(): List<String> =
        healthRecordsRepository?.current()?.allergies?.map { it.name }.orEmpty()

    private val zone = ZoneId.systemDefault()
    private val _selectedDate = MutableStateFlow(LocalDate.now(zone))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private var lastKnownToday: LocalDate = LocalDate.now(zone)

    /**
     * The app came back to the foreground, or the clock passed midnight. If the screen was
     * following "today", it moves to the new today - otherwise a meal logged after midnight
     * would land on yesterday. A day the user picked on purpose stays as it is.
     */
    fun onDayMaybeChanged() {
        val today = LocalDate.now(zone)
        if (today == lastKnownToday) return
        if (_selectedDate.value == lastKnownToday) _selectedDate.value = today
        lastKnownToday = today
    }

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    private data class NutritionSources(
        val targetDate: LocalDate,
        val entries: List<FoodLogEntity>,
        val totalCalories: Int,
        val plans: List<MealPlanEntity>,
        val profile: UserProfileEntity?
    )

    init {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                combine(
                    foodLogRepository.getForDate(date),
                    foodLogRepository.getTotalCaloriesForDate(date),
                    mealPlanRepository.getAll(),
                    userProfileRepository.getProfile()
                ) { entries, total, plans, profile ->
                    NutritionSources(date, entries, total, plans, profile)
                }
            }.collect { sources ->
                _uiState.update {
                    it.copy(
                        selectedDate = sources.targetDate,
                        todayEntries = sources.entries,
                        totalCaloriesToday = sources.totalCalories,
                        mealPlans = sources.plans,
                        aiFeaturesEnabled = sources.profile?.aiFeaturesEnabled ?: false
                    )
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * The one entry point for "log a meal": a photo straight from the camera (or gallery).
     * With AI on, it's analyzed and logged with no form at all - the user gets an Edit shortcut
     * afterwards. Only when AI can't do it does the manual details sheet appear, photo attached.
     */
    fun logMealFromPhoto(uri: Uri) {
        if (!_uiState.value.aiFeaturesEnabled) {
            openManualReview(uri, "Turn on AI Features in Settings and meals are filled in from the photo automatically.")
            return
        }
        analyzePhoto(uri, note = null)
    }

    fun analyzePhoto(uri: Uri, note: String?) {
        val base64 = context.readImageAsBase64Jpeg(uri)
        if (base64 == null) {
            _uiState.update { it.copy(errorMessage = "Couldn't read that photo. Try again.") }
            return
        }
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            foodAnalyzer.analyzePhoto(base64, note, allergies()).fold(
                onSuccess = { result -> autoSave(result, FoodLogSource.PHOTO, uri) },
                onFailure = { e ->
                    _uiState.update { it.copy(isAnalyzing = false) }
                    openManualReview(uri, "AI couldn't read this one: ${e.messageOrDefault()}")
                }
            )
        }
    }

    private fun openManualReview(uri: Uri, notice: String) {
        _uiState.update { it.copy(manualReviewPhoto = uri, manualReviewNotice = notice) }
    }

    fun dismissManualReview() {
        _uiState.update { it.copy(manualReviewPhoto = null, manualReviewNotice = null) }
    }

    fun analyzeDescription(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            foodAnalyzer.analyzeDescription(text, allergies()).fold(
                onSuccess = { result -> autoSave(result, FoodLogSource.MANUAL, photoUri = null) },
                onFailure = { e ->
                    _uiState.update { it.copy(isAnalyzing = false, errorMessage = e.messageOrDefault()) }
                }
            )
        }
    }

    fun quickLogMeal(
        foodName: String,
        calories: Int,
        photoUri: Uri? = null,
        proteinG: Double = 0.0,
        carbsG: Double = 0.0,
        fatG: Double = 0.0
    ) {
        val safeName = foodName.ifBlank { "Meal" }
        val safeCalories = calories.coerceAtLeast(0)
        viewModelScope.launch {
            val permanentPhotoPath = photoUri?.let { movePhotoToPermanentStorage(it) }
            val entry = FoodLogEntity(
                date = _selectedDate.value,
                timestamp = Instant.now(),
                source = if (photoUri != null) FoodLogSource.PHOTO else FoodLogSource.MANUAL,
                foodName = safeName,
                calories = safeCalories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                photoPath = permanentPhotoPath
            )
            val id = foodLogRepository.insert(entry)
            val saved = entry.copy(id = id)
            val synced = healthConnectDataSource.writeNutritionRecord(entry)
            if (synced) foodLogRepository.markSyncedToHealthConnect(id)
            if (photoUri != null) {
                context.clearCapturedImageCache()
            }
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    lastSavedEntry = saved
                )
            }
        }
    }

    suspend fun estimateFood(photoUri: Uri?, note: String?): Result<FoodAnalysisResult> {
        val base64 = photoUri?.let { context.readImageAsBase64Jpeg(it) }
        return if (base64 != null) {
            foodAnalyzer.analyzePhoto(base64, note, allergies())
        } else if (!note.isNullOrBlank()) {
            foodAnalyzer.analyzeDescription(note)
        } else {
            Result.failure(IllegalArgumentException("Provide a photo or description to analyze."))
        }
    }

    private suspend fun autoSave(analysis: FoodAnalysisResult, source: String, photoUri: Uri?) {
        val permanentPhotoPath = photoUri?.let { movePhotoToPermanentStorage(it) }
        val entry = FoodLogEntity(
            date = _selectedDate.value,
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
                errorMessage = if (!synced) "Logged, but couldn't sync to Health Connect." else null,
                allergenAlert = analysis.allergenWarnings.takeIf { w -> w.isNotEmpty() }?.let { w -> AllergenAlert(saved, w) }
            )
        }
    }

    fun dismissAllergenAlert() {
        _uiState.update { it.copy(allergenAlert = null) }
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

    fun logFromMealPlan(plan: MealPlanEntity, portion: Double = 1.0) {
        logFoodInstantly(
            FoodLogEntity(
                date = _selectedDate.value,
                timestamp = Instant.now(),
                source = FoodLogSource.MEAL_PLAN,
                foodName = portionedName(plan.name, portion),
                calories = (plan.calories * portion).roundToInt(),
                proteinG = plan.proteinG * portion,
                carbsG = plan.carbsG * portion,
                fatG = plan.fatG * portion,
                fiberG = plan.fiberG * portion,
                sugarG = plan.sugarG * portion,
                sodiumMg = plan.sodiumMg * portion
            )
        )
    }

    /** One tap on a built-in food (banana, egg, ...) logs [portion] standard servings of it. */
    fun logCommonFood(food: CommonFood, portion: Double = 1.0) {
        logFoodInstantly(
            FoodLogEntity(
                date = _selectedDate.value,
                timestamp = Instant.now(),
                source = FoodLogSource.MANUAL,
                foodName = portionedName(food.name, portion),
                calories = (food.calories * portion).roundToInt(),
                proteinG = food.proteinG * portion,
                carbsG = food.carbsG * portion,
                fatG = food.fatG * portion,
                fiberG = food.fiberG * portion,
                sugarG = food.sugarG * portion,
                sodiumMg = food.sodiumMg * portion
            )
        )
    }

    private fun logFoodInstantly(entry: FoodLogEntity) {
        viewModelScope.launch {
            val id = foodLogRepository.insert(entry)
            if (healthConnectDataSource.writeNutritionRecord(entry)) {
                foodLogRepository.markSyncedToHealthConnect(id)
            }
            _uiState.update { it.copy(lastSavedEntry = entry.copy(id = id)) }
        }
    }

    /** Adds a saved meal by hand, or saves changes to one (a rename replaces the old entry). */
    fun saveMeal(meal: MealPlanEntity, replacing: MealPlanEntity? = null) {
        viewModelScope.launch {
            if (replacing != null && savedMealKey(replacing.name) != savedMealKey(meal.name)) {
                mealPlanRepository.delete(replacing)
            }
            mealPlanRepository.upsert(meal)
        }
    }

    fun deleteSavedMeal(meal: MealPlanEntity) {
        viewModelScope.launch { mealPlanRepository.delete(meal) }
    }

    /** Puts back a saved meal removed a moment ago, stats and all. */
    fun restoreSavedMeal(meal: MealPlanEntity) {
        viewModelScope.launch { mealPlanRepository.upsert(meal.copy(id = 0)) }
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
            userProfileRepository: UserProfileRepository,
            healthRecordsRepository: HealthRecordsRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NutritionViewModel(
                    context = context.applicationContext,
                    foodLogRepository = foodLogRepository,
                    mealPlanRepository = mealPlanRepository,
                    foodAnalyzer = foodAnalyzer,
                    healthConnectDataSource = healthConnectDataSource,
                    userProfileRepository = userProfileRepository,
                    healthRecordsRepository = healthRecordsRepository
                ) as T
            }
        }
    }
}
