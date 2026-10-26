package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {

    private val vectors: JsonObject by lazy {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/rest_timer.json").readText()).jsonObject
    }

    private fun JsonObject.getInt(key: String) = this[key]!!.jsonPrimitive.int
    private fun JsonObject.getIntOrNull(key: String) = this[key]!!.jsonPrimitive.intOrNull

    @Test
    fun resolveMatchesSharedVectors() {
        vectors["resolve"]!!.jsonArray.map { it.jsonObject }.forEach { c ->
            assertEquals(c.toString(), c.getInt("expected"), resolveRestSeconds(c.getIntOrNull("workoutExerciseRest"), c.getIntOrNull("exerciseDefault")))
        }
    }

    @Test
    fun remainingMatchesSharedVectors() {
        vectors["remaining"]!!.jsonArray.map { it.jsonObject }.forEach { c ->
            val state = RestTimerState.start(c.getInt("startSeconds"), nowMillis = 0)!!
            assertEquals(c.toString(), c.getInt("expected"), state.remainingSeconds(c["elapsedMillis"]!!.jsonPrimitive.long))
        }
    }

    @Test
    fun adjustMatchesSharedVectors() {
        vectors["adjust"]!!.jsonArray.map { it.jsonObject }.forEach { c ->
            val now = c["elapsedMillis"]!!.jsonPrimitive.long
            val adjusted = RestTimerState.start(c.getInt("startSeconds"), nowMillis = 0)!!.adjusted(c.getInt("deltaSeconds"), now)
            val expectedRemaining = c.getIntOrNull("expectedRemaining")
            if (expectedRemaining == null) {
                assertNull(c.toString(), adjusted)
            } else {
                assertEquals(c.toString(), expectedRemaining, adjusted!!.remainingSeconds(now))
                assertEquals(c.toString(), c.getInt("expectedTotal"), adjusted.totalSeconds)
            }
        }
    }

    @Test
    fun zeroSecondsMeansNoTimer() {
        assertNull(RestTimerState.start(0, nowMillis = 0))
    }
}
