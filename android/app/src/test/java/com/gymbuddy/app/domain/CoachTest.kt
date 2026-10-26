package com.gymbuddy.app.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs shared/test-vectors/coach.json. */
class CoachTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        Json.parseToJsonElement(File(dir, "test-vectors/coach.json").readText()).jsonObject
    }

    private fun JsonObject.cases(key: String) = this[key]!!.jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.num(key: String) = this[key]!!.jsonPrimitive

    private fun sessions(json: JsonElement) = json.jsonArray.map { it.jsonObject }.map { s ->
        CoachSession(
            s.num("startedAt").long,
            s["sets"]!!.jsonArray.map { it.jsonObject }.map {
                CoachSet(it.num("weightKg").doubleOrNull, it.num("reps").intOrNull, it.num("rpe").doubleOrNull, it.num("warmup").boolean)
            },
        )
    }

    private fun bodyPartSets(json: JsonElement) = json.jsonArray.map { it.jsonObject }.map {
        BodyPartSet(it.str("bodyPart"), it.num("startedAt").long, it.num("warmup").boolean)
    }

    private fun insight(o: JsonObject): CoachInsight = when (o.str("type")) {
        "inactive" -> CoachInsight.Inactive(o.num("value").long)
        "deload" -> CoachInsight.Deload(o.str("exerciseId"), o.num("weightKg").double, o.num("reps").int)
        "volume_spike" -> CoachInsight.VolumeSpike(BodyPart.fromKey(o.str("bodyPart")), o.num("value").int)
        "neglected" -> CoachInsight.Neglected(BodyPart.fromKey(o.str("bodyPart")), o.num("value").long)
        "streak" -> CoachInsight.Streak(o.num("value").int)
        else -> error("unknown insight ${o.str("type")}")
    }

    @Test
    fun nextTargetMatchesSharedVectors() {
        for (c in file.cases("nextTarget")) {
            val actual = nextTarget(sessions(c["sessions"]!!), c.str("bodyPart"), c.str("category"))
            val expected = c["expected"]!!
            val name = c.str("description")
            if (expected is JsonNull) {
                assertNull(name, actual)
            } else {
                val e = expected.jsonObject
                assertEquals(name, TargetKind.valueOf(e.str("kind").uppercase()), actual!!.kind)
                assertEquals(name, e.num("weightKg").double, actual.weightKg, 0.01)
                assertEquals(name, e.num("reps").int, actual.reps)
            }
        }
    }

    @Test
    fun recoveryMatchesSharedVectors() {
        for (c in file.cases("recovery")) {
            val expected = c.cases("expected").map {
                Recovery(BodyPart.fromKey(it.str("bodyPart")), it.num("hoursSince").long, RecoveryStatus.valueOf(it.str("status").uppercase()))
            }
            assertEquals(expected, recovery(bodyPartSets(c["sets"]!!), c.num("now").long))
        }
    }

    @Test
    fun volumeMatchesSharedVectors() {
        for (c in file.cases("volume")) {
            assertEquals(c.cases("expected").map(::insight), volumeInsights(bodyPartSets(c["sets"]!!), c.num("now").long))
        }
    }

    @Test
    fun streakMatchesSharedVectors() {
        for (c in file.cases("streak")) {
            val starts = c["workoutStarts"]!!.jsonArray.map { it.jsonPrimitive.long }
            assertEquals(c.str("description"), c.num("expected").int, streakWeeks(starts, c.num("now").long, c.num("goalPerWeek").int))
        }
    }

    @Test
    fun reminderMatchesSharedVectors() {
        for (c in file.cases("reminder")) {
            val last = c["lastWorkout"]!!.jsonPrimitive.longOrNull
            val expected = c["expected"]!!.jsonPrimitive.longOrNull
            assertEquals(c.str("description"), expected, reminderDueAt(last, c.num("daysPerWeek").int))
        }
    }

    @Test
    fun insightsMatchSharedVectors() {
        for (c in file.cases("insights")) {
            val exercises = c.cases("exercises").map {
                CoachExercise(it.str("exerciseId"), it.str("bodyPart"), it.str("category"), sessions(it["sessions"]!!))
            }
            val actual = coachInsights(
                exercises,
                bodyPartSets(c["bodyPartSets"]!!),
                c["workoutStarts"]!!.jsonArray.map { it.jsonPrimitive.long },
                c.num("goalPerWeek").int,
                c.num("now").long,
            )
            assertEquals(c.str("description"), c.cases("expected").map(::insight), actual)
        }
    }
}
