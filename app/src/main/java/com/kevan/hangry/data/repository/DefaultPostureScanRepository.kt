package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.PostureScanDao
import com.kevan.hangry.data.local.entity.PostureScanEntity
import com.kevan.hangry.domain.repository.PostureScanRepository
import kotlinx.coroutines.flow.Flow

class DefaultPostureScanRepository(
    private val dao: PostureScanDao
) : PostureScanRepository {
    override fun getAll(): Flow<List<PostureScanEntity>> = dao.getAll()
    override fun getLatest(): Flow<PostureScanEntity?> = dao.getLatest()
    override suspend fun getById(id: Long): PostureScanEntity? = dao.getById(id)
    override suspend fun insert(scan: PostureScanEntity): Long = dao.insert(scan)
    override suspend fun delete(scan: PostureScanEntity) = dao.delete(scan)
    override suspend fun deleteAll() = dao.deleteAll()
}
