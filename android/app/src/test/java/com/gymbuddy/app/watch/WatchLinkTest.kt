package com.gymbuddy.app.watch

import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutExerciseWithSets
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.watchlink.WatchState
import com.gymbuddy.watchlink.decodeWatchAction
import com.gymbuddy.watchlink.decodeWatchState
import com.gymbuddy.watchlink.encode
import com.gymbuddy.watchlink.nextSet
import com.gymbuddy.watchlink.setLabels
import com.gymbuddy.watchlink.watchJson
import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs shared/test-vectors/watch.json and checks how the phone builds the watch state. */
class WatchLinkTest {

    private val file = run {
        val dir = System.getProperty("gymbuddy.sharedDir") ?: error("gymbuddy.sharedDir not set")
        watchJson.parseToJsonElement(File(dir, "test-vectors/watch.json").readText()).jsonObject
    }

    @Test
    fun labelsMatchSharedVectors() {
        for (case in file["labels"]!!.jsonArray) {
            val o = case.jsonObject
            val types = o["types"]!!.jsonArray.map { it.jsonPrimitive.content }
            val labels = o["labels"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(labels, setLabels(types))
        }
    }

    @Test
    fun nextSetMatchesSharedVectors() {
        for (case in file["nextSet"]!!.jsonArray) {
            val o = case.jsonObject
            val state = decodeWatchState(o["state"].toString())
            val next = state.workout?.nextSet()
            val note = o["note"]!!.jsonPrimitive.content
            assertEquals(note, o["exercise"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull, next?.exerciseIndex)
            assertEquals(note, o["setId"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull, next?.set?.id)
            assertEquals(note, state, decodeWatchState(state.encode()))
        }
    }

    @Test
    fun actionsMatchSharedVectors() {
        for (case in file["actions"]!!.jsonArray) {
            val o = case.jsonObject
            val action = decodeWatchAction(o["json"]!!.jsonPrimitive.content)
            assertEquals(o["action"]!!.jsonPrimitive.content, action.action)
            assertEquals(o["setId"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull, action.setId)
        }
    }

    @Test
    fun buildsStateInTheUsersUnitWithHints() {
        val exercise = ExerciseEntity(
            id = "bench", name = "Bench Press", nameVi = "Đẩy ngực", bodyPart = "chest", category = "barbell", primaryMuscle = null, secondaryMuscles = emptyList(),
            isCustom = false, defaultRestSeconds = null, createdAt = 0, updatedAt = 0,
        )
        fun set(id: String, order: Int, type: SetType, kg: Double?, reps: Int?, done: Boolean) = WorkoutSetEntity(
            id = id, workoutExerciseId = "we", sortOrder = order, setType = type, weightKg = kg, reps = reps,
            completed = done, createdAt = 0, updatedAt = 0,
        )
        val workout = WorkoutWithExercises(
            WorkoutEntity(id = "w", name = "Push", startedAt = 1000, createdAt = 0, updatedAt = 0),
            listOf(
                WorkoutExerciseWithSets(
                    WorkoutExerciseEntity(id = "we", workoutId = "w", exerciseId = "bench", sortOrder = 0, createdAt = 0, updatedAt = 0),
                    exercise,
                    listOf(
                        set("b", 1, SetType.NORMAL, null, null, false),
                        set("a", 0, SetType.WARMUP, 20.0, 10, true),
                    ),
                ),
            ),
        )
        val previous = mapOf("bench" to listOf(set("p0", 0, SetType.WARMUP, 20.0, 10, true), set("p1", 1, SetType.NORMAL, 60.0, 8, true)))

        val state = buildWatchState(workout, previous, WeightUnit.LB, restEndsAt = 5000, vietnamese = true).workout!!

        assertEquals("Push", state.name)
        assertEquals(5000L, state.restEndsAt)
        assertEquals("Đẩy ngực", state.exercises.single().name)
        val (warmup, first) = state.exercises.single().sets
        assertEquals(listOf("a", "W", "44.09", "10", "lb", true), listOf(warmup.id, warmup.label, warmup.weight, warmup.reps, warmup.unit, warmup.done))
        assertEquals(listOf("b", "1", "", "", false), listOf(first.id, first.label, first.weight, first.reps, first.done))
        assertEquals("132.28", first.hintWeight)
        assertEquals("8", first.hintReps)
        assertNull(buildWatchState(null, emptyMap(), WeightUnit.KG, null, false).workout)
        assertEquals(WatchState(null), buildWatchState(null, emptyMap(), WeightUnit.KG, null, false))
    }
}
