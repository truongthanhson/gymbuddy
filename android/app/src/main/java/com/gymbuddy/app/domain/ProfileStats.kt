package com.gymbuddy.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The Profile tab's statistics. Everything here must match shared/test-vectors/profile_stats.json (and iOS).
 * Weeks start on Monday; times are epoch millis.
 */
data class StatsSet(val weightKg: Double?, val reps: Int?, val completed: Boolean, val warmup: Boolean)

data class StatsExercise(val exerciseId: String, val sets: List<StatsSet>)

/** A finished workout, reduced to what the stats need. */
data class StatsWorkout(val startedAt: Long, val endedAt: Long, val exercises: List<StatsExercise>)

data class ProfileTotals(val workouts: Int, val durationSeconds: Long, val volumeKg: Double, val sets: Int)

/** Weeks in a row with at least one workout. [current] still counts when this week has none yet. */
data class WeekStreaks(val current: Int, val best: Int)

data class TrainingHabits(
    val averageDurationSeconds: Long,
    /** 1 = Monday … 7 = Sunday, the day with the most workouts (the earlier day on a tie); null without workouts. */
    val favoriteWeekday: Int?,
    /** Average rest days between training days over the last [HABIT_DAYS] days; null with fewer than two. */
    val averageRestDays: Double?,
)

/** A workout in which an exercise's best estimated 1RM beat every earlier workout of it. */
data class PersonalRecord(
    val exerciseId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
    val estimatedOneRepMax: Double,
)

data class TopExercise(
    val exerciseId: String,
    val workouts: Int,
    /** Best estimated 1RM in the last [TREND_DAYS] days, and in the [TREND_DAYS] before; null when not trained then. */
    val recentOneRepMax: Double?,
    val previousOneRepMax: Double?,
)

const val HABIT_DAYS = 90
const val TREND_DAYS = 90
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun StatsSet.counts() = completed && !warmup && (reps ?: 0) > 0

private fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Volume counts every completed set, like the history cards; the set count leaves warm-ups out. */
fun profileTotals(workouts: List<StatsWorkout>): ProfileTotals {
    var duration = 0L
    var volume = 0.0
    var sets = 0
    for (workout in workouts) {
        duration += ((workout.endedAt - workout.startedAt) / 1000).coerceAtLeast(0)
        for (exercise in workout.exercises) {
            for (set in exercise.sets) {
                if (!set.completed) continue
                volume += (set.weightKg ?: 0.0) * (set.reps ?: 0)
                if (!set.warmup) sets++
            }
        }
    }
    return ProfileTotals(workouts.size, duration, volume, sets)
}

fun weekStreaks(startedAts: List<Long>, nowMillis: Long, zone: ZoneId): WeekStreaks {
    val current = monday(localDate(nowMillis, zone))
    // Whole weeks before this one; future workouts are left out.
    val weeksAgo = startedAts.map { ChronoUnit.WEEKS.between(monday(localDate(it, zone)), current) }
        .filter { it >= 0 }.toSortedSet()
    if (weeksAgo.isEmpty()) return WeekStreaks(0, 0)

    var best = 0
    var run = 0
    var previous: Long? = null
    for (ago in weeksAgo) {
        run = if (previous != null && ago - previous == 1L) run + 1 else 1
        best = maxOf(best, run)
        previous = ago
    }
    var streak = 0
    var week = if (0L in weeksAgo) 0L else 1L
    while (week in weeksAgo) {
        streak++
        week++
    }
    return WeekStreaks(streak, best)
}

/** Workouts per day for a [weeks]-week heatmap, Monday of the first week first; days after today are 0. */
fun workoutsPerDay(startedAts: List<Long>, nowMillis: Long, zone: ZoneId, weeks: Int): List<Int> {
    val first = monday(localDate(nowMillis, zone)).minusWeeks((weeks - 1).toLong())
    val counts = IntArray(weeks * 7)
    for (startedAt in startedAts) {
        val index = ChronoUnit.DAYS.between(first, localDate(startedAt, zone))
        if (index in 0 until counts.size) counts[index.toInt()]++
    }
    return counts.toList()
}

/** Volume in kg per week, oldest first; the last entry is this week. */
fun weeklyVolume(workouts: List<StatsWorkout>, nowMillis: Long, zone: ZoneId, weeks: Int): List<Double> {
    val current = monday(localDate(nowMillis, zone))
    val volumes = DoubleArray(weeks)
    for (workout in workouts) {
        val ago = ChronoUnit.WEEKS.between(monday(localDate(workout.startedAt, zone)), current)
        if (ago !in 0 until weeks) continue
        volumes[weeks - 1 - ago.toInt()] += profileTotals(listOf(workout)).volumeKg
    }
    return volumes.toList()
}

fun trainingHabits(workouts: List<StatsWorkout>, nowMillis: Long, zone: ZoneId): TrainingHabits {
    if (workouts.isEmpty()) return TrainingHabits(0, null, null)
    val average = profileTotals(workouts).durationSeconds / workouts.size
    val perWeekday = IntArray(7)
    workouts.forEach { perWeekday[localDate(it.startedAt, zone).dayOfWeek.value - 1]++ }
    val favorite = perWeekday.indices.maxBy { perWeekday[it] } + 1

    val since = localDate(nowMillis, zone).minusDays(HABIT_DAYS.toLong())
    val days = workouts.map { localDate(it.startedAt, zone) }.filter { it > since }.toSortedSet().toList()
    val rest = if (days.size < 2) {
        null
    } else {
        days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) - 1 }.average()
    }
    return TrainingHabits(average, favorite, rest)
}

/** Every estimated-1RM record, newest first. The first workout of an exercise sets its baseline, not a record. */
fun personalRecords(workouts: List<StatsWorkout>): List<PersonalRecord> {
    val best = mutableMapOf<String, Double>()
    val records = mutableListOf<PersonalRecord>()
    for (workout in workouts.sortedBy { it.startedAt }) {
        for (exercise in workout.exercises) {
            var top: StatsSet? = null
            var topValue = 0.0
            for (set in exercise.sets) {
                val weight = set.weightKg ?: 0.0
                if (!set.counts() || weight <= 0) continue
                val value = estimatedOneRepMax(weight, set.reps!!)
                if (value > topValue) {
                    top = set
                    topValue = value
                }
            }
            if (top == null) continue
            val previous = best[exercise.exerciseId]
            if (previous != null && topValue > previous) {
                records += PersonalRecord(exercise.exerciseId, workout.startedAt, top.weightKg!!, top.reps!!, topValue)
            }
            if (previous == null || topValue > previous) best[exercise.exerciseId] = topValue
        }
    }
    return records.reversed()
}

/** The [limit] exercises in the most workouts (with a finished working set), most first, ties by id. */
fun topExercises(workouts: List<StatsWorkout>, nowMillis: Long, limit: Int): List<TopExercise> {
    val counts = mutableMapOf<String, Int>()
    val recent = mutableMapOf<String, Double>()
    val previous = mutableMapOf<String, Double>()
    val recentFrom = nowMillis - TREND_DAYS * DAY_MILLIS
    val previousFrom = recentFrom - TREND_DAYS * DAY_MILLIS
    for (workout in workouts) {
        for (exerciseId in workout.exercises.filter { e -> e.sets.any { it.counts() } }.map { it.exerciseId }.distinct()) {
            counts[exerciseId] = (counts[exerciseId] ?: 0) + 1
        }
        val bucket = when {
            workout.startedAt >= recentFrom -> recent
            workout.startedAt >= previousFrom -> previous
            else -> continue
        }
        for (exercise in workout.exercises) {
            val top = exercise.sets.filter { it.counts() && (it.weightKg ?: 0.0) > 0 }
                .maxOfOrNull { estimatedOneRepMax(it.weightKg!!, it.reps!!) } ?: continue
            bucket[exercise.exerciseId] = maxOf(bucket[exercise.exerciseId] ?: 0.0, top)
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { TopExercise(it.key, it.value, recent[it.key], previous[it.key]) }
}
