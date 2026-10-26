package com.gymbuddy.app.ui.measurements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.measurement.MeasurementRepository
import com.gymbuddy.app.domain.MeasurementKind
import com.gymbuddy.app.ui.MeasurementDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The latest measurement of each kind, for the Measure tab. */
@HiltViewModel
class MeasurementsViewModel @Inject constructor(repository: MeasurementRepository) : ViewModel() {
    val latest: StateFlow<Map<MeasurementKind, MeasurementEntity>> = repository.measurements
        .map { all ->
            // Newest first, so the first of each kind is its latest.
            all.groupBy { it.kind }.mapNotNull { (key, rows) -> MeasurementKind.fromKey(key)?.let { it to rows.first() } }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/** All measurements of one kind. */
@HiltViewModel
class MeasurementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MeasurementRepository,
) : ViewModel() {
    val kind: MeasurementKind = MeasurementKind.fromKey(savedStateHandle.toRoute<MeasurementDetailRoute>().kind) ?: MeasurementKind.WEIGHT

    /** Newest first. */
    val entries: StateFlow<List<MeasurementEntity>> = repository.measurements
        .map { all -> all.filter { it.kind == kind.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [value] is in the stored unit. */
    fun add(value: Double, measuredAt: Long = System.currentTimeMillis()) {
        viewModelScope.launch { repository.add(kind, value, measuredAt) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
