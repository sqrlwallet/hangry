package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.PostureScanEntity
import kotlinx.coroutines.flow.Flow

interface PostureScanRepository {
    fun getAll(): Flow<List<PostureScanEntity>>
    fun getLatest(): Flow<PostureScanEntity?>
    suspend fun getById(id: Long): PostureScanEntity?
    suspend fun insert(scan: PostureScanEntity): Long
    suspend fun delete(scan: PostureScanEntity)
    suspend fun deleteAll()
}
