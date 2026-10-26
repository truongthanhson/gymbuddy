package com.gymbuddy.app.domain

/** Weights are stored in kg; other units are for display only. */
enum class WeightUnit(val perKg: Double) {
    KG(1.0),
    LB(2.20462262185);

    fun fromKg(kg: Double): Double = kg * perKg
    fun toKg(value: Double): Double = value / perKg
}
