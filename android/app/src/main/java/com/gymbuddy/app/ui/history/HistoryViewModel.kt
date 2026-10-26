package com.gymbuddy.app.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.bestSetIndex
import com.gymbuddy.app.domain.volumeKg
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exercise lines a history card shows before "+N more". */
private const val CARD_EXERCISES = 4

/** One exercise of a history card: "3 × Bench Press" and its best set. */
@Immutable
data class HistoryLine(val sets: Int, val exercise: ExerciseEntity?, val bestWeightKg: Double?, val bestReps: Int?)

/** Everything a history card shows, worked out once off the main thread so scrolling only draws. */
@Immutable
data class HistoryCard(
    val id: String,
    val name: String,
    val date: String,
    val durationSeconds: Long,
    val volumeKg: Double,
    val lines: List<HistoryLine>,
    val moreExercises: Int,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: WorkoutRepository) : ViewModel() {
    /** Null while loading. */
    val workouts: StateFlow<List<WorkoutWithExercises>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null while loading. */
    val cards: StateFlow<List<HistoryCard>?> = workouts.filterNotNull()
        .map { list ->
            val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            list.map { it.toCard(dates) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun WorkoutWithExercises.toCard(dates: DateFormat): HistoryCard {
    val all = exercises.flatMap { e -> e.sets.map { SetValues(it.weightKg, it.reps, it.completed) } }
    return HistoryCard(
        id = workout.id,
        name = workout.name,
        date = dates.format(Date(workout.startedAt)),
        durationSeconds = ((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1_000,
        volumeKg = volumeKg(all),
        lines = exercises.take(CARD_EXERCISES).map { e ->
            val sets = e.sets.map { SetValues(it.weightKg, it.reps, it.completed) }
            val best = bestSetIndex(sets)?.let { sets[it] }
            HistoryLine(sets.size, e.exercise, best?.weightKg, best?.reps)
        },
        moreExercises = (exercises.size - CARD_EXERCISES).coerceAtLeast(0),
    )
}
