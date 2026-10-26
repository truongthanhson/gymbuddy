package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs shared/test-vectors/exercise_records.json. */
class ExerciseRecordsTest {

    private val cases = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/exercise_records.json").readText()).jsonObject["cases"]!!.jsonArray
    }

    private fun assertRecord(name: String, expected: JsonElement?, actual: Record?) {
        if (expected == null || expected is JsonNull) {
            assertNull(name, actual)
            return
        }
        val e = expected.jsonObject
        assertEquals(name, e["value"]!!.jsonPrimitive.double, actual!!.value, 0.01)
        assertEquals(name, e["startedAt"]!!.jsonPrimitive.long, actual.startedAt)
    }

    @Test
    fun matchesSharedVectors() {
        cases.forEach { case ->
            val c = case.jsonObject
            val name = c["name"]!!.jsonPrimitive.content
            val sessions = c["sessions"]!!.jsonArray.map { s ->
                val o = s.jsonObject
                ExerciseSession(
                    startedAt = o["startedAt"]!!.jsonPrimitive.long,
                    sets = o["sets"]!!.jsonArray.map { set ->
                        val v = set.jsonObject
                        SetValues(
                            weightKg = v["weightKg"]?.jsonPrimitive?.doubleOrNull,
                            reps = v["reps"]?.jsonPrimitive?.intOrNull,
                            completed = v["completed"]!!.jsonPrimitive.boolean,
                        )
                    },
                )
            }
            val (records, points) = exerciseProgress(sessions)
            val expected = c["records"]!!.jsonObject
            assertRecord("$name e1RM", expected["estimatedOneRepMax"], records.estimatedOneRepMax)
            assertRecord("$name max weight", expected["maxWeight"], records.maxWeight)
            assertRecord("$name max set volume", expected["maxSetVolume"], records.maxSetVolume)
            assertRecord("$name max reps", expected["maxReps"], records.maxReps)
            assertRecord("$name max session volume", expected["maxSessionVolume"], records.maxSessionVolume)

            val expectedPoints = c["points"]!!.jsonArray
            assertEquals(name, expectedPoints.size, points.size)
            expectedPoints.zip(points).forEach { (e, p) ->
                val o = e.jsonObject
                assertEquals(name, o["startedAt"]!!.jsonPrimitive.long, p.startedAt)
                assertEquals(name, o["estimatedOneRepMax"]!!.jsonPrimitive.double, p.estimatedOneRepMax, 0.01)
                assertEquals(name, o["maxWeight"]!!.jsonPrimitive.double, p.maxWeight, 0.01)
                assertEquals(name, o["volumeKg"]!!.jsonPrimitive.double, p.volumeKg, 0.01)
            }
        }
    }
}
