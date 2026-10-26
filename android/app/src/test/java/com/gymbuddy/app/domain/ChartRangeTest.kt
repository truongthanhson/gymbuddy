package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/chart_range.json. */
class ChartRangeTest {
    private val file: JsonObject by lazy {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/chart_range.json").readText()).jsonObject
    }

    private fun JsonObject.days(): List<Long> = this["days"]!!.jsonArray.map { it.jsonPrimitive.long * 24 * 60 * 60 * 1000 }

    @Test
    fun filterMatchesSharedVectors() {
        for (c in file["filter"]!!.jsonArray.map { it.jsonObject }) {
            val times = c.days()
            val range = ChartRange.entries.first { it.code == c["range"]!!.jsonPrimitive.content }
            val kept = c["kept"]!!.jsonArray.map { times[it.jsonPrimitive.int] }
            assertEquals(c.toString(), kept, range.filter(times) { it })
        }
    }

    @Test
    fun availableRangesMatchSharedVectors() {
        for (c in file["available"]!!.jsonArray.map { it.jsonObject }) {
            val expected = c["ranges"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(c.toString(), expected, ChartRange.available(c.days()) { it }.map { it.code })
        }
    }

    @Test
    fun nearestIndexMatchesSharedVectors() {
        for (c in file["nearestIndex"]!!.jsonArray.map { it.jsonObject }) {
            val xs = c["xs"]!!.jsonArray.map { it.jsonPrimitive.float }
            assertEquals(c.toString(), c["index"]!!.jsonPrimitive.int, nearestIndex(xs, c["x"]!!.jsonPrimitive.float))
        }
    }
}
