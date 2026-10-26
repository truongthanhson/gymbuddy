package com.gymbuddy.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.measurement.MeasurementRepository
import com.gymbuddy.app.domain.MeasurementKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel @Inject constructor(measurements: MeasurementRepository) : ViewModel() {
    /** Body weight entries, oldest first, for the Profile chart. */
    val bodyWeights: StateFlow<List<MeasurementEntity>> = measurements.measurements
        .map { all -> all.filter { it.kind == MeasurementKind.WEIGHT.key }.reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
