package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.UserProfileDao
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow

class DefaultUserProfileRepository(
    private val dao: UserProfileDao
) : UserProfileRepository {
    override fun getProfile(): Flow<UserProfileEntity?> = dao.getProfile()

    override suspend fun getProfileSync(): UserProfileEntity? = dao.getProfileSync()

    override suspend fun saveProfile(profile: UserProfileEntity) {
        dao.insertOrReplace(profile)
    }
}
