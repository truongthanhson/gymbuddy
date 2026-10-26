package com.gymbuddy.app.domain

import kotlin.math.floor
import kotlin.math.round

// The Coach Engine: Buddy's rule-based advice. Must match shared/coach-rules.md,
// shared/test-vectors/coach.json and the iOS implementation.

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

/** One logged set as the coach sees it. */
data class CoachSet(val weightKg: Double?, val reps: Int?, val rpe: Double?, val warmup: Boolean)

/** One exercise in one finished workout. */
data class CoachSession(val startedAt: Long, val sets: List<CoachSet>)

/** An exercise with its sessions, for [coachInsights]. */
data class CoachExercise(val exerciseId: String, val bodyPart: String, val category: String, val sessions: List<CoachSession>)

enum class TargetKind { INCREASE, REPEAT, DELOAD }

data class SetTarget(val kind: TargetKind, val weightKg: Double, val reps: Int)

private val weightedCategories = setOf(
    ExerciseCategory.BARBELL.key,
    ExerciseCategory.DUMBBELL.key,
    ExerciseCategory.MACHINE.key,
    ExerciseCategory.CABLE.key,
    ExerciseCategory.WEIGHTED_BODYWEIGHT.key,
)

private fun CoachSession.workingSets(): List<CoachSet> =
    sets.filter { !it.warmup && (it.reps ?: 0) > 0 && (it.weightKg ?: 0.0) > 0.0 }

private fun List<CoachSession>.withWorkingSets(): List<CoachSession> =
    filter { it.workingSets().isNotEmpty() }.sortedBy { it.startedAt }

private fun repRange(topReps: Int): IntRange = when {
    topReps <= 6 -> 4..6
    topReps <= 12 -> 8..12
    else -> 12..20
}

/** Weight jump for one progression step, in kg. */
fun coachStep(bodyPart: String, category: String): Double = when {
    category == ExerciseCategory.DUMBBELL.key -> 2.0
    bodyPart == BodyPart.LEGS.key -> 5.0
    else -> 2.5
}

private fun round2(x: Double) = round(x * 100) / 100

private fun floorToStep(x: Double, step: Double) = round2(floor(x / step + 1e-9) * step)

/** Fatigued (RPE 9+ twice in a row) or no new estimated 1RM in the last 3 of at least 4 sessions. */
fun isStalled(sessions: List<CoachSession>): Boolean {
    val ss = sessions.withWorkingSets()
    if (ss.size >= 2) {
        val fatigued = ss.takeLast(2).all { s ->
            val rpes = s.workingSets().mapNotNull { it.rpe }
            rpes.isNotEmpty() && rpes.average() >= 9.0
        }
        if (fatigued) return true
    }
    if (ss.size < 4) return false
    fun best(s: CoachSession) = s.workingSets().maxOf { estimatedOneRepMax(it.weightKg!!, it.reps!!) }
    return ss.takeLast(3).maxOf(::best) <= ss.dropLast(3).maxOf(::best) + 1e-6
}

/** What to aim for next time, or null when the exercise isn't logged with weight and reps. */
fun nextTarget(sessions: List<CoachSession>, bodyPart: String, category: String): SetTarget? {
    if (category !in weightedCategories) return null
    val last = sessions.withWorkingSets().lastOrNull() ?: return null
    val working = last.workingSets()
    val top = working.maxOf { it.weightKg!! }
    val topReps = working.filter { it.weightKg == top }.map { it.reps!! }
    val range = repRange(topReps.max())
    val minReps = topReps.min()
    val step = coachStep(bodyPart, category)
    return when {
        isStalled(sessions) -> SetTarget(TargetKind.DELOAD, floorToStep(top * 0.9, step), minReps)
        minReps >= range.last -> SetTarget(TargetKind.INCREASE, round2(top + step), range.first)
        else -> SetTarget(TargetKind.REPEAT, top, minOf(range.last, minReps + 1))
    }
}

enum class RecoveryStatus { RECOVERING, READY }

data class Recovery(val bodyPart: BodyPart, val hoursSince: Long, val status: RecoveryStatus)

private val coachedParts = BodyPart.entries - BodyPart.CARDIO - BodyPart.OTHER

/** Hours since each body part was last trained in the past 28 days, most rested first. */
fun recovery(sets: List<BodyPartSet>, nowMillis: Long): List<Recovery> {
    val last = mutableMapOf<BodyPart, Long>()
    for (s in sets) {
        val part = BodyPart.fromKey(s.bodyPart)
        if (s.warmup || part !in coachedParts || s.startedAt > nowMillis || s.startedAt < nowMillis - 28 * DAY) continue
        last[part] = maxOf(last[part] ?: Long.MIN_VALUE, s.startedAt)
    }
    return last.map { (part, at) ->
        val since = nowMillis - at
        Recovery(part, since / HOUR, if (since < 48 * HOUR) RecoveryStatus.RECOVERING else RecoveryStatus.READY)
    }.sortedWith(compareByDescending<Recovery> { it.hoursSince }.thenBy { it.bodyPart.ordinal })
}

sealed interface CoachInsight {
    data class Inactive(val days: Long) : CoachInsight
    data class Deload(val exerciseId: String, val weightKg: Double, val reps: Int) : CoachInsight
    data class VolumeSpike(val bodyPart: BodyPart, val sets: Int) : CoachInsight
    data class Neglected(val bodyPart: BodyPart, val days: Long) : CoachInsight
    data class Streak(val weeks: Int) : CoachInsight
}

/** Body parts trained much more this week than last ([CoachInsight.VolumeSpike]) or dropped for two weeks ([CoachInsight.Neglected]). */
fun volumeInsights(sets: List<BodyPartSet>, nowMillis: Long): List<CoachInsight> {
    val spikes = mutableListOf<CoachInsight>()
    val neglected = mutableListOf<CoachInsight>()
    for (part in coachedParts) {
        val mine = sets.filter { !it.warmup && BodyPart.fromKey(it.bodyPart) == part }
        val current = mine.count { it.startedAt > nowMillis - 7 * DAY && it.startedAt <= nowMillis }
        val previous = mine.count { it.startedAt > nowMillis - 14 * DAY && it.startedAt <= nowMillis - 7 * DAY }
        val older = mine.count { it.startedAt > nowMillis - 42 * DAY && it.startedAt <= nowMillis - 14 * DAY }
        if (previous >= 6 && current > previous * 1.3) {
            spikes += CoachInsight.VolumeSpike(part, current)
        } else if (current + previous == 0 && older >= 6) {
            val latest = mine.filter { it.startedAt <= nowMillis }.maxOf { it.startedAt }
            neglected += CoachInsight.Neglected(part, (nowMillis - latest) / DAY)
        }
    }
    return spikes + neglected
}

/** Weeks in a row (7-day blocks back from now) with at least [goalPerWeek] workouts. */
fun streakWeeks(workoutStarts: List<Long>, nowMillis: Long, goalPerWeek: Int): Int {
    fun count(i: Int) = workoutStarts.count { it > nowMillis - 7 * (i + 1) * DAY && it <= nowMillis - 7 * i * DAY }
    var i = if (count(0) >= goalPerWeek) 0 else 1
    var weeks = 0
    while (count(i) >= goalPerWeek) {
        weeks++
        i++
    }
    return weeks
}

/** Whole days without a workout before Buddy reminds the person; see shared/coach-rules.md. */
fun reminderGapDays(daysPerWeek: Int): Int = maxOf(2, 7 / daysPerWeek.coerceIn(1, 7) + 1)

/** When the training reminder is due, or null before the first workout. */
fun reminderDueAt(lastWorkoutMillis: Long?, daysPerWeek: Int): Long? =
    lastWorkoutMillis?.let { it + reminderGapDays(daysPerWeek) * DAY }

/** Everything Buddy has to say, most urgent first. */
fun coachInsights(
    exercises: List<CoachExercise>,
    bodyPartSets: List<BodyPartSet>,
    workoutStarts: List<Long>,
    goalPerWeek: Int,
    nowMillis: Long,
): List<CoachInsight> {
    val out = mutableListOf<CoachInsight>()
    val starts = workoutStarts.filter { it <= nowMillis }
    starts.maxOrNull()?.let { latest ->
        val days = (nowMillis - latest) / DAY
        if (days >= 7) out += CoachInsight.Inactive(days)
    }
    exercises.mapNotNull { e ->
        val target = nextTarget(e.sessions, e.bodyPart, e.category) ?: return@mapNotNull null
        val latest = e.sessions.maxOf { it.startedAt }
        if (target.kind == TargetKind.DELOAD && nowMillis - latest <= 14 * DAY) latest to CoachInsight.Deload(e.exerciseId, target.weightKg, target.reps) else null
    }.sortedByDescending { it.first }.forEach { out += it.second }
    out += volumeInsights(bodyPartSets, nowMillis)
    val weeks = streakWeeks(starts, nowMillis, goalPerWeek)
    if (weeks >= 2) out += CoachInsight.Streak(weeks)
    return out
}
