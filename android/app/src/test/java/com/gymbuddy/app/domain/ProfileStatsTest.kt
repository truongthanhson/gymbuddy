package com.gymbuddy.app.domain

import java.io.File
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs shared/test-vectors/profile_stats.json. */
class ProfileStatsTest {

    private fun JsonObject.long(key: String) = this[key]!!.jsonPrimitive.long
    private fun JsonObject.int(key: String) = this[key]!!.jsonPrimitive.int
    private fun JsonObject.double(key: String) = this[key]!!.jsonPrimitive.double
    private fun JsonObject.doubleOrNull(key: String) = this[key]!!.jsonPrimitive.doubleOrNull

    private fun assertClose(message: String, expected: Double?, actual: Double?) {
        if (expected == null) assertNull(message, actual) else assertEquals(message, expected, actual!!, 0.01)
    }

    @Test
    fun profileStatsMatchSharedVectors() {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        val file = Json.parseToJsonElement(File(dir, "test-vectors/profile_stats.json").readText()).jsonObject
        for (c in file["cases"]!!.jsonArray.map { it.jsonObject }) {
            val name = c["name"]!!.jsonPrimitive.content
            val now = c.long("now")
            val zone = ZoneOffset.UTC
            val workouts = c["workouts"]!!.jsonArray.map { it.jsonObject }.map { w ->
                StatsWorkout(
                    startedAt = w.long("startedAt"),
                    endedAt = w.long("endedAt"),
                    exercises = w["exercises"]!!.jsonArray.map { it.jsonObject }.map { e ->
                        StatsExercise(
                            e["exerciseId"]!!.jsonPrimitive.content,
                            e["sets"]!!.jsonArray.map { it.jsonObject }.map { s ->
                                StatsSet(
                                    weightKg = s.doubleOrNull("weightKg"),
                                    reps = s["reps"]!!.jsonPrimitive.intOrNull,
                                    completed = s["completed"]!!.jsonPrimitive.boolean,
                                    warmup = s["warmup"]!!.jsonPrimitive.boolean,
                                )
                            },
                        )
                    },
                )
            }
            val starts = workouts.map { it.startedAt }
            val expected = c["expected"]!!.jsonObject

            val totals = expected["totals"]!!.jsonObject
            val actualTotals = profileTotals(workouts)
            assertEquals(name, totals.int("workouts"), actualTotals.workouts)
            assertEquals(name, totals.long("durationSeconds"), actualTotals.durationSeconds)
            assertClose(name, totals.double("volumeKg"), actualTotals.volumeKg)
            assertEquals(name, totals.int("sets"), actualTotals.sets)

            val streaks = expected["streaks"]!!.jsonObject
            assertEquals(name, WeekStreaks(streaks.int("current"), streaks.int("best")), weekStreaks(starts, now, zone))

            assertEquals(
                name,
                expected["heatmap"]!!.jsonArray.map { it.jsonPrimitive.int },
                workoutsPerDay(starts, now, zone, c.int("heatmapWeeks")),
            )
            val volumes = weeklyVolume(workouts, now, zone, c.int("volumeWeeks"))
            val expectedVolumes = expected["weeklyVolume"]!!.jsonArray.map { it.jsonPrimitive.double }
            assertEquals(name, expectedVolumes.size, volumes.size)
            expectedVolumes.indices.forEach { assertClose(name, expectedVolumes[it], volumes[it]) }

            val habits = expected["habits"]!!.jsonObject
            val actualHabits = trainingHabits(workouts, now, zone)
            assertEquals(name, habits.long("averageDurationSeconds"), actualHabits.averageDurationSeconds)
            assertEquals(name, habits["favoriteWeekday"]!!.jsonPrimitive.intOrNull, actualHabits.favoriteWeekday)
            assertClose(name, habits.doubleOrNull("averageRestDays"), actualHabits.averageRestDays)

            val records = expected["records"]!!.jsonArray.map { it.jsonObject }
            val actualRecords = personalRecords(workouts)
            assertEquals(name, records.size, actualRecords.size)
            records.zip(actualRecords).forEach { (e, a) ->
                assertEquals(name, e["exerciseId"]!!.jsonPrimitive.content, a.exerciseId)
                assertEquals(name, e.long("startedAt"), a.startedAt)
                assertClose(name, e.double("weightKg"), a.weightKg)
                assertEquals(name, e.int("reps"), a.reps)
                assertClose(name, e.double("estimatedOneRepMax"), a.estimatedOneRepMax)
            }

            val top = expected["top"]!!.jsonArray.map { it.jsonObject }
            val actualTop = topExercises(workouts, now, c.int("topLimit"))
            assertEquals(name, top.size, actualTop.size)
            top.zip(actualTop).forEach { (e, a) ->
                assertEquals(name, e["exerciseId"]!!.jsonPrimitive.content, a.exerciseId)
                assertEquals(name, e.int("workouts"), a.workouts)
                assertClose(name, e.doubleOrNull("recentOneRepMax"), a.recentOneRepMax)
                assertClose(name, e.doubleOrNull("previousOneRepMax"), a.previousOneRepMax)
            }
        }
    }
}
