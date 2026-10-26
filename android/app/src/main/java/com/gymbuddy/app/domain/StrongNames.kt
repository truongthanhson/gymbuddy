package com.gymbuddy.app.domain

// How a Strong CSV import names and files exercises; must match shared/test-vectors/strong_names.json (and iOS).

/** Strong names that the library spells differently, lowercased. */
private val STRONG_ALIASES = mapOf(
    "triceps pushdown (cable - straight bar)" to "Triceps Pushdown (Cable)",
    "triceps pushdown (cable - rope)" to "Triceps Pushdown (Rope)",
    "crunch (machine)" to "Ab Crunch (Machine)",
    "calf press on seated leg press" to "Calf Press (Leg Press)",
    "triceps extension (dumbbell)" to "Overhead Triceps Extension (Dumbbell)",
    "triceps extension (cable)" to "Overhead Triceps Extension (Cable)",
    "strict military press (barbell)" to "Overhead Press (Barbell)",
    "leg press (machine)" to "Leg Press",
    "hack squat" to "Hack Squat (Machine)",
)

/** The library's name for a Strong exercise, or [name] itself. */
fun libraryName(name: String): String = STRONG_ALIASES[name.trim().lowercase()] ?: name

/** The category from the equipment in brackets, like "Bicep Curl (Machine)", as a key; null when it doesn't say. */
fun categoryFromName(name: String): String? {
    val lower = name.lowercase()
    return when {
        "(barbell" in lower -> "barbell"
        "(dumbbell" in lower -> "dumbbell"
        "(machine" in lower || "(plate loaded" in lower || "(smith machine" in lower -> "machine"
        "(cable" in lower -> "cable"
        "(assisted" in lower -> "assisted_bodyweight"
        "(weighted" in lower -> "weighted_bodyweight"
        "(bodyweight" in lower -> "reps_only"
        else -> null
    }
}

/** Words that give away the body part, checked in this order (some shoulder moves would otherwise read as chest or back). */
private val BODY_PART_WORDS = listOf(
    "shoulders" to listOf("reverse fly", "upright row", "rear delt", "face pull", "handstand", "pike push", "push press", "landmine press"),
    "legs" to listOf(
        "calf", "leg press", "leg extension", "leg curl", "squat", "lunge", "hamstring", "glute", "hip thrust", "deadlift",
        "step up", "good morning", "box jump", "wall sit", "abduct", "adduct",
    ),
    "back" to listOf("row", "pulldown", "pull up", "pull-up", "chin up", "chin-up", "lat ", "back extension", "shrug", "rack pull"),
    "chest" to listOf("bench", "chest", "fly", "flye", "pec ", "pec deck", "push up", "push-up", "crossover"),
    "shoulders" to listOf("shoulder", "overhead press", "military", "lateral raise", "front raise", "arnold"),
    "arms" to listOf("curl", "tricep", "bicep", "dip", "skull", "kickback", "wrist"),
    "core" to listOf(
        "crunch", "plank", "sit up", "sit-up", "ab ", "abs", "russian twist", "leg raise", "knee raise", "woodchop",
        "dead bug", "hollow", "oblique", "toes to bar", "mountain climber", "pallof",
    ),
)

private fun baseName(name: String) = name.lowercase().substringBefore(" (").trim()

/**
 * The body part key for an exercise that is not in the library: that of a [library] exercise (name to body part key)
 * with the same name before the brackets, else from words in the name, else "other".
 */
fun guessBodyPart(name: String, library: List<Pair<String, String>>): String {
    val base = baseName(name)
    library.firstOrNull { baseName(it.first) == base }?.let { return it.second }
    val words = name.lowercase() + " "
    return BODY_PART_WORDS.firstOrNull { (_, list) -> list.any { it in words } }?.first ?: "other"
}
