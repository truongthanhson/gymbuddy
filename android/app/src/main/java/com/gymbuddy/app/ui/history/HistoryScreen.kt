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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.gymbuddy.app.domain.volumeKg
import com.gymbuddy.app.ui.common.EmptyState
import com.gymbuddy.app.ui.common.GymCard
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.ScreenHeader
import com.gymbuddy.app.ui.common.StatTile
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    onOpenWorkout: (workoutId: String) -> Unit,
    onOpenCalendar: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val list = workouts
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.tab_history),
            subtitle = list?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.workout_count, it.size, it.size) },
        ) {
            IconButton(onClick = onOpenCalendar) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.calendar), tint = MaterialTheme.colorScheme.primary)
            }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.no_history_title),
                    message = stringResource(R.string.no_history_hint),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(list, key = { it.workout.id }) { WorkoutCard(it, onClick = { onOpenWorkout(it.workout.id) }) }
            }
        }
    }
}

private fun WorkoutSetEntity.values() = SetValues(weightKg, reps, completed)

@Composable
private fun WorkoutCard(item: WorkoutWithExercises, onClick: () -> Unit) {
    val vietnamese = useVietnameseNames()
    val unit = LocalAppSettings.current.weightUnit
    val workout = item.workout
    val allSets = item.exercises.flatMap { it.sets }.map { it.values() }
    val duration = ((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1_000
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.startedAt))

    GymCard(onClick = onClick) {
        Text(workout.name, style = MaterialTheme.typography.titleMedium)
        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(formatElapsed(duration), stringResource(R.string.duration), Modifier.weight(1f))
            StatTile(unit.format(volumeKg(allSets)), stringResource(R.string.volume), Modifier.weight(1f))
            StatTile(allSets.size.toString(), stringResource(R.string.stat_sets), Modifier.weight(1f))
        }
        item.exercises.forEach { exercise ->
            val sets = exercise.sets.map { it.values() }
            val best = bestSetIndex(sets)?.let { sets[it] }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sets.size} × ${exercise.exercise.displayName(vietnamese)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (best != null) {
                    Text(
                        "${unit.format(best.weightKg ?: 0.0)} × ${best.reps}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
