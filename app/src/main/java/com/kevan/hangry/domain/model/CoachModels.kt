package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExtractedJournalEntry(
    val category: String = "NOTE", // e.g. "PROBLEM", "DIET", "INJURY", "HABIT", "GOAL", "HEALTH", "NOTE"
    val summary: String,
    val content: String
)

@Serializable
data class CoachResponse(
    val reply: String,
    val journalEntry: ExtractedJournalEntry? = null,
    /** Things Dash offers to do in the app; each needs the user's tap to happen. */
    val actions: List<CoachAction> = emptyList()
)

/**
 * An in-app action Dash proposes (add a supplement, log a meal, save a lab reading...). It's
 * shown as a card under Dash's reply and only runs when the user taps it - Dash never changes
 * data on its own. Only the payload matching [type] is set.
 */
@Serializable
data class CoachAction(
    val type: String,
    /** Short label for the card, e.g. "Add Vitamin D3 at 8:00 AM". */
    val title: String = "",
    val supplement: SupplementActionPayload? = null,
    val meal: MealActionPayload? = null,
    val reading: ReadingActionPayload? = null,
    val goal: GoalActionPayload? = null,
    val item: ProfileItemActionPayload? = null,
    val goals: GoalsActionPayload? = null,
    val weight: WeightActionPayload? = null,
    val supplementName: String? = null,
    /** UPDATE_MEAL: name of the logged meal to change (today's or the latest match). */
    val mealName: String? = null,
    val supplementUpdate: SupplementUpdatePayload? = null,
    val mealPlan: MealPlanActionPayload? = null,
    val pregnancy: PregnancyActionPayload? = null,
    val period: PeriodActionPayload? = null,
    val profile: ProfileActionPayload? = null,
    val sleep: SleepActionPayload? = null,
    /** SET_HRV_FEELING: an HrvFeeling name. */
    val feeling: String? = null,
    val bodyFat: BodyFatActionPayload? = null,
    val workout: WorkoutActionPayload? = null,
    val resolveJournal: ResolveJournalActionPayload? = null,
    val reminders: RemindersActionPayload? = null,
    /** OPEN_SCREEN target - see [Screens]. */
    val screen: String? = null,
    /** OPEN_SCREEN with screen = "breathing": a BreathingPattern id. */
    val breathingPattern: String? = null,
    val status: String = CoachActionStatus.PENDING,
    val resultMessage: String? = null
) {
    companion object Types {
        const val ADD_SUPPLEMENT = "ADD_SUPPLEMENT"
        const val MARK_SUPPLEMENT_TAKEN = "MARK_SUPPLEMENT_TAKEN"
        const val LOG_MEAL = "LOG_MEAL"
        const val ADD_READING = "ADD_READING"
        const val SET_GOAL = "SET_GOAL"
        const val ADD_ALLERGY = "ADD_ALLERGY"
        const val ADD_CONDITION = "ADD_CONDITION"
        const val UPDATE_GOALS = "UPDATE_GOALS"
        const val LOG_WEIGHT = "LOG_WEIGHT"
        const val OPEN_SCREEN = "OPEN_SCREEN"
        const val UPDATE_MEAL = "UPDATE_MEAL"
        const val ADD_MEAL_PLAN = "ADD_MEAL_PLAN"
        const val UPDATE_SUPPLEMENT = "UPDATE_SUPPLEMENT"
        const val SET_PREGNANCY = "SET_PREGNANCY"
        const val LOG_PERIOD = "LOG_PERIOD"
        const val UPDATE_PROFILE = "UPDATE_PROFILE"
        const val LOG_SLEEP = "LOG_SLEEP"
        const val SET_HRV_FEELING = "SET_HRV_FEELING"
        const val LOG_BODY_FAT = "LOG_BODY_FAT"
        const val LOG_WORKOUT = "LOG_WORKOUT"
        const val RESOLVE_JOURNAL_ENTRY = "RESOLVE_JOURNAL_ENTRY"
        const val UPDATE_REMINDERS = "UPDATE_REMINDERS"

        /** Every action Dash can propose - one for each thing a user can enter in the app. */
        val ALL = setOf(
            ADD_SUPPLEMENT, UPDATE_SUPPLEMENT, MARK_SUPPLEMENT_TAKEN,
            LOG_MEAL, UPDATE_MEAL, ADD_MEAL_PLAN,
            ADD_READING, SET_GOAL, ADD_ALLERGY, ADD_CONDITION, SET_PREGNANCY, LOG_PERIOD,
            UPDATE_GOALS, UPDATE_PROFILE, LOG_WEIGHT, LOG_BODY_FAT,
            LOG_SLEEP, SET_HRV_FEELING, OPEN_SCREEN,
            LOG_WORKOUT, RESOLVE_JOURNAL_ENTRY, UPDATE_REMINDERS
        )
    }

    /** Screens Dash may open, by the name it uses in OPEN_SCREEN. */
    object Screens {
        val ALL = setOf(
            "breathing", "supplements", "fasting", "health_records", "body_metrics", "body_fat", "nutrition",
            "sleep", "recovery", "heart", "training", "trends", "posture", "settings"
        )
    }
}

object CoachActionStatus {
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val DISMISSED = "DISMISSED"
    const val FAILED = "FAILED"
}

@Serializable
data class SupplementActionPayload(
    val name: String,
    val brand: String? = null,
    val form: String? = null,
    val doseAmount: Double? = null,
    val doseUnit: String? = null,
    /** "HH:mm", 24-hour. */
    val times: List<String> = emptyList(),
    /** Only when the user asks for help tracking/remembering it; otherwise it's context, assumed taken. */
    val track: Boolean = false,
    val reminders: Boolean = true,
    val ingredients: List<SupplementIngredient> = emptyList(),
    val notes: String? = null
)

@Serializable
data class MealActionPayload(
    val foodName: String,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0
)

@Serializable
data class ReadingActionPayload(
    /** A MarkerType id, e.g. "ldl", "blood_pressure". */
    val marker: String,
    val value: Double,
    val secondaryValue: Double? = null,
    val unit: String? = null,
    /** GlucoseContext name for blood sugar. */
    val context: String? = null,
    /** "YYYY-MM-DD"; today when absent. */
    val date: String? = null
)

@Serializable
data class GoalActionPayload(
    val marker: String,
    val targetValue: Double,
    val targetSecondary: Double? = null,
    val unit: String? = null,
    val targetDate: String? = null
)

@Serializable
data class ProfileItemActionPayload(val name: String, val note: String? = null)

/** Only the fields present are changed. */
@Serializable
data class GoalsActionPayload(
    val weightGoalKg: Double? = null,
    /** "YYYY-MM-DD". */
    val goalDate: String? = null,
    val dailySteps: Long? = null,
    val dailyActiveCalories: Int? = null,
    val sleepHours: Double? = null
)

@Serializable
data class WeightActionPayload(val value: Double, val unit: String? = "kg")

/** Only the fields present change. */
@Serializable
data class SupplementUpdatePayload(
    val doseAmount: Double? = null,
    val doseUnit: String? = null,
    val times: List<String>? = null,
    val track: Boolean? = null,
    val reminders: Boolean? = null,
    /** false pauses it (history is kept). */
    val active: Boolean? = null,
    val notes: String? = null
)

@Serializable
data class MealPlanActionPayload(
    val name: String,
    /** BREAKFAST, LUNCH, DINNER, SNACK or OTHER. */
    val mealType: String? = null,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0
)

@Serializable
data class PregnancyActionPayload(val pregnant: Boolean, val dueDate: String? = null)

@Serializable
data class PeriodActionPayload(val startDate: String, val endDate: String? = null)

/** Only the fields present change. Lengths in [lengthUnit]: "cm" (default) or "in". */
@Serializable
data class ProfileActionPayload(
    val age: Int? = null,
    /** MALE, FEMALE or OTHER. */
    val sex: String? = null,
    val height: Double? = null,
    val neck: Double? = null,
    val chest: Double? = null,
    val waist: Double? = null,
    val hip: Double? = null,
    val lengthUnit: String? = null
)

@Serializable
data class SleepActionPayload(
    val durationMinutes: Int,
    /** "YYYY-MM-DDTHH:mm" wake time; now when absent. */
    val endTime: String? = null
)

@Serializable
data class BodyFatActionPayload(
    val percentage: Double,
    /** Where it came from, e.g. "DEXA scan", "smart scale". */
    val source: String? = null
)

@Serializable
data class WorkoutActionPayload(
    /** Health Connect exercise type name or label, e.g. "RUNNING", "WEIGHTLIFTING", "WALKING", "SWIMMING", "CYCLING", "YOGA", etc. */
    val exerciseType: String = "OTHER_WORKOUT",
    val title: String? = null,
    val durationMinutes: Int,
    val calories: Double? = null,
    val distanceKm: Double? = null,
    /** "YYYY-MM-DDTHH:mm" ISO timestamp or null for ending now */
    val startTime: String? = null,
    val notes: String? = null
)

@Serializable
data class ResolveJournalActionPayload(
    val entryId: Long? = null,
    val summary: String? = null
)

@Serializable
data class RemindersActionPayload(
    val morningReadinessEnabled: Boolean? = null,
    val bedtimeReminderEnabled: Boolean? = null
)

