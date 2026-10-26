package com.gymbuddy.app.data.workout

import androidx.room.withTransaction
import com.gymbuddy.app.data.GymBuddyDatabase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** All writes to workouts go through here so timestamps and soft deletes stay consistent for sync. */
@Singleton
class WorkoutRepository @Inject constructor(private val db: GymBuddyDatabase) {
    private val dao = db.workoutDao()

    fun observeActive(): Flow<WorkoutWithExercises?> = dao.observeActive()
    fun observeHistory(): Flow<List<WorkoutWithExercises>> = dao.observeHistory()
    fun observePreviousSets(exerciseId: String): Flow<List<WorkoutSetEntity>> = dao.observePreviousSets(exerciseId)

    /** Starts a new workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(name: String): String = db.withTransaction {
        dao.getActive()?.let { return@withTransaction it.id }
        val now = clock()
        val id = newId()
        dao.upsertWorkout(WorkoutEntity(id = id, name = name, startedAt = now, createdAt = now, updatedAt = now))
        id
    }

    suspend fun addExercises(workoutId: String, exerciseIds: List<String>) = db.withTransaction {
        val now = clock()
        var order = dao.maxExerciseOrder(workoutId)
        exerciseIds.forEach { exerciseId ->
            val workoutExerciseId = newId()
            dao.upsertWorkoutExercise(
                WorkoutExerciseEntity(
                    id = workoutExerciseId,
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    sortOrder = ++order,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.upsertSet(WorkoutSetEntity(id = newId(), workoutExerciseId = workoutExerciseId, sortOrder = 0, createdAt = now, updatedAt = now))
        }
        touchWorkout(workoutId, now)
    }

    /** Adds a set that starts with the values of the last set, like Strong does. */
    suspend fun addSet(workoutExerciseId: String) = db.withTransaction {
        val now = clock()
        val last = dao.getSets(workoutExerciseId).lastOrNull()
        dao.upsertSet(
            WorkoutSetEntity(
                id = newId(),
                workoutExerciseId = workoutExerciseId,
                sortOrder = (last?.sortOrder ?: -1) + 1,
                weightKg = last?.weightKg,
                reps = last?.reps,
                durationSec = last?.durationSec,
                distanceM = last?.distanceM,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateSet(setId: String, transform: (WorkoutSetEntity) -> WorkoutSetEntity) = db.withTransaction {
        val set = dao.getSet(setId) ?: return@withTransaction
        dao.upsertSet(transform(set).copy(updatedAt = clock()))
    }

    suspend fun toggleSetCompleted(setId: String) = updateSet(setId) {
        val completed = !it.completed
        it.copy(completed = completed, completedAt = if (completed) clock() else null)
    }

    suspend fun deleteSet(setId: String) = updateSet(setId) { it.copy(deletedAt = clock()) }

    suspend fun removeExercise(workoutExerciseId: String) = db.withTransaction {
        val item = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        val now = clock()
        dao.upsertWorkoutExercise(item.copy(deletedAt = now, updatedAt = now))
    }

    /** Rest time after each set of this exercise in this workout; 0 turns the timer off. */
    suspend fun setRestSeconds(workoutExerciseId: String, seconds: Int) = db.withTransaction {
        val item = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        dao.upsertWorkoutExercise(item.copy(restSeconds = seconds, updatedAt = clock()))
    }

    suspend fun renameWorkout(workoutId: String, name: String) = updateWorkout(workoutId) { it.copy(name = name) }

    /** Ends the workout; sets that were never ticked are dropped. */
    suspend fun finishWorkout(workoutId: String) = db.withTransaction {
        val now = clock()
        val workout = dao.getWorkout(workoutId) ?: return@withTransaction
        cleanUpUncompletedSets(workoutId, now)
        dao.upsertWorkout(workout.copy(endedAt = now, updatedAt = now))
    }

    suspend fun discardWorkout(workoutId: String) = updateWorkout(workoutId) { it.copy(deletedAt = clock()) }

    private suspend fun cleanUpUncompletedSets(workoutId: String, now: Long) {
        dao.workoutExerciseIds(workoutId).forEach { workoutExerciseId ->
            val sets = dao.getSets(workoutExerciseId)
            val dropped = sets.filterNot { it.completed }.map { it.copy(deletedAt = now, updatedAt = now) }
            if (dropped.isNotEmpty()) dao.upsertSets(dropped)
            if (dropped.size == sets.size) {
                dao.getWorkoutExercise(workoutExerciseId)?.let {
                    dao.upsertWorkoutExercise(it.copy(deletedAt = now, updatedAt = now))
                }
            }
        }
    }

    private suspend fun updateWorkout(workoutId: String, transform: (WorkoutEntity) -> WorkoutEntity) =
        db.withTransaction {
            val workout = dao.getWorkout(workoutId) ?: return@withTransaction
            dao.upsertWorkout(transform(workout).copy(updatedAt = clock()))
        }

    private suspend fun touchWorkout(workoutId: String, now: Long) {
        dao.getWorkout(workoutId)?.let { dao.upsertWorkout(it.copy(updatedAt = now)) }
    }

    private fun clock() = System.currentTimeMillis()

    private fun newId() = UUID.randomUUID().toString()
}
