package com.kevan.hangry.domain.model

enum class ScoreConfidence {
    LOW,
    MEDIUM,
    HIGH
}

enum class RecoveryState {
    PRIMED,
    BALANCED,
    REBUILD,
    BUILDING_BASELINE
}

enum class DataQualityState {
    VALID,
    SUSPECT,
    UNAVAILABLE
}

enum class SyncStatus {
    IDLE,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

enum class ExerciseType {
    RUNNING,
    CYCLING,
    SWIMMING,
    WALKING,
    STRENGTH_TRAINING,
    HIIT,
    YOGA,
    OTHER
}

enum class HealthDataType {
    SLEEP,
    STEPS,
    EXERCISE,
    HEART_RATE,
    RESTING_HEART_RATE,
    HRV,
    WEIGHT
}
