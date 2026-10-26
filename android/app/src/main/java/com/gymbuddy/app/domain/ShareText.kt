package com.gymbuddy.app.domain

/** A completed set as shared text; [type] is the set type key ("warmup", "normal", "drop", "failure"). */
data class ShareSet(
    val type: String = "normal",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
)

data class ShareExercise(val name: String, val category: String, val sets: List<ShareSet>)

/** "100 kg × 5", "× 12", "5 km · 25:00"; the values an exercise in [category] logs. Matches iOS (share_text.json). */
fun setText(category: String, set: ShareSet, unit: WeightUnit): String {
    val parts = setFields(category).mapNotNull { field ->
        when (field) {
            SetField.WEIGHT -> set.weightKg?.let(unit::format)
            SetField.REPS -> set.reps?.let { "× $it" }
            SetField.DISTANCE -> set.distanceM?.let {
                val distance = unit.distanceUnit
                "${formatWeight(distance.display(it))} ${distance.symbol}"
            }
            SetField.DURATION -> set.durationSec?.let(::formatDuration)
        }
    }
    if (parts.isEmpty()) return "—"
    return parts.reduce { text, part -> if (part.startsWith("×")) "$text $part" else "$text · $part" }
}

/** The text a finished workout is shared as, like Strong's "Share workout". */
fun workoutShareText(title: String, subtitle: String, exercises: List<ShareExercise>, unit: WeightUnit): String = buildString {
    append(title).append('\n').append(subtitle)
    exercises.filter { it.sets.isNotEmpty() }.forEach { exercise ->
        append("\n\n").append(exercise.name)
        var number = 0
        exercise.sets.forEach { set ->
            val label = when (set.type) {
                "warmup" -> "W"
                "drop" -> "D"
                "failure" -> "F"
                else -> (++number).toString()
            }
            append('\n').append(label).append(": ").append(setText(exercise.category, set, unit))
        }
    }
}
