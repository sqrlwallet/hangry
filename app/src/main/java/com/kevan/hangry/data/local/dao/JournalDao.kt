package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entry: JournalEntryEntity)

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    fun getEntryForDate(date: LocalDate): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("DELETE FROM journal_entries WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}
