package com.gymbuddy.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [workout] is null when no workout is in progress. */
data class ActiveWorkoutState(val workout: WorkoutWithExercises?)

@HiltViewModel
class WorkoutViewModel @Inject constructor(private val repository: WorkoutRepository) : ViewModel() {

    /** Null until the database has answered, so the UI doesn't flash the start screen. */
    val state: StateFlow<ActiveWorkoutState?> = repository.observeActive()
        .map { ActiveWorkoutState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousSets(exerciseId: String): Flow<List<WorkoutSetEntity>> = repository.observePreviousSets(exerciseId)

    fun startEmptyWorkout(name: String) = launch { repository.startEmptyWorkout(name) }

    fun addExercises(workoutId: String, exerciseIds: List<String>) =
        launch { repository.addExercises(workoutId, exerciseIds) }

    fun addSet(workoutExerciseId: String) = launch { repository.addSet(workoutExerciseId) }

    fun setWeight(setId: String, weightKg: Double?) = launch {
        repository.updateSet(setId) { it.copy(weightKg = weightKg) }
    }

    fun setReps(setId: String, reps: Int?) = launch {
        repository.updateSet(setId) { it.copy(reps = reps) }
    }

    /** Ticking an empty set fills it from the previous session, like Strong. */
    fun toggleSet(set: WorkoutSetEntity, previous: WorkoutSetEntity?) = launch {
        if (!set.completed && set.weightKg == null && set.reps == null && previous != null) {
            repository.updateSet(set.id) { it.copy(weightKg = previous.weightKg, reps = previous.reps) }
        }
        repository.toggleSetCompleted(set.id)
    }

    fun deleteSet(setId: String) = launch { repository.deleteSet(setId) }

    fun removeExercise(workoutExerciseId: String) = launch { repository.removeExercise(workoutExerciseId) }

    fun renameWorkout(workoutId: String, name: String) = launch { repository.renameWorkout(workoutId, name) }

    /** Finishing with no ticked sets discards the workout instead of saving an empty one. */
    fun finishWorkout(workout: WorkoutWithExercises) = launch {
        val anyCompleted = workout.exercises.any { e -> e.sets.any { it.completed } }
        if (anyCompleted) {
            repository.finishWorkout(workout.workout.id)
        } else {
            repository.discardWorkout(workout.workout.id)
        }
    }

    fun discardWorkout(workoutId: String) = launch { repository.discardWorkout(workoutId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
