package com.gymbuddy.app.domain

import kotlin.math.round

/** Weights are stored in kg; other units are for display only. */
enum class WeightUnit(val perKg: Double, val symbol: String) {
    KG(1.0, "kg"),
    LB(2.20462262185, "lb");

    fun fromKg(kg: Double): Double = kg * perKg
    fun toKg(value: Double): Double = value / perKg

    /** A stored kg value in this unit, rounded to 2 decimals so what the user typed survives the round trip. */
    fun display(kg: Double): Double = round(fromKg(kg) * 100) / 100

    /** "102.5 kg" / "225 lb". */
    fun format(kg: Double): String = "${formatWeight(display(kg))} $symbol"
}
