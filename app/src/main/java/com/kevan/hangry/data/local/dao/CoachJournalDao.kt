package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CoachJournalEntity): Long

    @Query("DELETE FROM coach_journal_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM coach_journal_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CoachJournalEntity>>

    @Query("SELECT * FROM coach_journal_entries ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<CoachJournalEntity>

    @Query("DELETE FROM coach_journal_entries")
    suspend fun deleteAll()
}
