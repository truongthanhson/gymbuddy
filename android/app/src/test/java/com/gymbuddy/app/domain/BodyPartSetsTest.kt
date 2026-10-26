package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/body_part_sets.json. */
class BodyPartSetsTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/body_part_sets.json").readText()).jsonObject
    }

    @Test
    fun orderMatchesBodyParts() {
        assertEquals(file["order"]!!.jsonArray.map { it.jsonPrimitive.content }, BodyPart.entries.map { it.key })
    }

    @Test
    fun countsMatchSharedVectors() {
        for (c in file["cases"]!!.jsonArray.map { it.jsonObject }) {
            val sets = c["sets"]!!.jsonArray.map { it.jsonObject }.map {
                BodyPartSet(it["bodyPart"]!!.jsonPrimitive.content, it["startedAt"]!!.jsonPrimitive.long, it["warmup"]!!.jsonPrimitive.boolean)
            }
            val expected = c["expected"]!!.jsonArray.map { it.jsonObject }.map {
                BodyPart.fromKey(it["bodyPart"]!!.jsonPrimitive.content) to it["sets"]!!.jsonPrimitive.int
            }
            assertEquals(expected, setsPerBodyPart(sets, c["since"]!!.jsonPrimitive.long))
        }
    }
}
