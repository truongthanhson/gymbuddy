package com.gymbuddy.app.domain

/** The numbers of a set that matter for stats; platform-neutral so it matches the iOS implementation. */
data class SetValues(val weightKg: Double?, val reps: Int?, val completed: Boolean)

/** Total volume in kg: weight × reps over completed sets. */
fun volumeKg(sets: List<SetValues>): Double =
    sets.filter { it.completed }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

/** Index of the completed set with the highest estimated 1RM (ties go to the earlier set), or null. */
fun bestSetIndex(sets: List<SetValues>): Int? {
    var best: Int? = null
    var bestValue = Double.NEGATIVE_INFINITY
    sets.forEachIndexed { index, set ->
        val reps = set.reps ?: 0
        if (!set.completed || reps <= 0) return@forEachIndexed
        val value = estimatedOneRepMax(set.weightKg ?: 0.0, reps)
        if (value > bestValue) {
            best = index
            bestValue = value
        }
    }
    return best
}

/** "1:05:09" or "42:07" for an elapsed duration. */
fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Weight without a trailing ".0": 100 → "100", 102.5 → "102.5". */
fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
