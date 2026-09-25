package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.LongevityCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface LongevityCheckInDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(checkIn: LongevityCheckInEntity)

    @Query("DELETE FROM longevity_check_ins WHERE date = :date AND pillar = :pillar")
    suspend fun delete(date: LocalDate, pillar: String)

    @Query("SELECT * FROM longevity_check_ins WHERE date BETWEEN :start AND :end")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<LongevityCheckInEntity>>

    @Query("DELETE FROM longevity_check_ins")
    suspend fun deleteAll()
}
