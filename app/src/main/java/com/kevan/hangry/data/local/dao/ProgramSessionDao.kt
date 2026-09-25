package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kevan.hangry.data.local.entity.ProgramSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ProgramSessionDao {
    @Insert
    suspend fun insert(session: ProgramSessionEntity): Long

    @Query("SELECT * FROM program_sessions ORDER BY date DESC, completedAt DESC")
    fun observeAll(): Flow<List<ProgramSessionEntity>>

    @Query("SELECT * FROM program_sessions ORDER BY date DESC, completedAt DESC")
    suspend fun getAll(): List<ProgramSessionEntity>

    @Query("SELECT * FROM program_sessions WHERE date BETWEEN :start AND :end")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ProgramSessionEntity>>

    @Query("DELETE FROM program_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM program_sessions")
    suspend fun deleteAll()
}
