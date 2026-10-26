package com.gymbuddy.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun millis(year: Int, month: Int, day: Int, hour: Int) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun weekStartsOnMonday() {
        // 2026-10-26 is a Monday; 2026-11-01 the Sunday after it.
        assertEquals(millis(2026, 10, 26, 0), startOfWeekMillis(millis(2026, 10, 26, 7), zone))
        assertEquals(millis(2026, 10, 26, 0), startOfWeekMillis(millis(2026, 11, 1, 23), zone))
        assertEquals(millis(2026, 10, 19, 0), startOfWeekMillis(millis(2026, 10, 25, 12), zone))
    }
}
