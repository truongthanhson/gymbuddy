package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/timestamps.json: the text format sync uses for timestamps. */
class TimestampsTest {

    private val vectors = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/timestamps.json").readText()).jsonObject
    }

    @Test
    fun parsesServerTimestamps() {
        vectors["parse"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            val text = c["text"]!!.jsonPrimitive.content
            assertEquals(text, c["epochMillis"]!!.jsonPrimitive.long, parseTimestamp(text))
        }
    }

    @Test
    fun formatsUtcWithMilliseconds() {
        vectors["format"]!!.jsonArray.forEach { case ->
            val c = case.jsonObject
            assertEquals(c["text"]!!.jsonPrimitive.content, formatTimestamp(c["epochMillis"]!!.jsonPrimitive.long))
        }
    }
}
