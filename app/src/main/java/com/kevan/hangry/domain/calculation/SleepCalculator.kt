package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepAnalysis

interface SleepCalculator {
    /**
     * @param recentSessions sessions before [currentSession], newest first; sleep debt over the
     *   last week comes from these.
     * @param previousDayStrain yesterday's day-strain, compared against [rollingAverageStrain]
     *   to add recovery time after an unusually demanding day.
     */
    fun analyzeSleep(
        currentSession: SleepSessionEntity?,
        recentSessions: List<SleepSessionEntity>,
        targetDurationMinutes: Int = 480,
        previousDayStrain: Double? = null,
        rollingAverageStrain: Double? = null
    ): SleepAnalysis
}
