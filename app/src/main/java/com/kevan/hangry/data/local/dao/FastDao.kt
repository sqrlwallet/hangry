package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kevan.hangry.data.local.entity.FastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FastDao {
    @Insert
    suspend fun insert(fast: FastEntity): Long

    @Update
    suspend fun update(fast: FastEntity)

    @Query("SELECT * FROM fasts WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun getActive(): FastEntity?

    @Query("SELECT * FROM fasts ORDER BY startAt DESC")
    fun observeAll(): Flow<List<FastEntity>>

    @Query("SELECT * FROM fasts ORDER BY startAt DESC")
    suspend fun getAll(): List<FastEntity>

    @Query("DELETE FROM fasts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM fasts")
    suspend fun deleteAll()
}
