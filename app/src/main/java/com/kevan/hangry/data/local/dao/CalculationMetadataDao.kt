package com.kevan.hangry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevan.hangry.data.local.entity.CalculationMetadataEntity

@Dao
interface CalculationMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: CalculationMetadataEntity)

    @Query("SELECT * FROM calculation_metadata WHERE calculationType = :type LIMIT 1")
    suspend fun getForType(type: String): CalculationMetadataEntity?

    @Query("DELETE FROM calculation_metadata")
    suspend fun deleteAll()
}
