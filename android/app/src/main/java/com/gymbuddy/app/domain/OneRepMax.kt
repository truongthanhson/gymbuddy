package com.gymbuddy.app.domain

/**
 * Estimated one-rep max using the Epley formula.
 * Must match shared/test-vectors/one_rep_max.json (and the iOS implementation).
 */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
    reps <= 0 -> 0.0
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}
