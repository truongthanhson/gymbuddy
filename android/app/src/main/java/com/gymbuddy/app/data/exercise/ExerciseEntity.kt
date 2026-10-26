package com.gymbuddy.app.data.exercise

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A built-in (seeded from shared/exercises.json) or custom exercise.
 * Built-in ids are stable slugs; custom ids are UUID strings.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameVi: String?,
    val category: String,
    val bodyPart: String,
    val primaryMuscle: String?,
    val secondaryMuscles: List<String>,
    val defaultRestSeconds: Int?,
    val isCustom: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
