package com.gymbuddy.app.data.settings

import android.content.Context
import com.gymbuddy.app.domain.DEFAULT_REST_SECONDS
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.defaultBar
import com.gymbuddy.app.domain.defaultPlates
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
    /** Write finished workouts and body weight to Health Connect. */
    val healthEnabled: Boolean = false,
    /** Bar weight in each unit, where the user changed it. */
    val bars: Map<WeightUnit, Double> = emptyMap(),
    /** Plates the user has in each unit (heaviest first), where they changed them. */
    val plates: Map<WeightUnit, List<Double>> = emptyMap(),
) {
    fun barFor(unit: WeightUnit = weightUnit): Double = bars[unit] ?: unit.defaultBar
    fun platesFor(unit: WeightUnit = weightUnit): List<Double> = plates[unit] ?: unit.defaultPlates
}

/** Preferences kept on this device. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun setWeightUnit(unit: WeightUnit) = update { it.copy(weightUnit = unit) }

    fun setDefaultRestSeconds(seconds: Int) = update { it.copy(defaultRestSeconds = seconds.coerceAtLeast(0)) }

    fun setHealthEnabled(enabled: Boolean) = update { it.copy(healthEnabled = enabled) }

    fun setBar(unit: WeightUnit, weight: Double) = update { it.copy(bars = it.bars + (unit to weight.coerceAtLeast(0.0))) }

    fun setPlates(unit: WeightUnit, plates: List<Double>) =
        update { it.copy(plates = it.plates + (unit to plates.distinct().sortedDescending())) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = state.updateAndGet(transform)
        val editor = prefs.edit()
            .putString(KEY_WEIGHT_UNIT, next.weightUnit.name)
            .putInt(KEY_DEFAULT_REST, next.defaultRestSeconds)
            .putBoolean(KEY_HEALTH, next.healthEnabled)
        next.bars.forEach { (unit, weight) -> editor.putString(KEY_BAR + unit.name, weight.toString()) }
        next.plates.forEach { (unit, plates) -> editor.putString(KEY_PLATES + unit.name, plates.joinToString(",")) }
        editor.apply()
    }

    private fun read() = AppSettings(
        weightUnit = WeightUnit.entries.firstOrNull { it.name == prefs.getString(KEY_WEIGHT_UNIT, null) } ?: WeightUnit.KG,
        defaultRestSeconds = prefs.getInt(KEY_DEFAULT_REST, DEFAULT_REST_SECONDS),
        healthEnabled = prefs.getBoolean(KEY_HEALTH, false),
        bars = WeightUnit.entries.mapNotNull { unit ->
            prefs.getString(KEY_BAR + unit.name, null)?.toDoubleOrNull()?.let { unit to it }
        }.toMap(),
        plates = WeightUnit.entries.mapNotNull { unit ->
            prefs.getString(KEY_PLATES + unit.name, null)?.let { saved ->
                unit to saved.split(',').mapNotNull { it.toDoubleOrNull() }
            }
        }.toMap(),
    )

    private companion object {
        const val KEY_WEIGHT_UNIT = "weight_unit"
        const val KEY_DEFAULT_REST = "default_rest_seconds"
        const val KEY_HEALTH = "health_enabled"
        const val KEY_BAR = "bar_"
        const val KEY_PLATES = "plates_"
    }
}
