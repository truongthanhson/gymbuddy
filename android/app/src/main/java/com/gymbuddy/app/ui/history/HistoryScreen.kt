package com.gymbuddy.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.bestSetIndex
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.domain.volumeKg
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val list = workouts
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_history), textAlign = TextAlign.Center)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.workout.id }) { WorkoutCard(it) }
        }
    }
}

private fun WorkoutSetEntity.values() = SetValues(weightKg, reps, completed)

@Composable
private fun WorkoutCard(item: WorkoutWithExercises) {
    val vietnamese = useVietnameseNames()
    val unit = LocalAppSettings.current.weightUnit
    val workout = item.workout
    val allSets = item.exercises.flatMap { it.sets }.map { it.values() }
    val duration = ((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1_000
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.startedAt))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.workout_summary, formatElapsed(duration), unit.format(volumeKg(allSets))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.exercises.forEach { exercise ->
                val sets = exercise.sets.map { it.values() }
                val best = bestSetIndex(sets)?.let { sets[it] }
                Row {
                    Text(
                        "${sets.size} × ${exercise.exercise.displayName(vietnamese)}",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (best != null) {
                        Text("${unit.format(best.weightKg ?: 0.0)} × ${best.reps}")
                    }
                }
            }
        }
    }
}
