package com.gymbuddy.app.data.sync

import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateEntity
import com.gymbuddy.app.data.template.TemplateExerciseEntity
import com.gymbuddy.app.data.template.TemplateSetEntity
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.domain.formatTimestamp
import com.gymbuddy.app.domain.parseTimestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Rows as the Supabase tables name them (supabase/migrations). Timestamps travel as text.

/** Every synced row has these. */
interface SyncedRow {
    val id: String
    val updatedAt: String
}

private fun Long.ts() = formatTimestamp(this)
private fun Long?.ts() = this?.let(::formatTimestamp)
private fun String.ms() = parseTimestamp(this)
private fun String?.ms() = this?.let(::parseTimestamp)

private fun SetType.key() = name.lowercase()
private fun setTypeOf(key: String) = SetType.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SetType.NORMAL

@Serializable
data class CustomExerciseRow(
    override val id: String,
    val name: String,
    val category: String,
    @SerialName("body_part") val bodyPart: String,
    @SerialName("primary_muscle") val primaryMuscle: String? = null,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    @SerialName("default_rest_seconds") val defaultRestSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun ExerciseEntity.toRow() = CustomExerciseRow(
    id, name, category, bodyPart, primaryMuscle, secondaryMuscles, defaultRestSeconds,
    createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun CustomExerciseRow.toEntity() = ExerciseEntity(
    id = id, name = name, nameVi = null, category = category, bodyPart = bodyPart,
    primaryMuscle = primaryMuscle, secondaryMuscles = secondaryMuscles, defaultRestSeconds = defaultRestSeconds,
    isCustom = true, createdAt = createdAt.ms(), updatedAt = updatedAt.ms(), deletedAt = deletedAt.ms(),
)

@Serializable
data class FolderRow(
    override val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun FolderEntity.toRow() = FolderRow(id, name, sortOrder, createdAt.ts(), updatedAt.ts(), deletedAt.ts())

fun FolderRow.toEntity() = FolderEntity(id, name, sortOrder, createdAt.ms(), updatedAt.ms(), deletedAt.ms())

@Serializable
data class TemplateRow(
    override val id: String,
    @SerialName("folder_id") val folderId: String? = null,
    val name: String,
    val note: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun TemplateEntity.toRow() = TemplateRow(
    id, folderId, name, note, sortOrder, lastUsedAt.ts(), createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun TemplateRow.toEntity() = TemplateEntity(
    id, folderId, name, note, sortOrder, lastUsedAt.ms(), createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)

@Serializable
data class TemplateExerciseRow(
    override val id: String,
    @SerialName("template_id") val templateId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("rest_seconds") val restSeconds: Int? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun TemplateExerciseEntity.toRow() = TemplateExerciseRow(
    id, templateId, exerciseId, sortOrder, restSeconds, note, createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun TemplateExerciseRow.toEntity() = TemplateExerciseEntity(
    id, templateId, exerciseId, sortOrder, restSeconds, note, createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)

@Serializable
data class TemplateSetRow(
    override val id: String,
    @SerialName("template_exercise_id") val templateExerciseId: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("set_type") val setType: String,
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    @SerialName("target_reps") val targetReps: Int? = null,
    @SerialName("target_duration_sec") val targetDurationSec: Int? = null,
    @SerialName("target_distance_m") val targetDistanceM: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun TemplateSetEntity.toRow() = TemplateSetRow(
    id, templateExerciseId, sortOrder, setType.key(), targetWeightKg, targetReps, targetDurationSec, targetDistanceM,
    createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun TemplateSetRow.toEntity() = TemplateSetEntity(
    id, templateExerciseId, sortOrder, setTypeOf(setType), targetWeightKg, targetReps, targetDurationSec, targetDistanceM,
    createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)

@Serializable
data class WorkoutRow(
    override val id: String,
    @SerialName("template_id") val templateId: String? = null,
    val name: String,
    val note: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun WorkoutEntity.toRow() = WorkoutRow(
    id, templateId, name, note, startedAt.ts(), endedAt.ts(), createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun WorkoutRow.toEntity() = WorkoutEntity(
    id = id, name = name, note = note, templateId = templateId, startedAt = startedAt.ms(), endedAt = endedAt.ms(),
    createdAt = createdAt.ms(), updatedAt = updatedAt.ms(), deletedAt = deletedAt.ms(),
)

@Serializable
data class WorkoutExerciseRow(
    override val id: String,
    @SerialName("workout_id") val workoutId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("rest_seconds") val restSeconds: Int? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun WorkoutExerciseEntity.toRow() = WorkoutExerciseRow(
    id, workoutId, exerciseId, sortOrder, restSeconds, note, createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun WorkoutExerciseRow.toEntity() = WorkoutExerciseEntity(
    id, workoutId, exerciseId, sortOrder, restSeconds, note, createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)

@Serializable
data class WorkoutSetRow(
    override val id: String,
    @SerialName("workout_exercise_id") val workoutExerciseId: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("set_type") val setType: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val reps: Int? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    val rpe: Double? = null,
    val completed: Boolean,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun WorkoutSetEntity.toRow() = WorkoutSetRow(
    id, workoutExerciseId, sortOrder, setType.key(), weightKg, reps, durationSec, distanceM, rpe, completed,
    completedAt.ts(), createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun WorkoutSetRow.toEntity() = WorkoutSetEntity(
    id, workoutExerciseId, sortOrder, setTypeOf(setType), weightKg, reps, durationSec, distanceM, rpe, completed,
    completedAt.ms(), createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)

@Serializable
data class MeasurementRow(
    override val id: String,
    val kind: String,
    val value: Double,
    val unit: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") override val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) : SyncedRow

fun MeasurementEntity.toRow() = MeasurementRow(
    id, kind, value, unit, measuredAt.ts(), createdAt.ts(), updatedAt.ts(), deletedAt.ts(),
)

fun MeasurementRow.toEntity() = MeasurementEntity(
    id, kind, value, unit, measuredAt.ms(), createdAt.ms(), updatedAt.ms(), deletedAt.ms(),
)
