package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/strong_names.json. */
class StrongNamesTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/strong_names.json").readText()).jsonObject
    }

    private fun JsonObject.text(key: String) = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    @Test
    fun aliasesMatchSharedVectors() {
        file["aliases"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            assertEquals(o.text("library"), libraryName(o.text("name")!!))
        }
    }

    @Test
    fun categoriesMatchSharedVectors() {
        file["categories"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            assertEquals(o.text("name"), o.text("category"), categoryFromName(o.text("name")!!))
        }
    }

    @Test
    fun bodyPartsMatchSharedVectors() {
        val bodyParts = file["bodyParts"]!!.jsonObject
        val library = bodyParts["library"]!!.jsonArray.map { pair ->
            pair.jsonArray[0].jsonPrimitive.content to pair.jsonArray[1].jsonPrimitive.content
        }
        bodyParts["cases"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            assertEquals(o.text("name"), o.text("bodyPart"), guessBodyPart(o.text("name")!!, library))
        }
    }
}
