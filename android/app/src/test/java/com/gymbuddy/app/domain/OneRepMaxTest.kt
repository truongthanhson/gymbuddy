package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs the cross-platform test vectors in shared/test-vectors. */
class OneRepMaxTest {

    private fun vectors(name: String): JsonObject {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        return Json.parseToJsonElement(File(dir, "test-vectors/$name").readText()).jsonObject
    }

    @Test
    fun epleyMatchesSharedVectors() {
        vectors("one_rep_max.json")["cases"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            val weight = c["weightKg"]!!.jsonPrimitive.double
            val reps = c["reps"]!!.jsonPrimitive.int
            val expected = c["expected"]!!.jsonPrimitive.double
            assertEquals("$weight x $reps", expected, estimatedOneRepMax(weight, reps), 0.01)
        }
    }

    @Test
    fun weightConversionMatchesSharedVectors() {
        vectors("units.json")["cases"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            val kg = c["kg"]!!.jsonPrimitive.double
            val lb = c["lb"]!!.jsonPrimitive.double
            assertEquals(lb, WeightUnit.LB.fromKg(kg), 0.01)
            assertEquals(kg, WeightUnit.LB.toKg(lb), 0.01)
        }
    }

    @Test
    fun typedWeightsSurviveTheKgRoundTrip() {
        vectors("units.json")["roundTrip"]!!.jsonObject["cases"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            val unit = WeightUnit.valueOf(c["unit"]!!.jsonPrimitive.content.uppercase())
            val typed = c["typed"]!!.jsonPrimitive.double
            val kg = unit.toKg(typed)
            assertEquals("$typed $unit", typed, unit.display(kg), 1e-9)
            assertEquals(c["formatted"]!!.jsonPrimitive.content, unit.format(kg))
        }
    }
}
