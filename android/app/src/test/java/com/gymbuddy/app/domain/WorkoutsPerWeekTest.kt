package com.gymbuddy.app.domain

import java.io.File
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/workouts_per_week.json. */
class WorkoutsPerWeekTest {

    @Test
    fun workoutsPerWeekMatchesSharedVectors() {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        val file = Json.parseToJsonElement(File(dir, "test-vectors/workouts_per_week.json").readText()).jsonObject
        for (c in file["cases"]!!.jsonArray.map { it.jsonObject }) {
            val result = workoutsPerWeek(
                startedAts = c["startedAts"]!!.jsonArray.map { it.jsonPrimitive.long },
                nowMillis = c["now"]!!.jsonPrimitive.long,
                zone = ZoneOffset.UTC,
                weeks = c["weeks"]!!.jsonPrimitive.int,
            )
            assertEquals(c["name"]!!.jsonPrimitive.content, c["counts"]!!.jsonArray.map { it.jsonPrimitive.int }, result)
        }
    }
}
