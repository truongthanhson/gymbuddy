package com.gymbuddy.app.data.coach

import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.measurement.MeasurementEntity
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.CoachInsight
import com.gymbuddy.app.domain.MeasurementKind
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.estimatedOneRepMax
import com.gymbuddy.app.domain.formatDuration
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val DAY = 86_400_000L

/**
 * The compact training summary Buddy AI reads: goal profile, the Coach Engine's output,
 * the last 8 weeks of workouts, records and body weight. Weights are written in the person's unit.
 */
class CoachContext(
    private val history: List<WorkoutWithExercises>,
    private val snapshot: CoachSnapshot,
    private val unit: WeightUnit,
    private val vietnamese: Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private fun name(e: ExerciseEntity?): String = when {
        e == null -> "?"
        vietnamese && e.nameVi != null -> e.nameVi
        else -> e.name
    }

    private fun name(id: String): String = snapshot.names[id]?.let { (en, vi) -> if (vietnamese && vi != null) vi else en } ?: id

    private fun date(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    private fun fmt(kg: Double) = unit.format(kg)

    /** One workout as text lines per exercise. */
    fun workout(w: WorkoutWithExercises): JsonObject = buildJsonObject {
        put("date", date(w.workout.startedAt))
        put("name", w.workout.name)
        w.workout.endedAt?.let { put("duration", formatDuration(((it - w.workout.startedAt) / 1000).toInt())) }
        putJsonArray("exercises") {
            for (e in w.exercises) {
                val done = e.sets.filter { it.completed }
                if (done.isEmpty()) continue
                addJsonObject {
                    put("name", name(e.exercise))
                    put("bodyPart", e.exercise?.bodyPart ?: "other")
                    put("sets", done.joinToString(", ") { s ->
                        buildString {
                            when {
                                s.weightKg != null && s.reps != null -> append("${fmt(s.weightKg)} × ${s.reps}")
                                s.reps != null -> append("${s.reps} reps")
                                s.durationSec != null -> append(formatDuration(s.durationSec))
                                s.distanceM != null -> append("${s.distanceM.toInt()} m")
                            }
                            s.rpe?.let { append(" @$it") }
                            if (s.setType != SetType.NORMAL) append(" (${s.setType.name.lowercase()})")
                        }
                    })
                }
            }
        }
    }

    private fun insight(i: CoachInsight): JsonObject = buildJsonObject {
        when (i) {
            is CoachInsight.Inactive -> { put("type", "inactive"); put("days", i.days) }
            is CoachInsight.Deload -> {
                put("type", "deload"); put("exercise", name(i.exerciseId)); put("target", "${fmt(i.weightKg)} × ${i.reps}")
            }
            is CoachInsight.VolumeSpike -> { put("type", "volume_spike"); put("bodyPart", i.bodyPart.key); put("setsThisWeek", i.sets) }
            is CoachInsight.Neglected -> { put("type", "neglected"); put("bodyPart", i.bodyPart.key); put("days", i.days) }
            is CoachInsight.Streak -> { put("type", "streak"); put("weeks", i.weeks) }
        }
    }

    /** Everything but the task-specific part; [extra] adds it (the workout, the week, the library). */
    fun build(
        profile: JsonObject,
        bodyWeights: List<MeasurementEntity>,
        nowMillis: Long,
        extra: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("today", date(nowMillis))
        put("unit", unit.symbol)
        put("profile", profile)
        put("totalWorkouts", history.size)
        val recentIds = history.filter { it.workout.startedAt >= nowMillis - 28 * DAY }
            .flatMap { w -> w.exercises.map { it.workoutExercise.exerciseId } }.toSet()
        putJsonObject("engine") {
            put("insights", JsonArray(snapshot.insights.map(::insight)))
            putJsonArray("recovery") {
                snapshot.recovery.forEach { r ->
                    addJsonObject { put("bodyPart", r.bodyPart.key); put("hoursSince", r.hoursSince); put("status", r.status.name.lowercase()) }
                }
            }
            putJsonArray("targets") {
                snapshot.targets.filterKeys { it in recentIds }.forEach { (id, t) ->
                    addJsonObject { put("exercise", name(id)); put("target", "${fmt(t.weightKg)} × ${t.reps}"); put("kind", t.kind.name.lowercase()) }
                }
            }
        }
        val recent = history.filter { it.workout.startedAt >= nowMillis - 56 * DAY }.sortedByDescending { it.workout.startedAt }.take(40)
        put("recentWorkouts", JsonArray(recent.map(::workout)))
        put("records", records())
        putJsonArray("bodyWeight") {
            bodyWeights.filter { it.kind == MeasurementKind.WEIGHT.key && it.deletedAt == null }
                .sortedByDescending { it.measuredAt }.take(10)
                .forEach { m -> addJsonObject { put("date", date(m.measuredAt)); put("weight", fmt(m.value)) } }
        }
        extra()
    }

    /** Working sets per body part of the workouts started in [from, to). */
    private fun setsByBodyPart(from: Long, to: Long): JsonObject = buildJsonObject {
        val counts = sortedMapOf<String, Int>()
        for (w in history) {
            if (w.workout.startedAt < from || w.workout.startedAt >= to) continue
            for (e in w.exercises) {
                val n = e.sets.count { it.completed && it.setType != SetType.WARMUP }
                if (n > 0) counts.merge(e.exercise?.bodyPart ?: "other", n, Int::plus)
            }
        }
        counts.forEach { (part, n) -> put(part, n) }
    }

    /** The week a weekly report covers, [from, to), with the week before for comparison. */
    fun week(from: Long, to: Long): JsonObject = buildJsonObject {
        put("from", date(from))
        put("to", date(to - 1))
        val inWeek = history.filter { it.workout.startedAt in from until to }.sortedBy { it.workout.startedAt }
        put("workouts", JsonArray(inWeek.map(::workout)))
        put("setsByBodyPart", setsByBodyPart(from, to))
        putJsonObject("weekBefore") {
            val span = to - from
            put("workoutCount", history.count { it.workout.startedAt in (from - span) until from })
            put("setsByBodyPart", setsByBodyPart(from - span, from))
        }
    }

    /** Best estimated 1RM per exercise for the 15 exercises done most often. */
    private fun records(): JsonArray {
        data class Best(var sessions: Int = 0, var e1rm: Double = 0.0, var set: String = "", var date: String = "")
        val bests = linkedMapOf<String, Best>()
        for (w in history) {
            for (e in w.exercises) {
                val sets = e.sets.filter { it.completed && it.setType != SetType.WARMUP && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
                if (sets.isEmpty()) continue
                val best = bests.getOrPut(name(e.exercise)) { Best() }
                best.sessions++
                for (s in sets) {
                    val value = estimatedOneRepMax(s.weightKg!!, s.reps!!)
                    if (value > best.e1rm) {
                        best.e1rm = value
                        best.set = "${fmt(s.weightKg)} × ${s.reps}"
                        best.date = date(w.workout.startedAt)
                    }
                }
            }
        }
        return buildJsonArray {
            bests.entries.sortedByDescending { it.value.sessions }.take(15).forEach { (exercise, b) ->
                addJsonObject { put("exercise", exercise); put("bestSet", b.set); put("estimated1RM", fmt(b.e1rm)); put("date", b.date) }
            }
        }
    }
}

/** The library Buddy may pick from when designing a routine. */
fun exerciseLibrary(exercises: List<ExerciseEntity>, vietnamese: Boolean): JsonArray = buildJsonArray {
    exercises.filter { it.deletedAt == null }.forEach { e ->
        addJsonObject {
            put("id", e.id)
            put("name", if (vietnamese && e.nameVi != null) e.nameVi else e.name)
            put("bodyPart", e.bodyPart)
            put("category", e.category)
        }
    }
}
