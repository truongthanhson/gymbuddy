package com.gymbuddy.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.health.HealthConnectManager
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.data.template.TemplateRepository
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutExerciseWithSets
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.data.workout.filledFrom
import com.gymbuddy.app.data.workout.isEmpty
import com.gymbuddy.app.domain.RestTimerState
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.liveRecords
import com.gymbuddy.app.domain.recordHolders
import com.gymbuddy.app.domain.resolveRestSeconds
import com.gymbuddy.app.domain.warmupSets
import com.gymbuddy.app.timer.RestTimerController
import com.gymbuddy.app.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [workout] is null when no workout is in progress. */
data class ActiveWorkoutState(val workout: WorkoutWithExercises?)

/** What the "workout complete" screen shows: the [number]th finished workout, with [records] record-setting sets. */
data class WorkoutSummary(val workout: WorkoutWithExercises, val number: Int, val records: Int)

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

    private val summaryState = MutableStateFlow<WorkoutSummary?>(null)

    /** The workout just finished, until its summary is closed. */
    val summary: StateFlow<WorkoutSummary?> = summaryState.asStateFlow()

    fun summaryShown() {
        summaryState.value = null
    }

    /** Null until the database has answered, so the UI doesn't flash the start screen. */
    val state: StateFlow<ActiveWorkoutState?> = repository.observeActive()
        .map { ActiveWorkoutState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Cached per exercise so a card scrolling back into view reuses the result instead of querying again.
    private val previousCache = mutableMapOf<Pair<String, Long>, StateFlow<List<WorkoutSetEntity>>>()
    private val historyCache = mutableMapOf<Pair<String, Long>, StateFlow<List<SetValues>>>()

    /** Sets from the last finished workout with this exercise that started before [before]. */
    fun previousSets(exerciseId: String, before: Long): StateFlow<List<WorkoutSetEntity>> =
        previousCache.getOrPut(exerciseId to before) {
            repository.observePreviousSets(exerciseId, before)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(CACHE_MILLIS), emptyList())
        }

    /** Only the sets holding a record: enough for the live PR badges, and cheap to compare against. */
    fun historySets(exerciseId: String, before: Long): StateFlow<List<SetValues>> =
        historyCache.getOrPut(exerciseId to before) {
            repository.observeHistorySets(exerciseId, before)
                .map(::recordHolders)
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(CACHE_MILLIS), emptyList())
        }

    /** A finished workout from history, for editing. Emits null once it is deleted. */
    fun workout(workoutId: String): Flow<WorkoutWithExercises?> = repository.observeWorkout(workoutId)

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
        val current = settings.settings.value
        val unit = current.weightUnit
        val working = item.sets.filter { it.setType == SetType.NORMAL }.mapNotNull { it.weightKg }.maxOrNull() ?: return@launch
        val sets = warmupSets(unit.display(working), current.barFor(), current.platesFor())
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
        summaryState.value = summarize(id)
        if (saveToTemplate) {
            val templateId = workout.workout.templateId
            if (templateId != null) templates.updateFromWorkout(templateId, id) else templates.createFromWorkout(id)
        }
        sync.requestSync()
        health.onWorkoutFinished(id)
        widgets.refresh()
    }

    private suspend fun summarize(workoutId: String): WorkoutSummary? {
        val finished = repository.observeWorkout(workoutId).first() ?: return null
        val number = repository.observeHistory().first().size
        // Warm-up sets never count as records, as on the sets while logging.
        val records = finished.exercises.sumOf { exercise ->
            val history = repository.observeHistorySets(exercise.workoutExercise.exerciseId, finished.workout.startedAt).first()
            val sets = exercise.sets.map { SetValues(it.weightKg, it.reps, it.completed && it.setType != SetType.WARMUP) }
            liveRecords(history, sets).count { it.isNotEmpty() }
        }
        return WorkoutSummary(finished, number, records)
    }

    /** Leaving the editor of a finished workout. */
    fun finishEditing(workoutId: String) = launch {
        repository.finishEditing(workoutId)
        afterHistoryChange()
    }

    fun setWorkoutTimes(workoutId: String, startedAt: Long, endedAt: Long) = launch {
        repository.setWorkoutTimes(workoutId, startedAt, endedAt)
        afterHistoryChange()
    }

    fun deleteWorkout(workoutId: String) = launch {
        repository.discardWorkout(workoutId)
        afterHistoryChange()
    }

    fun saveAsTemplate(workoutId: String) = launch { templates.createFromWorkout(workoutId) }

    /** Starts a new workout like [workoutId]; does nothing while another one is in progress. */
    fun repeatWorkout(workoutId: String) = launch { repository.repeatWorkout(workoutId) }

    private fun afterHistoryChange() {
        sync.requestSync()
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

private const val CACHE_MILLIS = 60_000L
