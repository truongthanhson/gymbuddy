package com.gymbuddy.app.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Share of the grow animation each bar takes; the bars start one after another across the rest. */
private const val BAR_SHARE = 0.6f

/**
 * Vertical bars that grow in one after another, with a dashed average line. Touching or dragging across the bars
 * highlights one and shows [tooltip] for it; tapping it again clears the highlight.
 */
@Composable
fun AnimatedBarChart(
    values: List<Int>,
    labels: List<String>,
    tooltip: (index: Int) -> String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
    averageLabel: ((Double) -> String)? = null,
    description: String = "",
) {
    if (values.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val tabular = TextStyle(fontFeatureSettings = "tnum")
    val labelStyle = MaterialTheme.typography.labelSmall.merge(tabular).copy(color = colors.onSurfaceVariant)
    val countStyle = MaterialTheme.typography.labelMedium.merge(tabular).copy(color = colors.onSurface)
    val tooltipStyle = MaterialTheme.typography.labelMedium.merge(tabular).copy(color = colors.inverseOnSurface)

    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(durationMillis = 1000, easing = LinearEasing))
    }
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    var beforeDown by remember { mutableStateOf<Int?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val dim by animateFloatAsState(if (selected != null) 0.35f else 1f, tween(200), label = "dim")
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val average = values.average()

    Canvas(
        modifier
            .fillMaxWidth()
            .height(chartHeight)
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = description }
            .chartScrubbing(
                key = values to canvasSize,
                onDown = { beforeDown = selected },
                onScrub = { x ->
                    val slot = canvasSize.width.toFloat() / values.size
                    val index = (x / slot).toInt().coerceIn(0, values.lastIndex)
                    if (index != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selected = index
                    }
                },
                onTap = { if (beforeDown == selected) selected = null },
                onCancel = { selected = beforeDown },
            ),
    ) {
        val labelHeight = measurer.measure(AnnotatedString("0"), labelStyle).size.height.toFloat()
        val countHeight = measurer.measure(AnnotatedString("0"), countStyle).size.height.toFloat()
        val top = countHeight + 4.dp.toPx()
        val bottom = size.height - labelHeight - 6.dp.toPx()
        val plot = bottom - top
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        val share = if (values.size > 1) BAR_SHARE else 1f
        val step = if (values.size > 1) (1f - share) / (values.size - 1) else 0f

        drawLine(colors.outlineVariant, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, value ->
            val local = ((grow.value - i * step) / share).coerceIn(0f, 1f)
            val eased = EaseOutBack.transform(local)
            val h = plot * value / max * eased
            val left = i * slot + (slot - barWidth) / 2
            val alpha = if (selected == null || selected == i) 1f else dim
            if (value > 0) {
                drawRoundRect(
                    colors.primary.copy(alpha = alpha),
                    topLeft = Offset(left, bottom - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(6.dp.toPx().coerceAtMost(barWidth / 2)),
                )
            }
            val count = measurer.measure(AnnotatedString(value.toString()), countStyle)
            drawText(
                count,
                topLeft = Offset(i * slot + (slot - count.size.width) / 2, bottom - h - count.size.height - 2.dp.toPx()),
                alpha = local * alpha,
            )
            val label = measurer.measure(AnnotatedString(labels.getOrElse(i) { "" }), labelStyle)
            drawText(label, topLeft = Offset(i * slot + (slot - label.size.width) / 2, size.height - label.size.height))
        }

        if (averageLabel != null && average > 0) {
            val y = bottom - plot * (average / max).toFloat() * grow.value.coerceAtMost(1f)
            drawLine(
                colors.onSurfaceVariant.copy(alpha = 0.7f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            val text = measurer.measure(AnnotatedString(averageLabel(average)), labelStyle)
            drawRoundRect(
                colors.surface.copy(alpha = 0.85f),
                topLeft = Offset(size.width - text.size.width - 6.dp.toPx(), y - text.size.height - 2.dp.toPx()),
                size = Size(text.size.width + 4.dp.toPx(), text.size.height.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), y - text.size.height - 2.dp.toPx()))
        }

        selected?.let { i ->
            val value = values[i]
            val anchor = Offset(i * slot + slot / 2, bottom - plot * value / max)
            drawTooltip(measurer.measure(AnnotatedString(tooltip(i)), tooltipStyle), anchor, colors.inverseSurface.copy(alpha = 0.92f))
        }
    }
}
