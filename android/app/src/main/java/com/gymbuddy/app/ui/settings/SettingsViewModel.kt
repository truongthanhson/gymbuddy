package com.gymbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import com.gymbuddy.app.data.settings.AppSettings
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.domain.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    fun setWeightUnit(unit: WeightUnit) = repository.setWeightUnit(unit)

    fun setDefaultRestSeconds(seconds: Int) = repository.setDefaultRestSeconds(seconds)
}
