package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sleepDurationTargetMinutes: Int = 480, // Default 8h
    val baselineWindowDays: Int = 7,
    val onboardingCompleted: Boolean = false,
    val preferredDataSourcePackage: String? = null,
    val heightCm: Double? = null,
    /** Whole years; kept in step with [dateOfBirth] when that is set (see UserProfileRepository). */
    val age: Int? = null,
    /** Gives an always-current, exact age (Body Age uses e.g. 34.7). */
    val dateOfBirth: LocalDate? = null,
    val biologicalSex: String? = null, // "MALE", "FEMALE", "OTHER" - see BiologicalSex
    val weightGoalKg: Double? = null,
    val goalTargetDate: LocalDate? = null,
    val currentWeightKg: Double? = null,
    val neckCircumferenceCm: Double? = null,
    val chestCircumferenceCm: Double? = null,
    val waistCircumferenceCm: Double? = null,
    val hipCircumferenceCm: Double? = null,
    // Only offered when biologicalSex is FEMALE.
    val isPregnant: Boolean = false,
    val pregnancyDueDate: LocalDate? = null,
    // AI features (food/posture photo analysis via OpenRouter) are opt-in - false until the
    // user explicitly accepts the consent dialog in Settings. The API key itself is never
    // stored here; see data/security/SecureKeyStore.
    val aiFeaturesEnabled: Boolean = false,
    val preferredAiModel: String? = null,
    val preferredCoachModel: String? = null,
    val dailyStepGoal: Long = 6000L,
    val dailyActivityMinutesGoal: Int = 90,
    /** How long the user aims to sleep; drives sleep need, debt and the Sleep screen target. */
    val sleepGoalMinutes: Int = 480,
    val dailyActiveCaloriesGoal: Int = 500,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
