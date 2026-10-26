package com.gymbuddy.app.ui.charts

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.ChartRange
import com.gymbuddy.app.domain.nearestIndex
import kotlin.math.abs

@StringRes
private fun ChartRange.label(): Int = when (this) {
    ChartRange.OneMonth -> R.string.chart_range_1m
    ChartRange.ThreeMonths -> R.string.chart_range_3m
    ChartRange.SixMonths -> R.string.chart_range_6m
    ChartRange.OneYear -> R.string.chart_range_1y
    ChartRange.All -> R.string.chart_range_all
}

/**
 * A time-series line that draws itself in, follows the finger with a value bubble, and zooms to 1M/3M/6M/1Y.
 * [points] are (epoch millis, value), oldest first. The header shows the touched value (or the latest one) and the
 * change since the start of the range, coloured by [higherIsBetter] (null keeps it neutral, e.g. body weight).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveLineChart(
    points: List<Pair<Long, Double>>,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
    higherIsBetter: Boolean? = null,
    showRanges: Boolean = true,
) {
    if (points.isEmpty()) return
    val ranges = remember(points) { ChartRange.available(points) { it.first } }
    var chosenRange by rememberSaveable { mutableStateOf(ChartRange.All) }
    val range = if (chosenRange in ranges) chosenRange else ChartRange.All
    val shown = remember(points, range) { range.filter(points) { it.first } }
    var selected by remember(shown) { mutableStateOf<Int?>(null) }

    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = colors.inverseOnSurface)
    val tabular = TextStyle(fontFeatureSettings = "tnum")

    // Draw-in: the line sweeps left to right whenever the data or the range changes.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    // The value axis glides to the new bounds instead of jumping.
    val dataMin = shown.minOf { it.second }
    val dataMax = shown.maxOf { it.second }
    val span = (dataMax - dataMin).takeIf { it > 0 } ?: (abs(dataMax).takeIf { it > 0 } ?: 1.0)
    val low by animateFloatAsState((dataMin - span * 0.12).toFloat(), spring(stiffness = Spring.StiffnessLow), label = "low")
    val high by animateFloatAsState((dataMax + span * 0.12).toFloat(), spring(stiffness = Spring.StiffnessLow), label = "high")

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val padX = with(density) { 8.dp.toPx() }
    val padTop = with(density) { 8.dp.toPx() }
    val padBottom = with(density) { 8.dp.toPx() }
    val firstTime = shown.first().first
    val timeSpan = (shown.last().first - firstTime).takeIf { it > 0 } ?: 1L
    fun xOf(time: Long): Float =
        if (shown.size == 1) canvasSize.width / 2f
        else padX + (time - firstTime).toFloat() / timeSpan * (canvasSize.width - 2 * padX)
    fun yOf(value: Double): Float {
        val h = canvasSize.height - padTop - padBottom
        return padTop + h - ((value.toFloat() - low) / (high - low).coerceAtLeast(1e-6f)) * h
    }
    val xs = shown.map { xOf(it.first) }

    val focusIndex = selected ?: shown.lastIndex
    val focus = shown[focusIndex]
    val start = shown.first()
    val delta = focus.second - start.second
    // With nothing selected the marker rests on the newest point, so a first touch glides from there.
    val selectionX by animateFloatAsState(xs[focusIndex], spring(stiffness = Spring.StiffnessMediumLow), label = "selX")
    val selectionY by animateFloatAsState(yOf(focus.second), spring(stiffness = Spring.StiffnessMediumLow), label = "selY")
    val selectionAlpha by animateFloatAsState(if (selected != null) 1f else 0f, tween(200), label = "selAlpha")
    var beforeDown by remember { mutableStateOf<Int?>(null) }
    val description = stringResource(
        R.string.chart_description,
        formatTime(shown.first().first),
        formatTime(shown.last().first),
        formatValue(shown.last().second),
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(
                formatValue(focus.second),
                style = MaterialTheme.typography.headlineMedium.merge(tabular),
                fontWeight = FontWeight.SemiBold,
            )
            val deltaColor = when {
                higherIsBetter == null || delta == 0.0 -> colors.onSurfaceVariant
                (delta > 0) == higherIsBetter -> successColor(colors.surface)
                else -> colors.error
            }
            Text(
                buildAnnotatedString {
                    append(formatTime(focus.first))
                    if (focusIndex > 0) {
                        append("  ")
                        withStyle(SpanStyle(color = deltaColor, fontWeight = FontWeight.Medium)) {
                            append(if (delta > 0) "▲ " else if (delta < 0) "▼ " else "")
                            append(formatValue(abs(delta)))
                        }
                        append(" ")
                        append(stringResource(R.string.chart_since, formatTime(start.first)))
                    }
                },
                style = MaterialTheme.typography.bodySmall.merge(tabular),
                color = colors.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = description }
                .chartScrubbing(
                    // The positions depend on the size, so a resize restarts the handler with fresh ones.
                    key = shown to canvasSize,
                    onDown = { beforeDown = selected },
                    onScrub = { x ->
                        val index = nearestIndex(xs, x)
                        if (index != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selected = index
                        }
                    },
                    onTap = { if (beforeDown == selected) selected = null },
                    onCancel = { selected = beforeDown },
                ),
        ) {
            val grid = colors.outlineVariant
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            // Gridlines at the smallest, middle and largest value, labelled at the left edge.
            for (value in listOf(dataMax, (dataMin + dataMax) / 2, dataMin).distinct()) {
                val y = yOf(value)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val text = measurer.measure(AnnotatedString(formatValue(value)), labelStyle)
                drawText(text, topLeft = Offset(0f, (y - text.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }

            val offsets = shown.mapIndexed { i, p -> Offset(xs[i], yOf(p.second)) }
            val path = Path().apply {
                moveTo(offsets[0].x, offsets[0].y)
                for (i in 1 until offsets.size) {
                    val a = offsets[i - 1]
                    val b = offsets[i]
                    val mid = (a.x + b.x) / 2
                    // Control points level with each end keep the curve from overshooting the data.
                    cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                }
            }
            val revealX = padX + reveal.value * (size.width - padX)
            clipRect(right = revealX) {
                if (offsets.size > 1) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(offsets.last().x, size.height)
                        lineTo(offsets.first().x, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
                }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            offsets.forEach { o ->
                // Each dot pops in as the sweep passes it.
                val grow = ((revealX - o.x) / 24.dp.toPx()).coerceIn(0f, 1f)
                if (grow > 0f && offsets.size <= 60) {
                    drawCircle(colors.surface, radius = 4.5.dp.toPx() * grow, center = o)
                    drawCircle(line, radius = 3.dp.toPx() * grow, center = o)
                }
            }

            if (selectionAlpha > 0f) {
                val index = selected ?: shown.lastIndex
                val point = Offset(selectionX, selectionY)
                drawLine(
                    colors.onSurfaceVariant.copy(alpha = 0.5f * selectionAlpha),
                    Offset(point.x, 0f),
                    Offset(point.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(line.copy(alpha = 0.22f * selectionAlpha), radius = 12.dp.toPx(), center = point)
                drawCircle(colors.surface.copy(alpha = selectionAlpha), radius = 6.dp.toPx(), center = point)
                drawCircle(line.copy(alpha = selectionAlpha), radius = 4.dp.toPx(), center = point)
                val text = measurer.measure(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(formatValue(shown[index].second)) }
                        append("\n")
                        append(formatTime(shown[index].first))
                    },
                    tooltipStyle.merge(tabular),
                )
                drawTooltip(text, point, colors.inverseSurface.copy(alpha = 0.92f * selectionAlpha))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown.first().first), style = labelStyle)
            if (shown.size > 1) Text(formatTime(shown.last().first), style = labelStyle)
        }
        if (showRanges && ranges.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { r ->
                    FilterChip(selected = range == r, onClick = { chosenRange = r }, label = { Text(stringResource(r.label())) })
                }
            }
        }
    }
}

/** Green for a change in the good direction, brighter on a dark surface. Swap for the theme's success colour once it has one. */
private fun successColor(surface: Color): Color = if (surface.luminance() < 0.5f) Color(0xFF3DD68C) else Color(0xFF1E9E5A)
