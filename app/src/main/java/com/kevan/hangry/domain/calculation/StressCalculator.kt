package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.StressResult

interface StressCalculator {
    fun calculateStress(
        hrvToday: Double?,
        hrvBaseline7d: Double?,
        rhrToday: Double?,
        rhrBaseline7d: Double?,
        dayStrain: Double? = null,
        baselineDaysCount: Int = 7
    ): StressResult?
}
