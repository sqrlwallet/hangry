package com.kevan.hangry.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Every Hangry widget refreshes the whole set, so pinning one shows current data straight away. */
abstract class HangryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        HangryWidgetUpdater.updateAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HangryWidgetUpdater.updateAllWidgets(context)
    }
}

class BreatheWidgetProvider : HangryWidgetProvider()
class HeartWidgetProvider : HangryWidgetProvider()
class HealthMarkersWidgetProvider : HangryWidgetProvider()
class GoalsWidgetProvider : HangryWidgetProvider()
class WeightWidgetProvider : HangryWidgetProvider()
class PostureWidgetProvider : HangryWidgetProvider()
class CycleWidgetProvider : HangryWidgetProvider()
class CaloriesWidgetProvider : HangryWidgetProvider()
class NutritionWidgetProvider : HangryWidgetProvider()
class FastingWidgetProvider : HangryWidgetProvider()
