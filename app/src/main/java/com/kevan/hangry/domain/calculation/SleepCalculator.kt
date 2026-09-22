package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepAnalysis

interface SleepCalculator {
    /**
     * @param previousDaySleepDebtMinutes carryover debt from the prior day, used to raise
     *   tonight's sleep need (see CALCULATIONS.md §7).
     * @param previousDayStrain yesterday's day-strain, compared against [rollingAverageStrain]
     *   to add recovery time after an unusually demanding day.
     */
    fun analyzeSleep(
        currentSession: SleepSessionEntity?,
        recentSessions: List<SleepSessionEntity>,
        targetDurationMinutes: Int = 480,
        previousDaySleepDebtMinutes: Int = 0,
        previousDayStrain: Double? = null,
        rollingAverageStrain: Double? = null
    ): SleepAnalysis
}
