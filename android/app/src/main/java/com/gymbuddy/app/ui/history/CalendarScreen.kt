package com.gymbuddy.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.monthGrid
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Months with the days you trained marked, newest month first, like Strong's history calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenWorkout: (workoutId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    // The first workout of each day; the list is newest first.
    val byDay = remember(workouts) {
        workouts.orEmpty().reversed()
            .groupBy { Instant.ofEpochMilli(it.workout.startedAt).atZone(zone).toLocalDate() }
            .mapValues { it.value.first().workout.id }
    }
    val months = remember(byDay) {
        val now = YearMonth.now(zone)
        val first = byDay.keys.minOrNull()?.let(YearMonth::from) ?: now
        generateSequence(now) { it.minusMonths(1) }.takeWhile { !it.isBefore(first) }.toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(months, key = { it.toString() }) { month -> Month(month, byDay, onOpenWorkout) }
        }
    }
}

@Composable
private fun Month(month: YearMonth, byDay: Map<LocalDate, String>, onOpenWorkout: (String) -> Unit) {
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val grid = monthGrid(month.year, month.monthValue, firstDay.value)
    val count = byDay.keys.count { YearMonth.from(it) == month }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(R.plurals.workouts_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            (0 until 7).forEach { i ->
                Text(
                    firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        (0 until grid.weeks).forEach { week ->
            Row {
                (0 until 7).forEach { column ->
                    val day = week * 7 + column - grid.leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..grid.days) {
                            val workoutId = byDay[month.atDay(day)]
                            if (workoutId != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { onOpenWorkout(workoutId) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(day.toString(), color = MaterialTheme.colorScheme.onPrimary)
                                }
                            } else {
                                Text(day.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
