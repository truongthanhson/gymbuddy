package com.gymbuddy.app.domain

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Time ranges a chart can zoom to, counted back from its newest point (not from today), so old data still fills the chart.
 * Must match shared/test-vectors/chart_range.json (and iOS).
 */
enum class ChartRange(val code: String, val days: Int?) {
    OneMonth("1M", 30),
    ThreeMonths("3M", 91),
    SixMonths("6M", 182),
    OneYear("1Y", 365),
    All("ALL", null),
    ;

    /** The points of [points] (oldest first, x = epoch millis) inside this range. */
    fun <T> filter(points: List<T>, time: (T) -> Long): List<T> {
        if (days == null || points.isEmpty()) return points
        val cutoff = time(points.last()) - days * DAY_MILLIS
        return points.filter { time(it) >= cutoff }
    }

    companion object {
        /** The ranges worth offering: each keeps at least two points and fewer than all of them; [All] is always there. */
        fun <T> available(points: List<T>, time: (T) -> Long): List<ChartRange> =
            entries.filter { it == All || it.filter(points, time).size in 2 until points.size }
    }
}

/** Index of the point closest to [x] in [xs] (sorted ascending), the earlier one on a tie; -1 when empty. */
fun nearestIndex(xs: List<Float>, x: Float): Int {
    if (xs.isEmpty()) return -1
    var best = 0
    for (i in 1 until xs.size) {
        if (kotlin.math.abs(xs[i] - x) < kotlin.math.abs(xs[best] - x)) best = i
    }
    return best
}
