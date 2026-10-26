package com.gymbuddy.app.domain

import kotlin.math.round

/** Plates in the user's unit; must match shared/test-vectors/calculators.json (and iOS). */
val WeightUnit.defaultPlates: List<Double>
    get() = when (this) {
        WeightUnit.KG -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        WeightUnit.LB -> listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    }

val WeightUnit.defaultBar: Double
    get() = when (this) {
        WeightUnit.KG -> 20.0
        WeightUnit.LB -> 45.0
    }

/** [perSide] is heaviest first; [remainder] is what the plates could not make (negative below the bar). */
data class PlateLoad(val perSide: List<Double>, val loaded: Double, val remainder: Double)

private fun round4(value: Double) = round(value * 10_000) / 10_000

/** Loads plates greedily, heaviest first, on each side of the bar. */
fun plateLoad(target: Double, bar: Double, plates: List<Double>): PlateLoad {
    val perSide = mutableListOf<Double>()
    var side = (target - bar) / 2
    for (plate in plates.filter { it > 0 }.sortedDescending()) {
        while (side >= plate - 1e-9) {
            perSide += plate
            side -= plate
        }
    }
    val loaded = round4(bar + 2 * perSide.sum())
    return PlateLoad(perSide, loaded, round4(target - loaded))
}

/** Weight you could lift for [reps] given a one-rep max (Epley inverted). */
fun repMaxWeight(oneRepMax: Double, reps: Int): Double =
    if (reps <= 1) oneRepMax else oneRepMax / (1 + reps / 30.0)

data class WarmupSet(val weight: Double, val reps: Int)

/** Percent of the working weight and reps of each warm-up step; null is the empty bar. */
private val WARMUP_STEPS = listOf<Pair<Double?, Int>>(null to 10, 0.4 to 5, 0.6 to 3, 0.8 to 2)

/**
 * Warm-up sets before [working], each rounded down to what [plates] can load on [bar].
 * Steps that are not heavier than the one before, or not lighter than [working], are left out.
 */
fun warmupSets(working: Double, bar: Double, plates: List<Double>): List<WarmupSet> {
    val sets = mutableListOf<WarmupSet>()
    for ((percent, reps) in WARMUP_STEPS) {
        val weight = if (percent == null) bar else plateLoad(working * percent, bar, plates).loaded
        val heavier = sets.lastOrNull()?.let { weight > it.weight + 1e-9 } ?: true
        if (heavier && weight < working - 1e-9) sets += WarmupSet(weight, reps)
    }
    return sets
}
