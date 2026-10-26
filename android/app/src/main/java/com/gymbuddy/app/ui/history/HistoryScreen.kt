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
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.ui.common.EmptyState
import com.gymbuddy.app.ui.common.GymCard
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.ScreenHeader
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.theme.Spacing

@Composable
fun HistoryScreen(
    onOpenWorkout: (workoutId: String) -> Unit,
    onOpenCalendar: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val workouts by viewModel.cards.collectAsStateWithLifecycle()
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
                items(list, key = { it.id }) { WorkoutCard(it, onClick = { onOpenWorkout(it.id) }) }
            }
        }
    }
}

@Composable
private fun WorkoutCard(card: HistoryCard, onClick: () -> Unit) {
    val vietnamese = useVietnameseNames()
    val unit = LocalAppSettings.current.weightUnit
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    GymCard(onClick = onClick) {
        Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${card.date} · ${formatElapsed(card.durationSeconds)} · ${unit.format(card.volumeKg)}",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        card.lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${line.sets} × ${line.exercise.displayName(vietnamese)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.bestReps != null) {
                    Text("${unit.format(line.bestWeightKg ?: 0.0)} × ${line.bestReps}", style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
        if (card.moreExercises > 0) {
            Text(stringResource(R.string.more_exercises, card.moreExercises), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
