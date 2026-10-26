package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks shared/exercises.json, the built-in library both apps seed from. */
class ExerciseLibraryTest {

    @Test
    fun everyExerciseIsValidAndHasInstructions() {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        val exercises = Json.parseToJsonElement(File(dir, "exercises.json").readText()).jsonObject["exercises"]!!.jsonArray
            .map { it.jsonObject }
        val ids = exercises.map { it["id"]!!.jsonPrimitive.content }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        val categories = ExerciseCategory.entries.map { it.key }.toSet()
        val bodyParts = BodyPart.entries.map { it.key }.toSet()
        for (e in exercises) {
            val id = e["id"]!!.jsonPrimitive.content
            assertTrue(id, e["category"]!!.jsonPrimitive.content in categories)
            assertTrue(id, e["bodyPart"]!!.jsonPrimitive.content in bodyParts)
            val steps = e["instructions"]?.jsonArray.orEmpty()
            assertTrue("$id has no instructions", steps.isNotEmpty())
            assertEquals("$id: Vietnamese steps", steps.size, e["instructionsVi"]?.jsonArray.orEmpty().size)
        }
    }
}
