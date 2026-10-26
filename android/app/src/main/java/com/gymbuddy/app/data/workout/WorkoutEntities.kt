package com.gymbuddy.app.data.workout

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.gymbuddy.app.data.exercise.ExerciseEntity

@Entity(tableName = "workouts", indices = [Index("endedAt"), Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String? = null,
    val templateId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val restSeconds: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Neighbouring exercises with the same number form a superset. */
    val supersetGroup: Int? = null,
)

enum class SetType { WARMUP, NORMAL, DROP, FAILURE }

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val sortOrder: Int,
    val setType: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Nothing typed in yet. */
fun WorkoutSetEntity.isEmpty(): Boolean = weightKg == null && reps == null && durationSec == null && distanceM == null

/** Ticking an empty set takes the values of the same set last session, like Strong. */
fun WorkoutSetEntity.filledFrom(previous: WorkoutSetEntity): WorkoutSetEntity =
    copy(weightKg = previous.weightKg, reps = previous.reps, durationSec = previous.durationSec, distanceM = previous.distanceM)

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val allSets: List<WorkoutSetEntity>,
) {
    /** Live sets in display order (soft-deleted rows are kept for sync). */
    val sets: List<WorkoutSetEntity>
        get() = allSets.filter { it.deletedAt == null }.sortedBy { it.sortOrder }
}

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val allExercises: List<WorkoutExerciseWithSets>,
) {
    val exercises: List<WorkoutExerciseWithSets>
        get() = allExercises.filter { it.workoutExercise.deletedAt == null }
            .sortedBy { it.workoutExercise.sortOrder }
}
