package com.gymbuddy.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: WorkoutRepository) : ViewModel() {
    /** Null while loading. */
    val workouts: StateFlow<List<WorkoutWithExercises>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
