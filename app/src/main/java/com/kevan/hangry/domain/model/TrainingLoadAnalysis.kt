package com.kevan.hangry.domain.model

data class TrainingLoadAnalysis(
    val dailyLoad: Double,
    val workoutCount: Int,
    val sevenDayAverageLoad: Double,
    val acuteToChronicRatio: Double = 1.0,
    val supportiveNote: String
)
