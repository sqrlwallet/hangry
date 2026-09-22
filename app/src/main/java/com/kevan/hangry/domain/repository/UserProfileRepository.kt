package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun getProfile(): Flow<UserProfileEntity?>
    suspend fun getProfileSync(): UserProfileEntity?
    suspend fun saveProfile(profile: UserProfileEntity)
}
