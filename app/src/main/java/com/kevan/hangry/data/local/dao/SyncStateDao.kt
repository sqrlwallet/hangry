package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(states: List<SyncStateEntity>)

    @Query("SELECT * FROM sync_state WHERE dataType = :dataType LIMIT 1")
    suspend fun getForDataType(dataType: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state ORDER BY dataType ASC")
    fun getAllSyncStates(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_state ORDER BY dataType ASC")
    suspend fun getAllSyncStatesList(): List<SyncStateEntity>

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
