package com.kevan.hangry.ui.nutrition

import android.net.Uri
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.domain.model.NutritionReview
import com.kevan.hangry.domain.model.NutritionTip
import com.kevan.hangry.domain.model.NutritionWeekSummary
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
    val errorMessage: String? = null,
    /**
     * A meal photo the AI couldn't handle (AI off, no key, or the request failed). Opens the
     * manual details sheet with the photo already attached so the shot isn't lost.
     */
    val manualReviewPhoto: Uri? = null,
    /** Why the photo landed in manual review, shown at the top of that sheet. */
    val manualReviewNotice: String? = null,
    /** A just-logged meal that may contain one of the user's allergies. */
    val allergenAlert: AllergenAlert? = null,
    /** Set while the weekly review sheet is open. */
    val weeklyReview: WeeklyReviewState? = null
) {
    val isViewingToday: Boolean get() = selectedDate == LocalDate.now()
}

data class AllergenAlert(val entry: FoodLogEntity, val warnings: List<String>)

/** The last week of eating: rule-based tips straight away, then the AI's review when AI is on. */
data class WeeklyReviewState(
    val calorieTarget: Int? = null,
    val aiAvailable: Boolean = false,
    val isLoading: Boolean = true,
    val summary: NutritionWeekSummary? = null,
    val tips: List<NutritionTip> = emptyList(),
    val isAiLoading: Boolean = false,
    val aiReview: NutritionReview? = null,
    val aiError: String? = null
)
