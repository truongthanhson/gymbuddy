package com.gymbuddy.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Epoch millis of the most recent Monday 00:00 in [zone], for "this week" counts. */
fun startOfWeekMillis(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Finished workouts per week for the Profile chart, oldest week first; the last entry is the week of [nowMillis].
 * Must match shared/test-vectors/workouts_per_week.json (and iOS).
 */
fun workoutsPerWeek(startedAts: List<Long>, nowMillis: Long, zone: ZoneId, weeks: Int): List<Int> {
    fun weekStart(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val current = weekStart(nowMillis)
    val counts = IntArray(weeks)
    for (startedAt in startedAts) {
        val ago = ChronoUnit.WEEKS.between(weekStart(startedAt), current).toInt()
        if (ago in 0 until weeks) counts[weeks - 1 - ago]++
    }
    return counts.toList()
}
