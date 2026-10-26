package com.gymbuddy.app.domain

/** A completed set for the muscle groups card: its exercise's body part key and when its workout started. */
data class BodyPartSet(val bodyPart: String, val startedAt: Long, val warmup: Boolean)

/**
 * Completed sets per body part since [sinceMillis], most first; warm-ups don't count.
 * Must match shared/test-vectors/body_part_sets.json (and iOS).
 */
fun setsPerBodyPart(sets: List<BodyPartSet>, sinceMillis: Long): List<Pair<BodyPart, Int>> =
    sets.filter { !it.warmup && it.startedAt >= sinceMillis }
        .groupingBy { BodyPart.fromKey(it.bodyPart) }
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<BodyPart, Int>> { it.second }.thenBy { it.first.ordinal })
