package com.gymbuddy.app.domain

import kotlin.math.round

/** A value a set can log. */
enum class SetField { WEIGHT, REPS, DISTANCE, DURATION }

/** The values a set of an exercise in [category] logs, in column order (shared/test-vectors/set_fields.json). */
fun setFields(category: String): List<SetField> = when (category) {
    ExerciseCategory.REPS_ONLY.key -> listOf(SetField.REPS)
    ExerciseCategory.CARDIO.key -> listOf(SetField.DISTANCE, SetField.DURATION)
    ExerciseCategory.DURATION.key -> listOf(SetField.DURATION)
    else -> listOf(SetField.WEIGHT, SetField.REPS)
}

/** Seconds from "90", "1:30" or "1:05:09"; null for nothing, zero or a malformed time. */
fun parseDuration(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    // Everything after the first part is minutes or seconds of a clock time.
    if (numbers.drop(1).any { it >= 60 }) return null
    val seconds = numbers.fold(0) { total, n -> total * 60 + n }
    return seconds.takeIf { it > 0 }
}

fun formatDuration(seconds: Int): String = formatElapsed(seconds.toLong())

enum class DistanceUnit(val metersPerUnit: Double, val symbol: String) {
    KM(1000.0, "km"),
    MI(1609.344, "mi");

    /** Rounded to 2 decimals for display. */
    fun display(meters: Double): Double = round(meters / metersPerUnit * 100) / 100

    fun toMeters(value: Double): Double = value * metersPerUnit
}

/** Distances follow the weight unit: kilometers with kg, miles with lb. */
val WeightUnit.distanceUnit: DistanceUnit
    get() = if (this == WeightUnit.LB) DistanceUnit.MI else DistanceUnit.KM
