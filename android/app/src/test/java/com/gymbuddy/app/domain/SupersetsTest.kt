package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/supersets.json. */
class SupersetsTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/supersets.json").readText()).jsonObject
    }

    private fun groups(array: JsonArray) = array.map { if (it is JsonNull) null else it.jsonPrimitive.int }

    @Test
    fun startsRestMatchesSharedVectors() {
        for (c in file["startsRest"]!!.jsonArray.map { it.jsonObject }) {
            val groups = groups(c["groups"]!!.jsonArray)
            val expected = c["results"]!!.jsonArray.map { it.jsonPrimitive.boolean }
            assertEquals(groups.toString(), expected, groups.indices.map { startsRest(groups, it) })
        }
    }

    @Test
    fun editsMatchSharedVectors() {
        for ((key, edit) in listOf<Pair<String, (List<Int?>, Int) -> List<Int?>>>(
            "supersetWithNext" to ::supersetWithNext,
            "removeFromSuperset" to ::removeFromSuperset,
        )) {
            for (c in file[key]!!.jsonArray.map { it.jsonObject }) {
                val result = edit(groups(c["groups"]!!.jsonArray), c["index"]!!.jsonPrimitive.int)
                assertEquals(c["note"]!!.jsonPrimitive.content, groups(c["result"]!!.jsonArray), result)
            }
        }
    }
}
