package com.gymbuddy.app.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.measurement.MeasurementRepository
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.BodyPartSet
import com.gymbuddy.app.domain.MeasurementKind
import com.gymbuddy.app.domain.PersonalRecord
import com.gymbuddy.app.domain.ProfileTotals
import com.gymbuddy.app.domain.StatsExercise
import com.gymbuddy.app.domain.StatsSet
import com.gymbuddy.app.domain.StatsWorkout
import com.gymbuddy.app.domain.TopExercise
import com.gymbuddy.app.domain.TrainingHabits
import com.gymbuddy.app.domain.WeekStreaks
import com.gymbuddy.app.domain.personalRecords
import com.gymbuddy.app.domain.profileTotals
import com.gymbuddy.app.domain.setsPerBodyPart
import com.gymbuddy.app.domain.startOfWeekMillis
import com.gymbuddy.app.domain.topExercises
import com.gymbuddy.app.domain.trainingHabits
import com.gymbuddy.app.domain.weekStreaks
import com.gymbuddy.app.domain.weeklyVolume
import com.gymbuddy.app.domain.workoutsPerDay
import com.gymbuddy.app.domain.workoutsPerWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val CHART_WEEKS = 8
const val HEATMAP_WEEKS = 18
const val VOLUME_WEEKS = 12
const val RECENT_RECORDS = 5
const val TOP_EXERCISES = 5

/** The periods the muscle groups card can show, in days. */
val MUSCLE_PERIODS = listOf(7, 30, 90)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

@Immutable
data class RecordRow(val exercise: ExerciseEntity?, val record: PersonalRecord)

@Immutable
data class TopRow(val exercise: ExerciseEntity?, val top: TopExercise)

/** Everything the Profile tab shows, worked out once off the main thread so the screen only draws. */
@Immutable
data class ProfileStats(
    val totals: ProfileTotals,
    val firstWorkoutAt: Long?,
    val streaks: WeekStreaks,
    val weekCounts: List<Int>,
    val firstWeekStart: Long,
    /** Workouts per day, Monday of the first week first; [today] is the index of today. */
    val heatmap: List<Int>,
    val today: Int,
    val weeklyVolumeKg: List<Double>,
    val muscleGroups: Map<Int, List<Pair<BodyPart, Int>>>,
    val recordCount: Int,
    val recentRecords: List<RecordRow>,
    val topExercises: List<TopRow>,
    val habits: TrainingHabits,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    workouts: WorkoutRepository,
    measurements: MeasurementRepository,
) : ViewModel() {
    /** Null while loading. */
    val stats: StateFlow<ProfileStats?> = workouts.observeHistory()
        .map { profileStats(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Body weight entries, oldest first, for the Profile chart. */
    val bodyWeights: StateFlow<List<MeasurementEntity>> = measurements.measurements
        .map { all -> all.filter { it.kind == MeasurementKind.WEIGHT.key }.reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private fun profileStats(history: List<WorkoutWithExercises>, now: Long, zone: ZoneId): ProfileStats {
    val workouts = history.map { item ->
        StatsWorkout(
            startedAt = item.workout.startedAt,
            endedAt = item.workout.endedAt ?: item.workout.startedAt,
            exercises = item.exercises.map { exercise ->
                StatsExercise(
                    exercise.workoutExercise.exerciseId,
                    exercise.sets.map { StatsSet(it.weightKg, it.reps, it.completed, it.setType == SetType.WARMUP) },
                )
            },
        )
    }
    val exercises = history.flatMap { it.exercises }.mapNotNull { it.exercise }.associateBy { it.id }
    val starts = workouts.map { it.startedAt }
    val thisWeek = startOfWeekMillis(now, zone)
    val heatmapStart = Instant.ofEpochMilli(thisWeek).atZone(zone).toLocalDate().minusWeeks((HEATMAP_WEEKS - 1).toLong())
    val bodyPartSets = history.flatMap { item ->
        item.exercises.flatMap { exercise ->
            exercise.sets.filter { it.completed }.map {
                BodyPartSet(exercise.exercise?.bodyPart.orEmpty(), item.workout.startedAt, it.setType == SetType.WARMUP)
            }
        }
    }
    val records = personalRecords(workouts)
    return ProfileStats(
        totals = profileTotals(workouts),
        firstWorkoutAt = starts.minOrNull(),
        streaks = weekStreaks(starts, now, zone),
        weekCounts = workoutsPerWeek(starts, now, zone, CHART_WEEKS),
        firstWeekStart = Instant.ofEpochMilli(thisWeek).atZone(zone).minusWeeks((CHART_WEEKS - 1).toLong()).toInstant().toEpochMilli(),
        heatmap = workoutsPerDay(starts, now, zone, HEATMAP_WEEKS),
        today = ChronoUnit.DAYS.between(heatmapStart, Instant.ofEpochMilli(now).atZone(zone).toLocalDate()).toInt(),
        weeklyVolumeKg = weeklyVolume(workouts, now, zone, VOLUME_WEEKS),
        muscleGroups = MUSCLE_PERIODS.associateWith { days -> setsPerBodyPart(bodyPartSets, now - days * DAY_MILLIS) },
        recordCount = records.size,
        recentRecords = records.take(RECENT_RECORDS).map { RecordRow(exercises[it.exerciseId], it) },
        topExercises = topExercises(workouts, now, TOP_EXERCISES).map { TopRow(exercises[it.exerciseId], it) },
        habits = trainingHabits(workouts, now, zone),
    )
}
