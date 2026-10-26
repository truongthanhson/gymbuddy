package com.gymbuddy.app.ui.exercise

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.data.exercise.Instructions
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.ExerciseCategory
import com.gymbuddy.app.domain.ProFeature
import com.gymbuddy.app.domain.Record
import com.gymbuddy.app.domain.SessionPoint
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.bestSetIndex
import com.gymbuddy.app.domain.estimatedOneRepMax
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.common.Card
import com.gymbuddy.app.ui.common.LineChart
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.exercises.ExerciseEditorDialog
import com.gymbuddy.app.ui.pro.ProLocked
import java.text.DateFormat
import java.util.Date

private enum class DetailTab(@StringRes val label: Int) {
    About(R.string.about_exercise),
    History(R.string.tab_history),
    Charts(R.string.charts),
    Records(R.string.records),
}

private enum class ChartMetric(@StringRes val label: Int, val value: (SessionPoint) -> Double) {
    OneRepMax(R.string.metric_one_rep_max, { it.estimatedOneRepMax }),
    MaxWeight(R.string.metric_max_weight, { it.maxWeight }),
    Volume(R.string.metric_volume, { it.volumeKg }),
}

/** History, charts and personal records of one exercise, like Strong's exercise page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(onBack: () -> Unit, onOpenPaywall: () -> Unit, viewModel: ExerciseDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val instructions by viewModel.instructions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val exercise = state.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise.displayName(useVietnameseNames()), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    if (exercise?.isCustom == true) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_exercise))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab) {
                DetailTab.entries.forEachIndexed { index, item ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(item.label)) })
                }
            }
            when (DetailTab.entries[tab]) {
                DetailTab.About -> AboutTab(exercise, instructions)
                DetailTab.History -> HistoryTab(state.history)
                DetailTab.Charts -> if (LocalIsPro.current) ChartsTab(state.points) else ProLocked(ProFeature.EXERCISE_CHARTS, onOpenPaywall)
                DetailTab.Records -> RecordsTab(state)
            }
        }
    }

    if (editing && exercise != null) {
        ExerciseEditorDialog(
            exercise = exercise,
            onSave = { name, bodyPart: BodyPart, category: ExerciseCategory -> viewModel.update(name, bodyPart, category) },
            onDismiss = { editing = false },
            onDelete = { viewModel.delete(onDeleted = onBack) },
        )
    }
}

private fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
private fun Empty(@StringRes text: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Body part, equipment and Strong-style step-by-step instructions. */
@Composable
private fun AboutTab(exercise: ExerciseEntity?, instructions: Instructions?) {
    if (exercise == null) return
    val vietnamese = useVietnameseNames()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "${stringResource(BodyPart.fromKey(exercise.bodyPart).label)} · ${stringResource(ExerciseCategory.fromKey(exercise.category).label)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val steps = instructions?.localized(vietnamese).orEmpty()
        if (steps.isEmpty()) {
            item { Text(stringResource(R.string.no_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { Text(stringResource(R.string.instructions), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(steps) { index, step ->
                Row {
                    Text("${index + 1}.", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                    Text(step, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(history: List<HistoryWorkout>) {
    if (history.isEmpty()) return Empty(R.string.no_exercise_history)
    val unit = LocalAppSettings.current.weightUnit
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(history, key = { it.workoutId }) { workout ->
            val best = bestSetIndex(workout.sets)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium)
                    Text(formatDate(workout.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    workout.sets.forEachIndexed { index, set ->
                        Row {
                            Text("${index + 1}", Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                setLabel(set.weightKg, set.reps, unit),
                                Modifier.weight(1f),
                                fontWeight = if (index == best) FontWeight.Bold else null,
                            )
                            val reps = set.reps ?: 0
                            if (reps > 0) {
                                Text(
                                    stringResource(R.string.one_rep_max_short, formatWeight(unit.display(estimatedOneRepMax(set.weightKg ?: 0.0, reps)))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun setLabel(weightKg: Double?, reps: Int?, unit: WeightUnit): String = when {
    weightKg != null && reps != null -> "${unit.format(weightKg)} × $reps"
    reps != null -> "× $reps"
    weightKg != null -> unit.format(weightKg)
    else -> "—"
}

@Composable
private fun ChartsTab(points: List<SessionPoint>) {
    if (points.size < 2) return Empty(R.string.not_enough_for_chart)
    val unit = LocalAppSettings.current.weightUnit
    var metric by rememberSaveable { mutableStateOf(ChartMetric.OneRepMax) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartMetric.entries.forEach { m ->
                FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(stringResource(m.label)) })
            }
        }
        LineChart(
            points = points.map { it.startedAt to unit.display(metric.value(it)) },
            formatValue = { "${formatWeight(it)} ${unit.symbol}" },
            formatTime = ::formatDate,
        )
    }
}

@Composable
private fun RecordsTab(state: ExerciseDetailState) {
    val records = state.records
    if (records.estimatedOneRepMax == null) return Empty(R.string.no_exercise_history)
    val unit = LocalAppSettings.current.weightUnit
    Column(Modifier.verticalScroll(rememberScrollState())) {
        RecordRow(R.string.metric_one_rep_max, records.estimatedOneRepMax) { unit.format(it) }
        RecordRow(R.string.record_max_weight, records.maxWeight) { unit.format(it) }
        RecordRow(R.string.record_max_set_volume, records.maxSetVolume) { unit.format(it) }
        RecordRow(R.string.record_max_reps, records.maxReps) { it.toInt().toString() }
        RecordRow(R.string.record_max_workout_volume, records.maxSessionVolume) { unit.format(it) }
    }
}

@Composable
private fun RecordRow(@StringRes label: Int, record: Record?, format: (Double) -> String) {
    if (record == null) return
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = { Text(formatDate(record.startedAt)) },
        trailingContent = { Text(format(record.value), style = MaterialTheme.typography.titleMedium) },
    )
    HorizontalDivider()
}
