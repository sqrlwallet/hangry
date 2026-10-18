package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.StressResult

interface StressCalculator {
    /**
     * @param hrvHistory the nightly HRV (RMSSD, ms) of the days before today, up to 30.
     * @param rhrHistory the resting heart rate (bpm) of the same days.
     */
    fun calculateStress(
        hrvToday: Double?,
        hrvHistory: List<Double>,
        rhrToday: Double?,
        rhrHistory: List<Double>,
        dayStrain: Double? = null
    ): StressResult?
}
