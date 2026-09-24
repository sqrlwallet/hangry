package com.kevan.hangry.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.data.local.entity.toLogEntry
import com.kevan.hangry.domain.model.CommonFoods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Logs a food from a Nutrition widget quick-add button without opening the app: one portion,
 * today, synced to Health Connect like any other entry. A toast confirms it; mistakes are
 * removed from the Nutrition tab.
 */
class NutritionWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_FOOD) return
        val app = context.applicationContext as? HangryApplication ?: return
        val pending = goAsync()
        scope.launch {
            val message = try {
                val container = app.container
                val today = LocalDate.now(ZoneId.systemDefault())
                val savedId = intent.getLongExtra(EXTRA_SAVED_ID, -1L)
                val entry = if (savedId > 0) {
                    container.mealPlanRepository.getAll().first().firstOrNull { it.id == savedId }?.toLogEntry(today)
                } else {
                    intent.getStringExtra(EXTRA_COMMON_NAME)
                        ?.let { name -> CommonFoods.all.firstOrNull { it.name == name } }
                        ?.toLogEntry(today)
                }
                if (entry == null) {
                    "That meal isn't saved anymore"
                } else {
                    val id = container.foodLogRepository.insert(entry)
                    if (container.healthConnectDataSource.writeNutritionRecord(entry)) {
                        container.foodLogRepository.markSyncedToHealthConnect(id)
                    }
                    "Logged ${entry.foodName} · ${entry.calories} kcal"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                "Couldn't log that. Try again from the app."
            }
            try {
                withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
                HangryWidgetUpdater.updateAllWidgets(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG_FOOD = "com.kevan.hangry.widget.LOG_FOOD"
        private const val EXTRA_SAVED_ID = "saved_meal_id"
        private const val EXTRA_COMMON_NAME = "common_food_name"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun logSavedIntent(context: Context, savedMealId: Long): Intent =
            Intent(context, NutritionWidgetReceiver::class.java).setAction(ACTION_LOG_FOOD).putExtra(EXTRA_SAVED_ID, savedMealId)

        fun logCommonIntent(context: Context, commonFoodName: String): Intent =
            Intent(context, NutritionWidgetReceiver::class.java).setAction(ACTION_LOG_FOOD).putExtra(EXTRA_COMMON_NAME, commonFoodName)
    }
}
