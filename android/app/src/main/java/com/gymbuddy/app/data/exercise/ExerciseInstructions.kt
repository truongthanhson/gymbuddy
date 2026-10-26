package com.gymbuddy.app.data.exercise

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How to do a built-in exercise, step by step, in English and Vietnamese. */
data class Instructions(val steps: List<String>, val stepsVi: List<String>) {
    fun localized(vietnamese: Boolean): List<String> = if (vietnamese && stepsVi.isNotEmpty()) stepsVi else steps
}

/**
 * Instructions come straight from assets/exercises.json rather than the database: they never change
 * per user and don't need to sync.
 */
@Singleton
class ExerciseInstructions @Inject constructor(@ApplicationContext private val context: Context) {

    @Serializable
    private data class File(val exercises: List<Entry>)

    @Serializable
    private data class Entry(val id: String, val instructions: List<String> = emptyList(), val instructionsVi: List<String> = emptyList())

    private val byId: Map<String, Instructions> by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val file = context.assets.open("exercises.json").bufferedReader().use { json.decodeFromString<File>(it.readText()) }
        file.exercises.filter { it.instructions.isNotEmpty() }.associate { it.id to Instructions(it.instructions, it.instructionsVi) }
    }

    /** Null for custom exercises. Reads the file on first use, so call it off the main thread. */
    fun forExercise(exerciseId: String): Instructions? = byId[exerciseId]
}
