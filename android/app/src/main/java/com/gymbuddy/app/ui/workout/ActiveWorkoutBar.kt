package com.gymbuddy.app.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.formatElapsed
import kotlinx.coroutines.delay

/** The workout in progress, minimized above the tabs; tapping it opens the workout again. */
@Composable
fun ActiveWorkoutBar(viewModel: WorkoutViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workout = state?.workout?.workout ?: return
    val restTimer by viewModel.restTimerState.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), restTimer) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (restTimer != null) 250 else 1_000)
        }
    }
    val rest = restTimer?.remainingSeconds(now)?.takeIf { it > 0 }

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(workout.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatElapsed((now - workout.startedAt) / 1_000), style = MaterialTheme.typography.bodySmall)
            }
            if (rest != null) {
                Text(
                    stringResource(R.string.rest_remaining, formatElapsed(rest.toLong())),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
