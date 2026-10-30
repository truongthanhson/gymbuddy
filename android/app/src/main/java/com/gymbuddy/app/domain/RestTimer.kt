package com.gymbuddy.app.domain

import kotlin.math.ceil

/** Rest after a set when neither the exercise in this workout nor the library exercise sets one. */
const val DEFAULT_REST_SECONDS = 120

/** Picks the rest time for a set: per-workout override, then the exercise's default, then the app default. 0 = off. */
fun resolveRestSeconds(workoutExerciseRest: Int?, exerciseDefault: Int?, appDefault: Int = DEFAULT_REST_SECONDS): Int =
    (workoutExerciseRest ?: exerciseDefault ?: appDefault).coerceAtLeast(0)

/**
 * A running rest timer. Times are epoch milliseconds so the state survives the UI going away.
 * [setId] is the set whose completion started it, so that set can show the countdown.
 */
data class RestTimerState(val endsAtMillis: Long, val totalSeconds: Int, val setId: String? = null) {

    fun remainingSeconds(nowMillis: Long): Int =
        ceil((endsAtMillis - nowMillis).coerceAtLeast(0) / 1000.0).toInt()

    /** Fraction of the rest still to go, 1 → 0. */
    fun progress(nowMillis: Long): Float =
        if (totalSeconds <= 0) 0f else (remainingSeconds(nowMillis).toFloat() / totalSeconds).coerceIn(0f, 1f)

    /** Adds (or with a negative delta removes) time; null when that leaves no rest. */
    fun adjusted(deltaSeconds: Int, nowMillis: Long): RestTimerState? {
        val next = copy(endsAtMillis = endsAtMillis + deltaSeconds * 1000L, totalSeconds = (totalSeconds + deltaSeconds).coerceAtLeast(1))
        return if (next.remainingSeconds(nowMillis) <= 0) null else next
    }

    companion object {
        fun start(seconds: Int, nowMillis: Long, setId: String? = null): RestTimerState? =
            if (seconds <= 0) null else RestTimerState(nowMillis + seconds * 1000L, seconds, setId)
    }
}

/** Rest-time choices offered per exercise, in seconds (0 = off). */
val REST_TIME_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
