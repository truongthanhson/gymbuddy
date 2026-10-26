package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs shared/test-vectors/calendar.json. */
class CalendarTest {

    @Test
    fun monthGridMatchesSharedVectors() {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        val file = Json.parseToJsonElement(File(dir, "test-vectors/calendar.json").readText()).jsonObject
        for (c in file["monthGrid"]!!.jsonArray.map { it.jsonObject }) {
            fun int(key: String) = c[key]!!.jsonPrimitive.int
            val grid = monthGrid(int("year"), int("month"), int("firstDayOfWeek"))
            assertEquals(c.toString(), int("leading"), grid.leading)
            assertEquals(c.toString(), int("days"), grid.days)
            assertEquals(c.toString(), int("weeks"), grid.weeks)
        }
    }
}
