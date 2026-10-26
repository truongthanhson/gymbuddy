package com.gymbuddy.app.data.coach

import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.BodyPartSet
import com.gymbuddy.app.domain.CoachExercise
import com.gymbuddy.app.domain.CoachInsight
import com.gymbuddy.app.domain.CoachSession
import com.gymbuddy.app.domain.CoachSet
import com.gymbuddy.app.domain.Recovery
import com.gymbuddy.app.domain.SetTarget
import com.gymbuddy.app.domain.coachInsights
import com.gymbuddy.app.domain.nextTarget
import com.gymbuddy.app.domain.recovery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** What Buddy knows right now, from the finished workouts. */
data class CoachSnapshot(
    val insights: List<CoachInsight> = emptyList(),
    val recovery: List<Recovery> = emptyList(),
    /** Next target per exercise id. */
    val targets: Map<String, SetTarget> = emptyMap(),
    /** Exercise names (English, Vietnamese) per exercise id, for the cards. */
    val names: Map<String, Pair<String, String?>> = emptyMap(),
    val workoutCount: Int = 0,
)

/** Default weekly goal until the goal profile lets people pick one. */
const val DEFAULT_WORKOUTS_PER_WEEK = 3

@Singleton
class CoachRepository @Inject constructor(private val workouts: WorkoutDao) {

    fun observe(goalPerWeek: Int = DEFAULT_WORKOUTS_PER_WEEK): Flow<CoachSnapshot> =
        workouts.observeHistory()
            .map { snapshot(it, goalPerWeek, System.currentTimeMillis()) }
            .flowOn(Dispatchers.Default)

    companion object {
        fun snapshot(history: List<WorkoutWithExercises>, goalPerWeek: Int, nowMillis: Long): CoachSnapshot {
            val exercises = mutableMapOf<String, MutableList<CoachSession>>()
            val info = mutableMapOf<String, Triple<String, String, Pair<String, String?>>>()
            val partSets = mutableListOf<BodyPartSet>()
            for (w in history) {
                for (e in w.exercises) {
                    val exercise = e.exercise ?: continue
                    val done = e.sets.filter { it.completed }
                    if (done.isEmpty()) continue
                    info[exercise.id] = Triple(exercise.bodyPart, exercise.category, exercise.name to exercise.nameVi)
                    exercises.getOrPut(exercise.id) { mutableListOf() } += CoachSession(
                        w.workout.startedAt,
                        done.map { CoachSet(it.weightKg, it.reps, it.rpe, it.setType == SetType.WARMUP) },
                    )
                    done.forEach { partSets += BodyPartSet(exercise.bodyPart, w.workout.startedAt, it.setType == SetType.WARMUP) }
                }
            }
            val coachExercises = exercises.map { (id, sessions) ->
                val (bodyPart, category) = info.getValue(id)
                CoachExercise(id, bodyPart, category, sessions)
            }
            return CoachSnapshot(
                insights = coachInsights(coachExercises, partSets, history.map { it.workout.startedAt }, goalPerWeek, nowMillis),
                recovery = recovery(partSets, nowMillis),
                targets = coachExercises.mapNotNull { e -> nextTarget(e.sessions, e.bodyPart, e.category)?.let { e.exerciseId to it } }.toMap(),
                names = info.mapValues { it.value.third },
                workoutCount = history.size,
            )
        }
    }
}
