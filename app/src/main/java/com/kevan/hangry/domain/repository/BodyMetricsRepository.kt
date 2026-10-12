package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One place that gathers height, weight, tape measurements, the latest body-fat scan and the
 * last week of steps/workouts, and turns them into every body metric plus the maintenance /
 * goal calorie estimate - shared by the Body Metrics screen, the dashboard and Ask Dash.
 */
interface BodyMetricsRepository {
    fun observe(): Flow<BodyMetricsSnapshot>
    suspend fun current(): BodyMetricsSnapshot

    /**
     * Saves a weigh-in typed into Hangry (quick log, Settings, onboarding). The newest weigh-in
     * also becomes the profile's current weight. Stays on this device - Hangry doesn't write
     * weight to Health Connect.
     */
    suspend fun logWeight(kg: Double, takenAt: Instant = Instant.now())
}
