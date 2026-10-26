package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/share_text.json. */
class ShareTextTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/share_text.json").readText()).jsonObject
    }

    private fun JsonObject.value(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.unit() = WeightUnit.entries.first { it.symbol == string("unit") }

    private fun JsonObject.set() = ShareSet(
        type = value("type")?.jsonPrimitive?.content ?: "normal",
        weightKg = value("weightKg")?.jsonPrimitive?.double,
        reps = value("reps")?.jsonPrimitive?.int,
        durationSec = value("durationSec")?.jsonPrimitive?.int,
        distanceM = value("distanceM")?.jsonPrimitive?.double,
    )

    @Test
    fun setTextMatchesSharedVectors() {
        for (c in file["setText"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(c.toString(), c.string("text"), setText(c.string("category"), c.set(), c.unit()))
        }
    }

    @Test
    fun shareTextMatchesSharedVectors() {
        for (c in file["shareText"]!!.jsonArray.map { it.jsonObject }) {
            val exercises = c["exercises"]!!.jsonArray.map { it.jsonObject }.map { e ->
                ShareExercise(e.string("name"), e.string("category"), e["sets"]!!.jsonArray.map { it.jsonObject.set() })
            }
            assertEquals(c.string("text"), workoutShareText(c.string("title"), c.string("subtitle"), exercises, c.unit()))
        }
    }
}
