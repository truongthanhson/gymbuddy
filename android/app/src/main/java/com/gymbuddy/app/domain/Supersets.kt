package com.gymbuddy.app.domain

// Supersets are neighbouring exercises with the same group number; null is not in a superset.
// Groups are given in exercise order (shared/test-vectors/supersets.json).

/** Whether a set of the exercise at [index] starts the rest timer: only the last exercise of a superset does. */
fun startsRest(groups: List<Int?>, index: Int): Boolean {
    val group = groups.getOrNull(index) ?: return true
    return groups.getOrNull(index + 1) != group
}

/** Joins the exercise at [index] and the one after it into a superset. */
fun supersetWithNext(groups: List<Int?>, index: Int): List<Int?> {
    if (index + 1 !in groups.indices) return groups
    val group = groups[index] ?: groups[index + 1] ?: ((groups.filterNotNull().maxOrNull() ?: 0) + 1)
    return groups.toMutableList().apply {
        this[index] = group
        this[index + 1] = group
    }
}

/** Takes the exercise at [index] out of its superset; a superset left with one exercise ends. */
fun removeFromSuperset(groups: List<Int?>, index: Int): List<Int?> {
    val result = groups.toMutableList()
    val group = result.getOrNull(index) ?: return groups
    result[index] = null
    if (result.count { it == group } == 1) result.replaceAll { if (it == group) null else it }
    return result
}
