package com.gymbuddy.app.ui.charts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Touch handling shared by the charts: a touch selects the point under the finger right away and a sideways drag
 * scrubs along the chart. A mostly vertical drag is left to the screen's scroll. [onDown] comes before the first
 * [onScrub]; a touch that doesn't drag ends with [onTap], so the chart can toggle the selection off, and one the screen
 * scrolls with ends with [onCancel], so the chart can put back what was selected before.
 */
fun Modifier.chartScrubbing(
    key: Any?,
    onDown: () -> Unit,
    onScrub: (x: Float) -> Unit,
    onTap: () -> Unit,
    onCancel: () -> Unit,
): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onDown()
            onScrub(down.position.x)
            var dragging = false
            var total = Offset.Zero
            var tapped = true
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.isConsumed) {
                    tapped = false
                    cancelled = !dragging
                    break
                }
                total += change.positionChange()
                if (!dragging) {
                    if (abs(total.y) > viewConfiguration.touchSlop && abs(total.y) > abs(total.x)) {
                        tapped = false
                        cancelled = true
                        break
                    }
                    if (abs(total.x) > viewConfiguration.touchSlop) dragging = true
                }
                if (dragging) {
                    tapped = false
                    change.consume()
                    onScrub(change.position.x)
                }
            }
            if (tapped) onTap()
            if (cancelled) onCancel()
        }
    }

/** Draws [text] in a rounded bubble centred above [anchor] (below it when there is no room), kept inside the canvas. */
fun DrawScope.drawTooltip(text: TextLayoutResult, anchor: Offset, background: Color, gap: Float = 12.dp.toPx()) {
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val bubble = Size(text.size.width + 2 * padH, text.size.height + 2 * padV)
    val left = (anchor.x - bubble.width / 2).coerceIn(0f, (size.width - bubble.width).coerceAtLeast(0f))
    val above = anchor.y - gap - bubble.height
    val top = if (above >= 0f) above else (anchor.y + gap).coerceAtMost(size.height - bubble.height)
    drawRoundRect(background, Offset(left, top), bubble, CornerRadius(8.dp.toPx()))
    drawText(text, topLeft = Offset(left + padH, top + padV))
}
