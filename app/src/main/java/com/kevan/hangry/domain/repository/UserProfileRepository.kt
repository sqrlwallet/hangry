package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.model.AgeMath
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun getProfile(): Flow<UserProfileEntity?>
    suspend fun getProfileSync(): UserProfileEntity?
    suspend fun saveProfile(profile: UserProfileEntity)

    /**
     * Brings the stored whole-year age up to date with the date of birth - run at app start and
     * hourly, so a birthday passing is picked up by everything that reads `age`.
     */
    suspend fun refreshAgeFromBirthday() {
        val profile = getProfileSync() ?: return
        val dob = profile.dateOfBirth ?: return
        val age = AgeMath.years(dob)
        if (profile.age != age) saveProfile(profile.copy(age = age))
    }
}
