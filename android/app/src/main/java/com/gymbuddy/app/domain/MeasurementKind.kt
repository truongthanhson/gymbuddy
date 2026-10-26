package com.gymbuddy.app.domain

import androidx.annotation.StringRes
import com.gymbuddy.app.R

/** How a measurement is stored; body weight is in kg and shown in the user's unit. */
enum class MeasurementUnit(val key: String) { KG("kg"), PERCENT("percent"), CM("cm") }

/** Body measurements; keys match the `kind` column in supabase/migrations (and iOS). */
enum class MeasurementKind(val key: String, val unit: MeasurementUnit, @StringRes val label: Int) {
    WEIGHT("weight", MeasurementUnit.KG, R.string.measure_weight),
    BODY_FAT("body_fat", MeasurementUnit.PERCENT, R.string.measure_body_fat),
    NECK("neck", MeasurementUnit.CM, R.string.measure_neck),
    CHEST("chest", MeasurementUnit.CM, R.string.measure_chest),
    WAIST("waist", MeasurementUnit.CM, R.string.measure_waist),
    HIPS("hips", MeasurementUnit.CM, R.string.measure_hips),
    ARM_LEFT("arm_left", MeasurementUnit.CM, R.string.measure_arm_left),
    ARM_RIGHT("arm_right", MeasurementUnit.CM, R.string.measure_arm_right),
    THIGH_LEFT("thigh_left", MeasurementUnit.CM, R.string.measure_thigh_left),
    THIGH_RIGHT("thigh_right", MeasurementUnit.CM, R.string.measure_thigh_right),
    CALF_LEFT("calf_left", MeasurementUnit.CM, R.string.measure_calf_left),
    CALF_RIGHT("calf_right", MeasurementUnit.CM, R.string.measure_calf_right);

    /** A stored value as shown to the user, e.g. "80.5 kg", "18.2 %", "38 cm". */
    fun format(value: Double, weightUnit: WeightUnit): String = when (unit) {
        MeasurementUnit.KG -> weightUnit.format(value)
        MeasurementUnit.PERCENT -> "${formatWeight(round2(value))} %"
        MeasurementUnit.CM -> "${formatWeight(round2(value))} cm"
    }

    /** A stored value in the unit the user types in. */
    fun display(value: Double, weightUnit: WeightUnit): Double =
        if (unit == MeasurementUnit.KG) weightUnit.display(value) else round2(value)

    /** What the user typed, converted to the stored unit. */
    fun toStored(typed: Double, weightUnit: WeightUnit): Double =
        if (unit == MeasurementUnit.KG) weightUnit.toKg(typed) else typed

    fun symbol(weightUnit: WeightUnit): String = when (unit) {
        MeasurementUnit.KG -> weightUnit.symbol
        MeasurementUnit.PERCENT -> "%"
        MeasurementUnit.CM -> "cm"
    }

    companion object {
        fun fromKey(key: String): MeasurementKind? = entries.firstOrNull { it.key == key }
    }
}

private fun round2(value: Double) = kotlin.math.round(value * 100) / 100
