package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.DataSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(source: DataSourceEntity)

    @Query("SELECT * FROM data_sources ORDER BY displayName ASC")
    fun getAllSources(): Flow<List<DataSourceEntity>>

    @Query("DELETE FROM data_sources")
    suspend fun deleteAll()
}
