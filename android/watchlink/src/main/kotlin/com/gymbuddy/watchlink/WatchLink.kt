package com.gymbuddy.watchlink

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data Layer paths and keys; see shared/watch-protocol.md. */
object WatchPaths {
    const val STATE = "/gymbuddy/state"
    const val ACTION = "/gymbuddy/action"
    const val JSON_KEY = "json"
}

@Serializable
data class WatchSet(
    val id: String,
    val label: String,
    val weight: String,
    val reps: String,
    val unit: String,
    val done: Boolean,
    val hintWeight: String? = null,
    val hintReps: String? = null,
)

@Serializable
data class WatchExercise(val name: String, val sets: List<WatchSet>)

@Serializable
data class WatchWorkout(
    val name: String,
    val startedAt: Long,
    val restEndsAt: Long? = null,
    val exercises: List<WatchExercise>,
)

/** What the phone shares with the watch; [workout] is null when no workout is in progress. */
@Serializable
data class WatchState(val workout: WatchWorkout? = null)

@Serializable
data class WatchAction(val action: String, val setId: String? = null) {
    companion object {
        const val COMPLETE_SET = "completeSet"
        const val SKIP_REST = "skipRest"

        fun completeSet(setId: String) = WatchAction(COMPLETE_SET, setId)
        fun skipRest() = WatchAction(SKIP_REST)
    }
}

/** Where the next set to do is: the first set not done yet, across exercises in order. */
data class NextSet(val exerciseIndex: Int, val set: WatchSet)

fun WatchWorkout.nextSet(): NextSet? {
    exercises.forEachIndexed { index, exercise ->
        exercise.sets.firstOrNull { !it.done }?.let { return NextSet(index, it) }
    }
    return null
}

/** Set labels like Strong's: W, D, F for warm-up, drop and failure sets, else the number of the normal set. */
fun setLabels(types: List<String>): List<String> {
    var normal = 0
    return types.map {
        when (it) {
            "warmup" -> "W"
            "drop" -> "D"
            "failure" -> "F"
            else -> (++normal).toString()
        }
    }
}

val watchJson = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }

fun WatchState.encode(): String = watchJson.encodeToString(WatchState.serializer(), this)
fun decodeWatchState(json: String): WatchState = watchJson.decodeFromString(WatchState.serializer(), json)
fun WatchAction.encode(): String = watchJson.encodeToString(WatchAction.serializer(), this)
fun decodeWatchAction(json: String): WatchAction = watchJson.decodeFromString(WatchAction.serializer(), json)
