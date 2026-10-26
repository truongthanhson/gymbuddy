package com.gymbuddy.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.health.HealthConnectManager
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.widget.WidgetRefresher
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.data.template.TemplateRepository
import com.gymbuddy.app.data.workout.WorkoutExerciseWithSets
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.data.workout.filledFrom
import com.gymbuddy.app.data.workout.isEmpty
import com.gymbuddy.app.domain.RestTimerState
import com.gymbuddy.app.domain.defaultBar
import com.gymbuddy.app.domain.defaultPlates
import com.gymbuddy.app.domain.warmupSets
import com.gymbuddy.app.domain.resolveRestSeconds
import com.gymbuddy.app.timer.RestTimerController
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
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val restTimer: RestTimerController,
    private val templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val sync: SyncManager,
    private val health: HealthConnectManager,
    private val widgets: WidgetRefresher,
) : ViewModel() {

    val restTimerState: StateFlow<RestTimerState?> = restTimer.state

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

    fun setDistance(setId: String, meters: Double?) = launch {
        repository.updateSet(setId) { it.copy(distanceM = meters) }
    }

    fun setDuration(setId: String, seconds: Int?) = launch {
        repository.updateSet(setId) { it.copy(durationSec = seconds) }
    }

    /** Adds Strong-style warm-up sets before the heaviest normal set of the exercise. */
    fun addWarmupSets(item: WorkoutExerciseWithSets) = launch {
        val unit = settings.settings.value.weightUnit
        val working = item.sets.filter { it.setType == SetType.NORMAL }.mapNotNull { it.weightKg }.maxOrNull() ?: return@launch
        val sets = warmupSets(unit.display(working), unit.defaultBar, unit.defaultPlates)
        repository.insertWarmupSets(item.workoutExercise.id, sets.map { unit.toKg(it.weight) to it.reps })
    }

    fun setSetType(setId: String, type: SetType) = launch {
        repository.updateSet(setId) { it.copy(setType = type) }
    }

    fun setRpe(setId: String, rpe: Double?) = launch {
        repository.updateSet(setId) { it.copy(rpe = rpe) }
    }

    /** A blank note is stored as no note. */
    fun setExerciseNote(workoutExerciseId: String, note: String) =
        launch { repository.setExerciseNote(workoutExerciseId, note.ifBlank { null }) }

    fun setWorkoutNote(workoutId: String, note: String) =
        launch { repository.setWorkoutNote(workoutId, note.ifBlank { null }) }

    /**
     * Ticking an empty set fills it from the previous session, like Strong.
     * Ticking a set starts the rest timer for its exercise; [exerciseName] labels the notification.
     */
    fun toggleSet(
        item: WorkoutExerciseWithSets,
        set: WorkoutSetEntity,
        previous: WorkoutSetEntity?,
        exerciseName: String,
        startsRest: Boolean,
    ) = launch {
        if (!set.completed && set.isEmpty() && previous != null) repository.updateSet(set.id) { it.filledFrom(previous) }
        repository.toggleSetCompleted(set.id)
        if (!set.completed && startsRest) {
            restTimer.start(
                resolveRestSeconds(
                    item.workoutExercise.restSeconds,
                    item.exercise?.defaultRestSeconds,
                    settings.settings.value.defaultRestSeconds,
                ),
                exerciseName,
            )
        }
    }

    fun moveExercise(workoutId: String, workoutExerciseId: String, delta: Int) =
        launch { repository.moveExercise(workoutId, workoutExerciseId, delta) }

    fun replaceExercise(workoutExerciseId: String, exerciseId: String) =
        launch { repository.replaceExercise(workoutExerciseId, exerciseId) }

    fun supersetWithNext(workoutId: String, index: Int) =
        launch { repository.updateSupersets(workoutId) { com.gymbuddy.app.domain.supersetWithNext(it, index) } }

    fun removeFromSuperset(workoutId: String, index: Int) =
        launch { repository.updateSupersets(workoutId) { com.gymbuddy.app.domain.removeFromSuperset(it, index) } }

    fun setRestSeconds(workoutExerciseId: String, seconds: Int) =
        launch { repository.setRestSeconds(workoutExerciseId, seconds) }

    fun adjustRestTimer(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    fun skipRestTimer() = restTimer.skip()

    fun onRestTimerElapsed() = restTimer.finishNow()

    fun deleteSet(setId: String) = launch { repository.deleteSet(setId) }

    fun removeExercise(workoutExerciseId: String) = launch { repository.removeExercise(workoutExerciseId) }

    fun renameWorkout(workoutId: String, name: String) = launch { repository.renameWorkout(workoutId, name) }

    /**
     * Finishing with no ticked sets discards the workout instead of saving an empty one.
     * With [saveToTemplate], a workout started from a template updates it; any other workout becomes a new template.
     */
    fun finishWorkout(workout: WorkoutWithExercises, saveToTemplate: Boolean) = launch {
        restTimer.skip()
        val id = workout.workout.id
        val anyCompleted = workout.exercises.any { e -> e.sets.any { it.completed } }
        if (!anyCompleted) {
            repository.discardWorkout(id)
            return@launch
        }
        repository.finishWorkout(id)
        if (saveToTemplate) {
            val templateId = workout.workout.templateId
            if (templateId != null) templates.updateFromWorkout(templateId, id) else templates.createFromWorkout(id)
        }
        sync.requestSync()
        health.onWorkoutFinished(id)
        widgets.refresh()
    }

    fun discardWorkout(workoutId: String) = launch {
        restTimer.skip()
        repository.discardWorkout(workoutId)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
