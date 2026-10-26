package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/set_fields.json. */
class SetFieldsTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/set_fields.json").readText()).jsonObject
    }

    private fun cases(key: String) = file[key]!!.jsonArray.map { it.jsonObject }

    @Test
    fun fieldsMatchSharedVectors() {
        for (c in cases("fields")) {
            val category = c["category"]!!.jsonPrimitive.content
            val expected = c["fields"]!!.jsonArray.map { SetField.valueOf(it.jsonPrimitive.content.uppercase()) }
            assertEquals(category, expected, setFields(category))
        }
    }

    @Test
    fun durationsMatchSharedVectors() {
        for (c in cases("parseDuration")) {
            val text = c["text"]!!.jsonPrimitive.content
            val expected = c["seconds"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
            assertEquals("'$text'", expected, parseDuration(text))
        }
        for (c in cases("formatDuration")) {
            assertEquals(c["text"]!!.jsonPrimitive.content, formatDuration(c["seconds"]!!.jsonPrimitive.int))
        }
    }

    @Test
    fun distancesMatchSharedVectors() {
        for (c in cases("distance")) {
            val unit = DistanceUnit.entries.first { it.symbol == c["unit"]!!.jsonPrimitive.content }
            val meters = c["meters"]!!.jsonPrimitive.double
            assertEquals(c["display"]!!.jsonPrimitive.double, unit.display(meters), 0.001)
            assertEquals(meters, unit.toMeters(unit.display(meters)), unit.metersPerUnit * 0.01)
        }
        assertEquals(DistanceUnit.MI, WeightUnit.LB.distanceUnit)
        assertEquals(DistanceUnit.KM, WeightUnit.KG.distanceUnit)
    }
}
