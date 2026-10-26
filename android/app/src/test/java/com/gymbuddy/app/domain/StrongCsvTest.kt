package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Runs shared/test-vectors/csv.json. */
class StrongCsvTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/csv.json").readText()).jsonObject
    }

    private fun JsonObject.string(key: String) = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    private fun JsonObject.double(key: String) = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.int(key: String) = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
    private fun unit(key: String) = WeightUnit.entries.first { it.symbol == key }

    private fun workouts(element: JsonElement) = element.jsonArray.map { w ->
        val o = w.jsonObject
        CsvWorkout(
            date = o.string("date")!!,
            name = o.string("name")!!,
            durationSec = o.int("durationSec"),
            note = o.string("note"),
            exercises = o["exercises"]!!.jsonArray.map { e ->
                val eo = e.jsonObject
                CsvExercise(
                    name = eo.string("name")!!,
                    note = eo.string("note"),
                    sets = eo["sets"]!!.jsonArray.map { s ->
                        val so = s.jsonObject
                        CsvSet(so.string("setType")!!, so.double("weightKg"), so.int("reps"), so.double("distanceM"), so.int("seconds"), so.double("rpe"))
                    },
                )
            },
        )
    }

    private fun assertSameWorkouts(label: String, expected: List<CsvWorkout>, actual: List<CsvWorkout>) {
        // Weights are compared with a tolerance; everything else exactly.
        fun normalized(list: List<CsvWorkout>) = list.map { w ->
            w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(weightKg = null) }) })
        }
        assertEquals(label, normalized(expected), normalized(actual))
        val expectedWeights = expected.flatMap { w -> w.exercises.flatMap { e -> e.sets.map { it.weightKg } } }
        val actualWeights = actual.flatMap { w -> w.exercises.flatMap { e -> e.sets.map { it.weightKg } } }
        expectedWeights.zip(actualWeights).forEach { (e, a) ->
            if (e == null) assertEquals(label, null, a) else assertEquals(label, e, a!!, 0.01)
        }
    }

    @Test
    fun fieldsMatchSharedVectors() {
        file["fields"]!!.jsonArray.forEach { c ->
            val fields = c.jsonObject["fields"]!!.jsonArray.map { it.jsonPrimitive.content }
            val text = c.jsonObject.string("text")!!
            assertEquals(text, csvLine(fields))
            assertEquals(listOf(fields), parseCsv(text))
        }
    }

    @Test
    fun durationsMatchSharedVectors() {
        val durations = file["durations"]!!.jsonObject
        durations["format"]!!.jsonArray.forEach { c ->
            assertEquals(c.jsonObject.string("text"), formatCsvDuration(c.jsonObject["seconds"]!!.jsonPrimitive.int))
        }
        durations["parse"]!!.jsonArray.forEach { c ->
            val text = c.jsonObject.string("text")!!
            assertEquals(text, c.jsonObject.int("seconds"), parseCsvDuration(text))
        }
    }

    @Test
    fun importMatchesSharedVectors() {
        file["import"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val actual = importCsv(o.string("text")!!, unit(o.string("defaultUnit")!!))
            assertNotNull(o.string("name"), actual)
            assertSameWorkouts(o.string("name")!!, workouts(o["workouts"]!!), actual!!)
        }
    }

    @Test
    fun exportMatchesSharedVectorsAndReimports() {
        file["export"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val workouts = workouts(o["workouts"]!!)
            val unit = unit(o.string("unit")!!)
            val text = exportCsv(workouts, unit)
            assertEquals(o.string("name"), o.string("text"), text)
            assertSameWorkouts(o.string("name")!!, workouts, importCsv(text, WeightUnit.KG)!!)
        }
    }

    @Test
    fun textWithoutTheColumnsIsRejected() {
        assertEquals(null, importCsv("a,b\n1,2\n", WeightUnit.KG))
    }
}
