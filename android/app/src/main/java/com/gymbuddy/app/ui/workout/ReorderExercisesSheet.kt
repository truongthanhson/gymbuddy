package com.gymbuddy.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gymbuddy.app.R

/** One row of the reorder sheet. */
data class ReorderEntry(val id: String, val title: String, val supersetGroup: Int?)

/**
 * The workout's exercises as a plain list with drag handles, like Strong's "Reorder exercises". Dragging a handle
 * moves its row and [onReorder] gets the new order of ids each time a row is dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderExercisesSheet(
    exercises: List<ReorderEntry>,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ids = exercises.map { it.id }
    val byId = exercises.associateBy { it.id }
    val listState = rememberLazyListState()
    var order by remember { mutableStateOf(ids) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ids) { if (draggingId == null) order = ids }

    fun dragBy(delta: Float) {
        dragOffset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == draggingId } ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val target = visible.firstOrNull { it.key != draggingId && center >= it.offset && center < it.offset + it.size }
            ?: return
        val from = order.indexOf(draggingId)
        val to = order.indexOf(target.key)
        if (from < 0 || to < 0) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The row now sits where the target was, so keep the finger where it is on screen.
        dragOffset += dragged.offset - target.offset
    }

    fun drop() {
        draggingId = null
        dragOffset = 0f
        if (order != ids) onReorder(order)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reorder_exercises),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
            LazyColumn(state = listState, modifier = Modifier.padding(top = 8.dp)) {
                items(order.mapNotNull { byId[it] }, key = { it.id }) { entry ->
                    val dragging = entry.id == draggingId
                    ReorderRow(
                        entry = entry,
                        modifier = if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = dragOffset
                                    shadowElevation = 6.dp.toPx()
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        } else {
                            Modifier.animateItem()
                        },
                        handleModifier = Modifier.pointerInput(entry.id) {
                            detectDragGestures(
                                onDragStart = {
                                    order = ids
                                    draggingId = entry.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragBy(amount.y)
                                },
                                onDragEnd = ::drop,
                                onDragCancel = ::drop,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(entry: ReorderEntry, modifier: Modifier, handleModifier: Modifier) {
    val bar = entry.supersetGroup?.let { supersetColor(it) } ?: Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind { drawRect(bar, topLeft = Offset(16.dp.toPx(), 0f), size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(handleModifier.size(56.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.reorder_exercises),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
