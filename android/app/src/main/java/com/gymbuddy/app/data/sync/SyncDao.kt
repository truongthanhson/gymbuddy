package com.gymbuddy.app.data.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateEntity
import com.gymbuddy.app.data.template.TemplateExerciseEntity
import com.gymbuddy.app.data.template.TemplateSetEntity
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutSetEntity

/** Id and last change of a local row, to decide whether a pulled row is newer. */
data class RowVersion(val id: String, val updatedAt: Long)

/** Room access for sync: what changed locally, and writing pulled rows. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM exercises WHERE isCustom = 1 AND updatedAt >= :since")
    suspend fun changedCustomExercises(since: Long): List<ExerciseEntity>

    @Query("SELECT id, updatedAt FROM exercises WHERE id IN (:ids)")
    suspend fun versionsOfCustomExercises(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertCustomExercises(rows: List<ExerciseEntity>)

    @Query("SELECT * FROM folders WHERE updatedAt >= :since")
    suspend fun changedFolders(since: Long): List<FolderEntity>

    @Query("SELECT id, updatedAt FROM folders WHERE id IN (:ids)")
    suspend fun versionsOfFolders(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertFolders(rows: List<FolderEntity>)

    @Query("SELECT * FROM templates WHERE updatedAt >= :since")
    suspend fun changedTemplates(since: Long): List<TemplateEntity>

    @Query("SELECT id, updatedAt FROM templates WHERE id IN (:ids)")
    suspend fun versionsOfTemplates(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertTemplates(rows: List<TemplateEntity>)

    @Query("SELECT * FROM template_exercises WHERE updatedAt >= :since")
    suspend fun changedTemplateExercises(since: Long): List<TemplateExerciseEntity>

    @Query("SELECT id, updatedAt FROM template_exercises WHERE id IN (:ids)")
    suspend fun versionsOfTemplateExercises(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertTemplateExercises(rows: List<TemplateExerciseEntity>)

    @Query("SELECT * FROM template_sets WHERE updatedAt >= :since")
    suspend fun changedTemplateSets(since: Long): List<TemplateSetEntity>

    @Query("SELECT id, updatedAt FROM template_sets WHERE id IN (:ids)")
    suspend fun versionsOfTemplateSets(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertTemplateSets(rows: List<TemplateSetEntity>)

    @Query("SELECT * FROM workouts WHERE updatedAt >= :since")
    suspend fun changedWorkouts(since: Long): List<WorkoutEntity>

    @Query("SELECT id, updatedAt FROM workouts WHERE id IN (:ids)")
    suspend fun versionsOfWorkouts(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertWorkouts(rows: List<WorkoutEntity>)

    @Query("SELECT * FROM workout_exercises WHERE updatedAt >= :since")
    suspend fun changedWorkoutExercises(since: Long): List<WorkoutExerciseEntity>

    @Query("SELECT id, updatedAt FROM workout_exercises WHERE id IN (:ids)")
    suspend fun versionsOfWorkoutExercises(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertWorkoutExercises(rows: List<WorkoutExerciseEntity>)

    @Query("SELECT * FROM workout_sets WHERE updatedAt >= :since")
    suspend fun changedWorkoutSets(since: Long): List<WorkoutSetEntity>

    @Query("SELECT id, updatedAt FROM workout_sets WHERE id IN (:ids)")
    suspend fun versionsOfWorkoutSets(ids: List<String>): List<RowVersion>

    @Upsert
    suspend fun upsertWorkoutSets(rows: List<WorkoutSetEntity>)

    // Signing out removes the account's data from this device; built-in exercises stay.
    @Query("DELETE FROM workout_sets")
    suspend fun deleteWorkoutSets()

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteWorkoutExercises()

    @Query("DELETE FROM workouts")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM template_sets")
    suspend fun deleteTemplateSets()

    @Query("DELETE FROM template_exercises")
    suspend fun deleteTemplateExercises()

    @Query("DELETE FROM templates")
    suspend fun deleteTemplates()

    @Query("DELETE FROM folders")
    suspend fun deleteFolders()

    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteCustomExercises()
}
