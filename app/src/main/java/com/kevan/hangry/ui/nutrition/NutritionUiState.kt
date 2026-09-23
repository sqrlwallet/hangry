package com.kevan.hangry.ui.nutrition

import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.MealPlanEntity
import java.time.LocalDate

data class NutritionUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val todayEntries: List<FoodLogEntity> = emptyList(),
    val totalCaloriesToday: Int = 0,
    val mealPlans: List<MealPlanEntity> = emptyList(),
    val aiFeaturesEnabled: Boolean = false,
    val isAnalyzing: Boolean = false,
    /** Just auto-saved - drives a brief "Logged X" snackbar with an Edit shortcut, then clears. */
    val lastSavedEntry: FoodLogEntity? = null,
    /** Set to open the edit dialog, either from the snackbar's Edit action or tapping a log row. */
    val editingEntry: FoodLogEntity? = null,
    val errorMessage: String? = null
) {
    val isViewingToday: Boolean get() = selectedDate == LocalDate.now()
}
