package com.gymbuddy.app.data.measurement

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    /** Live measurements, newest first. */
    @Query("SELECT * FROM measurements WHERE deletedAt IS NULL ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun byId(id: String): MeasurementEntity?

    @Upsert
    suspend fun upsert(measurement: MeasurementEntity)
}
