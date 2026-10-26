package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/calculators.json. */
class CalculatorsTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/calculators.json").readText()).jsonObject
    }

    @Test
    fun platesMatchSharedVectors() {
        file["plates"]!!.jsonArray.forEach { element ->
            val c = element.jsonObject
            val name = c["name"]!!.jsonPrimitive.content
            fun doubles(key: String) = c[key]!!.jsonArray.map { it.jsonPrimitive.double }
            val load = plateLoad(c["target"]!!.jsonPrimitive.double, c["bar"]!!.jsonPrimitive.double, doubles("plates"))
            assertEquals(name, doubles("perSide"), load.perSide)
            assertEquals(name, c["loaded"]!!.jsonPrimitive.double, load.loaded, 0.01)
            assertEquals(name, c["remainder"]!!.jsonPrimitive.double, load.remainder, 0.01)
        }
    }

    @Test
    fun repMaxMatchesSharedVectors() {
        val repMax = file["repMax"]!!.jsonObject
        val oneRepMax = repMax["oneRepMax"]!!.jsonPrimitive.double
        repMax["rows"]!!.jsonArray.forEach { element ->
            val row = element.jsonObject
            val reps = row["reps"]!!.jsonPrimitive.int
            assertEquals("$reps reps", row["weight"]!!.jsonPrimitive.double, repMaxWeight(oneRepMax, reps), 0.01)
        }
    }

    @Test
    fun warmupMatchesSharedVectors() {
        file["warmup"]!!.jsonObject["cases"]!!.jsonArray.forEach { element ->
            val c = element.jsonObject
            val name = c["name"]!!.jsonPrimitive.content
            val sets = warmupSets(
                c["working"]!!.jsonPrimitive.double,
                c["bar"]!!.jsonPrimitive.double,
                c["plates"]!!.jsonArray.map { it.jsonPrimitive.double },
            )
            val expected = c["sets"]!!.jsonArray.map { pair ->
                val (weight, reps) = pair.jsonArray.map { it.jsonPrimitive.double }
                WarmupSet(weight, reps.toInt())
            }
            assertEquals(name, expected, sets)
        }
    }
}
