package com.gymbuddy.app.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.exercise.ExerciseRepository
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.ExerciseCategory
import com.gymbuddy.app.domain.ExerciseRecords
import com.gymbuddy.app.domain.ExerciseSession
import com.gymbuddy.app.domain.SessionPoint
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.exerciseProgress
import com.gymbuddy.app.ui.ExerciseDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One finished workout's completed sets of this exercise. */
data class HistoryWorkout(val workoutId: String, val name: String, val startedAt: Long, val sets: List<SetValues>)

data class ExerciseDetailState(
    val exercise: ExerciseEntity? = null,
    /** Newest first. */
    val history: List<HistoryWorkout> = emptyList(),
    val records: ExerciseRecords = ExerciseRecords(),
    /** Oldest first. */
    val points: List<SessionPoint> = emptyList(),
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseDao: ExerciseDao,
    workoutDao: WorkoutDao,
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId

    val state: StateFlow<ExerciseDetailState> = combine(
        exerciseDao.observeById(exerciseId),
        workoutDao.observeExerciseHistory(exerciseId),
    ) { exercise, rows ->
        val history = rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
            HistoryWorkout(
                workoutId = workoutId,
                name = sets.first().workoutName,
                startedAt = sets.first().startedAt,
                sets = sets.map { SetValues(it.weightKg, it.reps, completed = true) },
            )
        }
        val (records, points) = exerciseProgress(history.map { ExerciseSession(it.startedAt, it.sets) })
        ExerciseDetailState(exercise, history, records, points)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    fun update(name: String, bodyPart: BodyPart, category: ExerciseCategory) {
        viewModelScope.launch { repository.updateCustom(exerciseId, name, bodyPart, category) }
    }

    /** [onDeleted] runs after the delete, so leaving the screen (which cancels [viewModelScope]) cannot cut it short. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCustom(exerciseId)
            onDeleted()
        }
    }
}
