package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.DashboardWidget
import kotlinx.coroutines.flow.Flow

interface DashboardWidgetRepository {
    fun getWidgets(): Flow<List<DashboardWidget>>
    suspend fun toggleWidgetVisibility(widgetId: String, isVisible: Boolean)
    suspend fun reorderWidgets(orderedIds: List<String>)
    suspend fun addCustomWidget(widget: DashboardWidget)
    suspend fun removeWidget(widgetId: String)
    suspend fun resetToDefault()
}
