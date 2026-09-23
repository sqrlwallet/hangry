package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.HrvFeelingEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HrvFeelingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feeling: HrvFeelingEntity)

    @Query("SELECT * FROM hrv_feelings WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<HrvFeelingEntity?>

    @Query("SELECT * FROM hrv_feelings WHERE date BETWEEN :start AND :end")
    suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<HrvFeelingEntity>

    @Query("DELETE FROM hrv_feelings")
    suspend fun deleteAll()
}
