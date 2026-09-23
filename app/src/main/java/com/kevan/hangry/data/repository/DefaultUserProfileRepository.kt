package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.UserProfileDao
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow

class DefaultUserProfileRepository(
    private val dao: UserProfileDao
) : UserProfileRepository {
    override fun getProfile(): Flow<UserProfileEntity?> = dao.getProfile()

    override suspend fun getProfileSync(): UserProfileEntity? = dao.getProfileSync()

    override suspend fun saveProfile(profile: UserProfileEntity) {
        // With a date of birth, the whole-year age is always derived from it, never stale.
        dao.insertOrReplace(profile.dateOfBirth?.let { profile.copy(age = AgeMath.years(it)) } ?: profile)
    }
}
