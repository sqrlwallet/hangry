package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kevan.hangry.data.local.entity.SupplementEntity
import com.kevan.hangry.data.local.entity.SupplementIntakeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SupplementDao {
    @Insert
    suspend fun insert(supplement: SupplementEntity): Long

    @Update
    suspend fun update(supplement: SupplementEntity)

    @Query("SELECT * FROM supplements ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplements ORDER BY active DESC, name COLLATE NOCASE")
    suspend fun getAll(): List<SupplementEntity>

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getById(id: Long): SupplementEntity?

    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntakes(intakes: List<SupplementIntakeEntity>): List<Long>

    @Query("DELETE FROM supplement_intakes WHERE supplementId = :supplementId AND date = :date AND scheduledTime = :time")
    suspend fun deleteIntake(supplementId: Long, date: LocalDate, time: String)

    @Query("DELETE FROM supplement_intakes WHERE supplementId = :supplementId")
    suspend fun deleteIntakesFor(supplementId: Long)

    @Query("SELECT * FROM supplement_intakes WHERE date BETWEEN :start AND :end")
    fun observeIntakesBetween(start: LocalDate, end: LocalDate): Flow<List<SupplementIntakeEntity>>

    @Query("SELECT * FROM supplement_intakes WHERE date BETWEEN :start AND :end")
    suspend fun getIntakesBetween(start: LocalDate, end: LocalDate): List<SupplementIntakeEntity>

    @Query("DELETE FROM supplements")
    suspend fun deleteAllSupplements()

    @Query("DELETE FROM supplement_intakes")
    suspend fun deleteAllIntakes()
}
