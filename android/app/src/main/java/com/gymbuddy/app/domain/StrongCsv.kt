package com.gymbuddy.app.domain

// CSV export and import in Strong's column layout; must match shared/test-vectors/csv.json (and iOS).

/** [setType] is "normal", "warmup", "drop" or "failure"; [weightKg] is always kg. */
data class CsvSet(
    val setType: String = "normal",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceM: Double? = null,
    val seconds: Int? = null,
    val rpe: Double? = null,
)

data class CsvExercise(val name: String, val note: String?, val sets: List<CsvSet>)

/** [date] is local time as "yyyy-MM-dd HH:mm:ss", the way Strong writes it. */
data class CsvWorkout(
    val date: String,
    val name: String,
    val durationSec: Int?,
    val note: String?,
    val exercises: List<CsvExercise>,
)

private val EXPORT_HEADER = listOf(
    "Date", "Workout Name", "Duration", "Exercise Name", "Set Order", "Weight", "Reps",
    "Distance", "Seconds", "Notes", "Workout Notes", "RPE", "Weight Unit",
)

/** One CSV line; fields with commas, quotes or line breaks are quoted. */
fun csvLine(fields: List<String>): String = fields.joinToString(",") { field ->
    if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${field.replace("\"", "\"\"")}\"" else field
}

/** Rows of a CSV text (RFC 4180, either line ending); blank lines are skipped. */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = if (text.startsWith('﻿')) 1 else 0
    fun endRow() {
        row += field.toString()
        field.clear()
        if (row.size > 1 || row[0].isNotEmpty()) rows += row
        row = mutableListOf()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' -> quoted = !quoted
            quoted -> field.append(c)
            c == ',' -> { row += field.toString(); field.clear() }
            c == '\r' && text.getOrNull(i + 1) == '\n' -> { endRow(); i++ }
            c == '\n' || c == '\r' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

/** "1h 5m" or "45m". */
fun formatCsvDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Reads "1h 5m", "45m", "30s" or a plain number of seconds. */
fun parseCsvDuration(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toIntOrNull()?.let { return it }
    val parts = Regex("""(\d+)\s*([hms])""").findAll(trimmed).toList()
    if (parts.isEmpty() || parts.joinToString("") { it.value }.filterNot(Char::isWhitespace) != trimmed.filterNot(Char::isWhitespace)) return null
    return parts.sumOf { part ->
        val value = part.groupValues[1].toInt()
        when (part.groupValues[2]) {
            "h" -> value * 3600
            "m" -> value * 60
            else -> value
        }
    }
}

private fun number(value: Double?): String = value?.let(::formatWeight) ?: ""

/** Workouts as CSV, one row per set, weights in [unit]. */
fun exportCsv(workouts: List<CsvWorkout>, unit: WeightUnit): String = buildString {
    append(csvLine(EXPORT_HEADER)).append('\n')
    for (workout in workouts) {
        val duration = workout.durationSec?.let(::formatCsvDuration) ?: ""
        for (exercise in workout.exercises) {
            var normal = 0
            for (set in exercise.sets) {
                val order = when (set.setType) {
                    "warmup" -> "W"
                    "drop" -> "D"
                    "failure" -> "F"
                    else -> (++normal).toString()
                }
                val fields = listOf(
                    workout.date, workout.name, duration, exercise.name, order,
                    number(set.weightKg?.let(unit::display)), set.reps?.toString() ?: "",
                    number(set.distanceM), set.seconds?.toString() ?: "",
                    exercise.note ?: "", workout.note ?: "", number(set.rpe), unit.symbol,
                )
                append(csvLine(fields)).append('\n')
            }
        }
    }
}

/** The unit of a weight header like "Weight (lbs)", or null. */
private fun unitInHeader(header: String): WeightUnit? = when {
    header.contains("lb", ignoreCase = true) -> WeightUnit.LB
    header.contains("kg", ignoreCase = true) -> WeightUnit.KG
    else -> null
}

/**
 * Workouts from a Strong (or GymBuddy) CSV export, in file order. Weights without a unit are read in [defaultUnit].
 * Returns null when the text has no Date, Workout Name and Exercise Name columns.
 */
fun importCsv(text: String, defaultUnit: WeightUnit): List<CsvWorkout>? {
    val rows = parseCsv(text)
    val header = rows.firstOrNull()?.map { it.trim() } ?: return null
    fun column(name: String) = header.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
    val dateCol = column("Date") ?: return null
    val nameCol = column("Workout Name") ?: return null
    val exerciseCol = column("Exercise Name") ?: return null
    val weightCol = header.indexOfFirst { it.startsWith("Weight", ignoreCase = true) && !it.equals("Weight Unit", ignoreCase = true) }
        .takeIf { it >= 0 }
    val headerUnit = weightCol?.let { unitInHeader(header[it]) }
    val durationCol = column("Duration")
    val orderCol = column("Set Order")
    val repsCol = column("Reps")
    val distanceCol = column("Distance")
    val secondsCol = column("Seconds")
    val notesCol = column("Notes")
    val workoutNotesCol = column("Workout Notes")
    val rpeCol = column("RPE")
    val unitCol = column("Weight Unit")

    class ExerciseDraft(val name: String) {
        var note: String? = null
        val sets = mutableListOf<CsvSet>()
    }
    class WorkoutDraft(val date: String, val name: String, val durationSec: Int?) {
        var note: String? = null
        val exercises = LinkedHashMap<String, ExerciseDraft>()
    }
    val workouts = LinkedHashMap<Pair<String, String>, WorkoutDraft>()

    for (row in rows.drop(1)) {
        fun cell(col: Int?) = col?.let { row.getOrNull(it)?.trim() }.orEmpty()
        fun positive(col: Int?) = cell(col).replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        val date = cell(dateCol)
        val exerciseName = cell(exerciseCol)
        if (date.isEmpty() || exerciseName.isEmpty()) continue
        val name = cell(nameCol)
        val workout = workouts.getOrPut(date to name) { WorkoutDraft(date, name, parseCsvDuration(cell(durationCol))) }
        if (workout.note == null) workout.note = cell(workoutNotesCol).ifEmpty { null }
        val exercise = workout.exercises.getOrPut(exerciseName) { ExerciseDraft(exerciseName) }
        if (exercise.note == null) exercise.note = cell(notesCol).ifEmpty { null }
        val unit = WeightUnit.entries.firstOrNull { it.symbol.equals(cell(unitCol), ignoreCase = true) } ?: headerUnit ?: defaultUnit
        exercise.sets += CsvSet(
            setType = when (cell(orderCol).uppercase()) {
                "W" -> "warmup"
                "D" -> "drop"
                "F" -> "failure"
                else -> "normal"
            },
            weightKg = positive(weightCol)?.let(unit::toKg),
            reps = positive(repsCol)?.toInt(),
            distanceM = positive(distanceCol),
            seconds = positive(secondsCol)?.toInt(),
            rpe = positive(rpeCol),
        )
    }
    return workouts.values.map { draft ->
        CsvWorkout(
            date = draft.date,
            name = draft.name,
            durationSec = draft.durationSec,
            note = draft.note,
            exercises = draft.exercises.values.map { CsvExercise(it.name, it.note, it.sets.toList()) },
        )
    }
}
