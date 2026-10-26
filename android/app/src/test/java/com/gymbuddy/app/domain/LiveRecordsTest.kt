package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/live_records.json. */
class LiveRecordsTest {

    private fun sets(array: JsonArray) = array.map { it.jsonObject }.map { s ->
        SetValues(
            weightKg = s["weightKg"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.double,
            reps = s["reps"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int,
            completed = s["completed"]!!.jsonPrimitive.boolean,
        )
    }

    private val kinds = mapOf(
        "oneRepMax" to RecordKind.ONE_REP_MAX,
        "weight" to RecordKind.WEIGHT,
        "volume" to RecordKind.VOLUME,
        "reps" to RecordKind.REPS,
    )

    @Test
    fun liveRecordsMatchSharedVectors() {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        val file = Json.parseToJsonElement(File(dir, "test-vectors/live_records.json").readText()).jsonObject
        for (c in file["cases"]!!.jsonArray.map { it.jsonObject }) {
            val expected = c["badges"]!!.jsonArray.map { set -> set.jsonArray.map { kinds.getValue(it.jsonPrimitive.content) }.toSet() }
            val result = liveRecords(sets(c["previous"]!!.jsonArray), sets(c["current"]!!.jsonArray))
            assertEquals(c["name"]!!.jsonPrimitive.content, expected, result)
        }
    }
}
