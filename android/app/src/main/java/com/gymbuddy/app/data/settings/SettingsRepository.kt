package com.gymbuddy.app.data.settings

import android.content.Context
import com.gymbuddy.app.domain.DEFAULT_REST_SECONDS
import com.gymbuddy.app.domain.WeightUnit
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
)

/** Preferences kept on this device. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun setWeightUnit(unit: WeightUnit) = update { it.copy(weightUnit = unit) }

    fun setDefaultRestSeconds(seconds: Int) = update { it.copy(defaultRestSeconds = seconds.coerceAtLeast(0)) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = state.updateAndGet(transform)
        prefs.edit()
            .putString(KEY_WEIGHT_UNIT, next.weightUnit.name)
            .putInt(KEY_DEFAULT_REST, next.defaultRestSeconds)
            .apply()
    }

    private fun read() = AppSettings(
        weightUnit = WeightUnit.entries.firstOrNull { it.name == prefs.getString(KEY_WEIGHT_UNIT, null) } ?: WeightUnit.KG,
        defaultRestSeconds = prefs.getInt(KEY_DEFAULT_REST, DEFAULT_REST_SECONDS),
    )

    private companion object {
        const val KEY_WEIGHT_UNIT = "weight_unit"
        const val KEY_DEFAULT_REST = "default_rest_seconds"
    }
}
