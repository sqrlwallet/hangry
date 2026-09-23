package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * One place that gathers height, weight, tape measurements, the latest body-fat scan and the
 * last week of steps/workouts, and turns them into every body metric plus the maintenance /
 * goal calorie estimate - shared by the Body Metrics screen, the dashboard and Ask Dash.
 */
interface BodyMetricsRepository {
    fun observe(): Flow<BodyMetricsSnapshot>
    suspend fun current(): BodyMetricsSnapshot
}
