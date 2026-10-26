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
