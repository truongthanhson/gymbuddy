package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStatsTest {

    private fun vectors(name: String): JsonObject {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        return Json.parseToJsonElement(File(dir, "test-vectors/$name").readText()).jsonObject
    }

    @Test
    fun volumeAndBestSetMatchSharedVectors() {
        vectors("workout_stats.json")["cases"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            val name = c["name"]!!.jsonPrimitive.content
            val sets = c["sets"]!!.jsonArray.map {
                val s = it.jsonObject
                SetValues(
                    weightKg = s["weightKg"]!!.jsonPrimitive.doubleOrNull,
                    reps = s["reps"]!!.jsonPrimitive.intOrNull,
                    completed = s["completed"]!!.jsonPrimitive.boolean,
                )
            }
            assertEquals(name, c["volumeKg"]!!.jsonPrimitive.double, volumeKg(sets), 0.01)
            val expectedBest = c["bestSetIndex"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.int }
            assertEquals(name, expectedBest, bestSetIndex(sets))
        }
    }

    @Test
    fun elapsedFormatMatchesSharedVectors() {
        vectors("elapsed.json")["cases"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            assertEquals(c["text"]!!.jsonPrimitive.content, formatElapsed(c["seconds"]!!.jsonPrimitive.long))
        }
    }

    @Test
    fun weightFormatDropsTrailingZeros() {
        assertEquals("100", formatWeight(100.0))
        assertEquals("102.5", formatWeight(102.5))
        assertEquals("2.25", formatWeight(2.25))
    }
}
