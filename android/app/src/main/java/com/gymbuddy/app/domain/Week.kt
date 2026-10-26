package com.gymbuddy.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Epoch millis of the most recent Monday 00:00 in [zone], for "this week" counts. */
fun startOfWeekMillis(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone).toInstant().toEpochMilli()
