package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kevan.hangry.data.local.entity.PostureScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostureScanDao {
    @Insert
    suspend fun insert(scan: PostureScanEntity): Long

    @Delete
    suspend fun delete(scan: PostureScanEntity)

    @Query("SELECT * FROM posture_scans ORDER BY date DESC, timestamp DESC")
    fun getAll(): Flow<List<PostureScanEntity>>

    @Query("SELECT * FROM posture_scans ORDER BY date DESC, timestamp DESC LIMIT 1")
    fun getLatest(): Flow<PostureScanEntity?>

    @Query("SELECT * FROM posture_scans ORDER BY date DESC, timestamp DESC LIMIT 1")
    suspend fun getLatestSync(): PostureScanEntity?

    @Query("SELECT * FROM posture_scans WHERE id = :id")
    suspend fun getById(id: Long): PostureScanEntity?

    @Query("DELETE FROM posture_scans")
    suspend fun deleteAll()
}
