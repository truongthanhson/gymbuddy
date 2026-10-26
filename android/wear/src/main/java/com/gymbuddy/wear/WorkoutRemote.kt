package com.gymbuddy.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.gymbuddy.watchlink.WatchSet
import com.gymbuddy.watchlink.WatchState
import com.gymbuddy.watchlink.WatchWorkout
import com.gymbuddy.watchlink.nextSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun WorkoutRemote(
    stateFlow: StateFlow<WatchState?>,
    onCompleteSet: (String) -> Unit,
    onSkipRest: () -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    MaterialTheme {
        val listState = rememberScalingLazyListState()
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            val workout = state?.workout
            if (workout == null) {
                Message(stringResource(if (state == null) R.string.waiting_for_phone else R.string.no_workout))
            } else {
                Workout(workout, listState, onCompleteSet, onSkipRest)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Workout(
    workout: WatchWorkout,
    listState: ScalingLazyListState,
    onCompleteSet: (String) -> Unit,
    onSkipRest: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val next = workout.nextSet()
    val restLeft = workout.restEndsAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) } ?: 0

    ScalingLazyColumn(Modifier.fillMaxWidth(), state = listState) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(workout.name, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
                Text(formatClock((now - workout.startedAt) / 1000), style = MaterialTheme.typography.caption1)
            }
        }
        if (restLeft > 0) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.rest, formatClock(restLeft)),
                        color = MaterialTheme.colors.secondary,
                        style = MaterialTheme.typography.title3,
                    )
                    CompactChip(onClick = onSkipRest, label = { Text(stringResource(R.string.skip_rest)) })
                }
            }
        }
        if (next == null) {
            item { Text(stringResource(R.string.all_sets_done), textAlign = TextAlign.Center) }
        } else {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(workout.exercises[next.exerciseIndex].name, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
                    Text(
                        "${stringResource(R.string.set_label, next.set.label)} · ${next.set.summary()}",
                        style = MaterialTheme.typography.body1,
                    )
                }
            }
            item {
                Chip(
                    onClick = { onCompleteSet(next.set.id) },
                    label = { Text(stringResource(R.string.complete_set)) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(workout.exercises) { exercise ->
            Chip(
                onClick = {},
                label = { Text(exercise.name, maxLines = 1) },
                secondaryLabel = {
                    Text(stringResource(R.string.sets_progress, exercise.sets.count { it.done }, exercise.sets.size))
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "60 kg × 8"; values not filled in yet show last session's in brackets, or a dash. */
private fun WatchSet.summary(): String {
    val w = weight.ifEmpty { hintWeight?.let { "($it)" } ?: "—" }
    val r = reps.ifEmpty { hintReps?.let { "($it)" } ?: "—" }
    return "$w $unit × $r"
}

private fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
