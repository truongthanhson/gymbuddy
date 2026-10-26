package com.gymbuddy.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.RestTimerState
import com.gymbuddy.app.domain.formatElapsed
import kotlinx.coroutines.delay

@Composable
fun RestTimerBar(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onElapsed: () -> Unit,
) {
    val now by produceState(System.currentTimeMillis(), state) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    val remaining = state.remainingSeconds(now)
    LaunchedEffect(state, remaining == 0) {
        if (remaining == 0) onElapsed()
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(progress = { state.progress(now) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAdjust(-15) }) { Text("−15s") }
                Text(
                    formatElapsed(remaining.toLong()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OutlinedButton(onClick = { onAdjust(15) }) { Text("+15s") }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
            }
        }
    }
}
