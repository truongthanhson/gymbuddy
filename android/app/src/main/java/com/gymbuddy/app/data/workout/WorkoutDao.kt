package com.gymbuddy.app.data.workout

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A completed set with its workout, for an exercise's history. */
data class ExerciseHistoryRow(
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val weightKg: Double?,
    val reps: Int?,
)

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutWithExercises?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<WorkoutWithExercises>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithExercises(id: String): WorkoutWithExercises?

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getWorkoutExercise(id: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: String): WorkoutSetEntity?

    @Query("SELECT id FROM workout_exercises WHERE workoutId = :workoutId AND deletedAt IS NULL")
    suspend fun workoutExerciseIds(workoutId: String): List<String>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxExerciseOrder(workoutId: String): Int

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId AND deletedAt IS NULL ORDER BY sortOrder")
    suspend fun getSets(workoutExerciseId: String): List<WorkoutSetEntity>

    /** Sets of [exerciseId] from the most recent finished workout that included it, for the "Previous" column. */
    @Query(
        """
        SELECT s.* FROM workout_sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        WHERE s.deletedAt IS NULL AND s.completed = 1 AND we.id = (
            SELECT we2.id FROM workout_exercises we2
            JOIN workouts w2 ON we2.workoutId = w2.id
            WHERE we2.exerciseId = :exerciseId AND we2.deletedAt IS NULL
              AND w2.endedAt IS NOT NULL AND w2.deletedAt IS NULL
            ORDER BY w2.startedAt DESC LIMIT 1
        )
        ORDER BY s.sortOrder
        """
    )
    fun observePreviousSets(exerciseId: String): Flow<List<WorkoutSetEntity>>

    /** Every completed set of an exercise in finished workouts, newest workout first. */
    @Query(
        """
        SELECT w.id AS workoutId, w.name AS workoutName, w.startedAt AS startedAt,
               s.weightKg AS weightKg, s.reps AS reps
        FROM workout_sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND s.completed = 1
          AND s.deletedAt IS NULL AND we.deletedAt IS NULL
          AND w.endedAt IS NOT NULL AND w.deletedAt IS NULL
        ORDER BY w.startedAt DESC, we.sortOrder, s.sortOrder
        """
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>>

    @Upsert suspend fun upsertWorkout(workout: WorkoutEntity)
    @Upsert suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Upsert suspend fun upsertWorkoutExercise(item: WorkoutExerciseEntity)
    @Upsert suspend fun upsertSets(sets: List<WorkoutSetEntity>)
    @Upsert suspend fun upsertSet(set: WorkoutSetEntity)
}
