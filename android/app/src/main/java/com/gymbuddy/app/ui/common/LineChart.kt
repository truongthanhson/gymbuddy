package com.gymbuddy.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A simple time-series line: x is the time of each point, y is scaled between the smallest and largest value.
 * [points] are (epoch millis, value), oldest first; labels come from [formatValue] and [formatTime].
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minValue = points.minOf { it.second }
    val maxValue = points.maxOf { it.second }
    val firstTime = points.first().first
    val lastTime = points.last().first
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Text(formatValue(maxValue), style = labelStyle, color = labelColor)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val valueSpan = (maxValue - minValue).takeIf { it > 0 } ?: 1.0
            val timeSpan = (lastTime - firstTime).takeIf { it > 0 } ?: 1L
            val pad = 6.dp.toPx()
            fun offset(point: Pair<Long, Double>): Offset {
                val x = if (points.size == 1) size.width / 2 else ((point.first - firstTime).toFloat() / timeSpan) * (size.width - 2 * pad) + pad
                val y = size.height - pad - ((point.second - minValue) / valueSpan).toFloat() * (size.height - 2 * pad)
                return Offset(x, y)
            }
            drawLine(grid, Offset(0f, pad), Offset(size.width, pad), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height - pad), Offset(size.width, size.height - pad), strokeWidth = 1.dp.toPx())
            val path = Path()
            points.forEachIndexed { index, point ->
                val o = offset(point)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
            points.forEach { drawCircle(line, radius = 4.dp.toPx(), center = offset(it)) }
        }
        Text(formatValue(minValue), style = labelStyle, color = labelColor)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(firstTime), style = labelStyle, color = labelColor)
            if (points.size > 1) Text(formatTime(lastTime), style = labelStyle, color = labelColor)
        }
    }
}
