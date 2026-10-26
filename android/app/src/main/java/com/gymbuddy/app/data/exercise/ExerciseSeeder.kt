package com.gymbuddy.app.data.exercise

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SeedFile(val version: Int, val exercises: List<SeedExercise>)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val nameVi: String? = null,
    val category: String,
    val bodyPart: String,
    val primaryMuscle: String? = null,
    val secondaryMuscles: List<String> = emptyList(),
    val defaultRestSeconds: Int? = null,
)

/** Loads the built-in exercise library from assets/exercises.json into Room. */
@Singleton
class ExerciseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExerciseDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        val seed = context.assets.open("exercises.json").bufferedReader().use {
            json.decodeFromString<SeedFile>(it.readText())
        }
        if (dao.countBuiltIn() >= seed.exercises.size) return
        val now = System.currentTimeMillis()
        dao.upsertAll(seed.exercises.map { it.toEntity(now) })
    }

    private fun SeedExercise.toEntity(now: Long) = ExerciseEntity(
        id = id,
        name = name,
        nameVi = nameVi,
        category = category,
        bodyPart = bodyPart,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        defaultRestSeconds = defaultRestSeconds,
        isCustom = false,
        createdAt = now,
        updatedAt = now,
    )
}
