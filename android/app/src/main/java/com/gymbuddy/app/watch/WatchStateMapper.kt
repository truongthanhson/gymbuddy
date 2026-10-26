package com.gymbuddy.app.watch

import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.watchlink.WatchExercise
import com.gymbuddy.watchlink.WatchSet
import com.gymbuddy.watchlink.WatchState
import com.gymbuddy.watchlink.WatchWorkout
import com.gymbuddy.watchlink.setLabels

/**
 * The active workout as the watch sees it. [previous] holds the last session's sets per exercise id,
 * used as hints for sets not filled in yet, like the "Previous" column on the phone.
 */
fun buildWatchState(
    workout: WorkoutWithExercises?,
    previous: Map<String, List<WorkoutSetEntity>>,
    unit: WeightUnit,
    restEndsAt: Long?,
    vietnamese: Boolean,
): WatchState {
    if (workout == null) return WatchState(null)
    fun weight(kg: Double?) = kg?.let { formatWeight(unit.display(it)) } ?: ""
    return WatchState(
        WatchWorkout(
            name = workout.workout.name,
            startedAt = workout.workout.startedAt,
            restEndsAt = restEndsAt,
            exercises = workout.exercises.map { item ->
                val sets = item.sets
                val labels = setLabels(sets.map { it.setType.name.lowercase() })
                val hints = previous[item.workoutExercise.exerciseId].orEmpty()
                WatchExercise(
                    name = item.exercise.displayName(vietnamese),
                    sets = sets.mapIndexed { index, set ->
                        val hint = hints.getOrNull(index)
                        WatchSet(
                            id = set.id,
                            label = labels[index],
                            weight = weight(set.weightKg),
                            reps = set.reps?.toString() ?: "",
                            unit = unit.symbol,
                            done = set.completed,
                            hintWeight = hint?.weightKg?.let(::weight),
                            hintReps = hint?.reps?.toString(),
                        )
                    },
                )
            },
        ),
    )
}
