package com.kevan.hangry.domain.model

data class HeartRateZoneDistribution(
    val zone1Count: Int = 0,
    val zone2Count: Int = 0,
    val zone3Count: Int = 0,
    val zone4Count: Int = 0,
    val zone5Count: Int = 0
) {
    val totalCount: Int get() = zone1Count + zone2Count + zone3Count + zone4Count + zone5Count

    val zone1Pct: Float get() = if (totalCount > 0) zone1Count.toFloat() / totalCount else 0f
    val zone2Pct: Float get() = if (totalCount > 0) zone2Count.toFloat() / totalCount else 0f
    val zone3Pct: Float get() = if (totalCount > 0) zone3Count.toFloat() / totalCount else 0f
    val zone4Pct: Float get() = if (totalCount > 0) zone4Count.toFloat() / totalCount else 0f
    val zone5Pct: Float get() = if (totalCount > 0) zone5Count.toFloat() / totalCount else 0f

    val activeTrainingCount: Int get() = zone2Count + zone3Count + zone4Count + zone5Count

    val primaryFocus: String
        get() = when {
            totalCount == 0 -> "No Heart Rate Data"
            zone5Count > zone4Count && zone5Count > zone3Count && zone5Count > zone2Count -> "Peak / VO2 Max"
            zone4Count > zone3Count && zone4Count > zone2Count -> "Lactate Threshold"
            zone3Count > zone2Count -> "Aerobic Tempo"
            zone2Count > 0 -> "Aerobic Base (Zone 2)"
            else -> "Active Recovery (Zone 1)"
        }

    val physiologicalInsight: String
        get() = when {
            totalCount == 0 -> "Record workouts with a connected wearable to view physiological zone distribution."
            zone2Pct >= 0.4f -> "Excellent aerobic base development. Training in Zone 2 enhances mitochondrial density and fat metabolism with low autonomic recovery cost."
            zone4Pct + zone5Pct >= 0.25f -> "High cardiovascular intensity detected. Ensure adequate recovery sleep and nutrition to allow parasympathetic rebound."
            zone3Pct >= 0.35f -> "Steady tempo cardiovascular work. Good stamina stimulus without excessive neuromuscular strain."
            else -> "Light cardiovascular stimulus. Ideal for maintaining circulation and active restoration."
        }
}
