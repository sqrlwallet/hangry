package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepAnalysis
import java.time.LocalDate

interface SleepCalculator {
    /**
     * @param recentSessions sessions before [currentSession], newest first; sleep debt over the
     *   last week comes from these.
     * @param previousDayStrain yesterday's day-strain, compared against [rollingAverageStrain]
     *   to add recovery time after an unusually demanding day.
     * @param currentDaySessions the separate sessions that make up [currentSession], so naps
     *   taken after waking can count toward tonight's need. Defaults to [currentSession] alone.
     * @param date the wake-up day being analysed, so a nap the afternoon before counts toward
     *   that night's need. Defaults to [currentSession]'s end date.
     * @param age the user's age, for how much deep sleep to expect (it falls with age).
     */
    fun analyzeSleep(
        currentSession: SleepSessionEntity?,
        recentSessions: List<SleepSessionEntity>,
        targetDurationMinutes: Int = 480,
        previousDayStrain: Double? = null,
        rollingAverageStrain: Double? = null,
        currentDaySessions: List<SleepSessionEntity>? = null,
        date: LocalDate? = null,
        age: Int? = null
    ): SleepAnalysis
}
