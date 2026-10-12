package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kevan.hangry.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WaterLogDao {

    @Insert
    suspend fun insert(entry: WaterLogEntity): Long

    @Query("DELETE FROM water_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(SUM(volumeMl), 0) FROM water_log WHERE date = :date")
    fun observeTotalMl(date: LocalDate): Flow<Int>

    @Query("SELECT COALESCE(SUM(volumeMl), 0) FROM water_log WHERE date = :date")
    suspend fun totalMl(date: LocalDate): Int

    @Query("UPDATE water_log SET healthConnectSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT * FROM water_log WHERE healthConnectSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<WaterLogEntity>
}
