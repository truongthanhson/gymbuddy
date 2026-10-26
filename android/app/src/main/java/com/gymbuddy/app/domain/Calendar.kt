package com.gymbuddy.app.domain

import java.time.YearMonth

/** A month of the history calendar: [leading] empty cells, then days 1 to [days]. */
data class MonthGrid(val leading: Int, val days: Int) {
    val weeks: Int get() = (leading + days + 6) / 7
}

/** [firstDayOfWeek] is an ISO weekday, 1 = Monday to 7 = Sunday. */
fun monthGrid(year: Int, month: Int, firstDayOfWeek: Int): MonthGrid {
    val yearMonth = YearMonth.of(year, month)
    val first = yearMonth.atDay(1).dayOfWeek.value
    return MonthGrid(leading = (first - firstDayOfWeek + 7) % 7, days = yearMonth.lengthOfMonth())
}
