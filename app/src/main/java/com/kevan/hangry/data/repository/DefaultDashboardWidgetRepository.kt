package com.kevan.hangry.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.repository.DashboardWidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_widgets")

class DefaultDashboardWidgetRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false }
) : DashboardWidgetRepository {

    private val widgetsKey = stringPreferencesKey("configured_widgets_json")

    override fun getWidgets(): Flow<List<DashboardWidget>> {
        return context.widgetDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                getCurrentList(preferences)
            }
    }

    override suspend fun toggleWidgetVisibility(widgetId: String, isVisible: Boolean) {
        context.widgetDataStore.edit { preferences ->
            val currentList = getCurrentList(preferences)
            val updated = currentList.map {
                if (it.id == widgetId) it.copy(isVisible = isVisible) else it
            }
            preferences[widgetsKey] = json.encodeToString(updated)
        }
    }

    override suspend fun reorderWidgets(orderedIds: List<String>) {
        context.widgetDataStore.edit { preferences ->
            val currentList = getCurrentList(preferences)
            val map = currentList.associateBy { it.id }
            val reordered = mutableListOf<DashboardWidget>()

            orderedIds.forEachIndexed { index, id ->
                map[id]?.let {
                    reordered.add(it.copy(order = index))
                }
            }
            // Add any widgets that weren't in orderedIds at the end
            val baseIndex = reordered.size
            currentList.filterNot { it.id in orderedIds }.forEachIndexed { idx, widget ->
                reordered.add(widget.copy(order = baseIndex + idx))
            }

            preferences[widgetsKey] = json.encodeToString(reordered)
        }
    }

    override suspend fun addCustomWidget(widget: DashboardWidget) {
        context.widgetDataStore.edit { preferences ->
            val currentList = getCurrentList(preferences).toMutableList()
            currentList.add(widget.copy(order = currentList.size))
            preferences[widgetsKey] = json.encodeToString(currentList)
        }
    }

    override suspend fun removeWidget(widgetId: String) {
        context.widgetDataStore.edit { preferences ->
            val currentList = getCurrentList(preferences)
            val updated = currentList.filterNot { it.id == widgetId }
            preferences[widgetsKey] = json.encodeToString(updated)
        }
    }

    override suspend fun resetToDefault() {
        context.widgetDataStore.edit { preferences ->
            preferences[widgetsKey] = json.encodeToString(DashboardWidget.createDefaultWidgets())
        }
    }

    private fun getCurrentList(preferences: Preferences): List<DashboardWidget> {
        val jsonString = preferences[widgetsKey]
        val list = if (jsonString.isNullOrBlank()) {
            DashboardWidget.createDefaultWidgets()
        } else {
            try {
                json.decodeFromString<List<DashboardWidget>>(jsonString)
            } catch (_: Exception) {
                DashboardWidget.createDefaultWidgets()
            }
        }
        val defaultWidgets = DashboardWidget.createDefaultWidgets()
        val existingIds = list.map { it.id }.toSet()
        val missingDefaults = defaultWidgets.filterNot { it.id in existingIds }
        val mergedList = if (missingDefaults.isNotEmpty()) {
            list + missingDefaults.mapIndexed { idx, w -> w.copy(order = list.size + idx) }
        } else {
            list
        }
        return mergedList.sortedBy { it.order }
    }
}
