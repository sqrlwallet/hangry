package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BreathingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: BreathingSessionEntity): Long

    @Query("UPDATE breathing_sessions SET healthConnectSynced = :synced WHERE id = :id")
    suspend fun setHealthConnectSynced(id: Long, synced: Boolean)

    @Query("SELECT * FROM breathing_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY startTime DESC")
    fun getSessionsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BreathingSessionEntity>>

    @Query("SELECT * FROM breathing_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<BreathingSessionEntity>>

    @Query("SELECT * FROM breathing_sessions WHERE healthConnectSynced = 0 ORDER BY startTime ASC")
    suspend fun getUnsyncedSessions(): List<BreathingSessionEntity>

    @Query("DELETE FROM breathing_sessions")
    suspend fun deleteAll()
}
