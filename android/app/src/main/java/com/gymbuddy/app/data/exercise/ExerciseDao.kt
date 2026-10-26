package com.gymbuddy.app.data.exercise

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query(
        """
        SELECT * FROM exercises
        WHERE deletedAt IS NULL
          AND (:bodyPart IS NULL OR bodyPart = :bodyPart)
          AND (:query = '' OR name LIKE '%' || :query || '%' OR nameVi LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observe(query: String, bodyPart: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    suspend fun countBuiltIn(): Int

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)
}
