package com.gymbuddy.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.WorkoutExerciseWithSets
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.REST_TIME_OPTIONS
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.domain.resolveRestSeconds
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutScreen(
    workout: WorkoutWithExercises,
    viewModel: WorkoutViewModel,
    onAddExercises: () -> Unit,
) {
    var showFinish by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val vietnamese = useVietnameseNames()

    val restTimer by viewModel.restTimerState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { showRename = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(workout.workout.name, style = MaterialTheme.typography.headlineSmall)
                        }
                        ElapsedText(startedAt = workout.workout.startedAt)
                    }
                    Button(onClick = { showFinish = true }) { Text(stringResource(R.string.finish)) }
                }
            }
            items(workout.exercises, key = { it.workoutExercise.id }) { item ->
                ExerciseCard(
                    item = item,
                    title = item.exercise.displayName(vietnamese),
                    viewModel = viewModel,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_exercises))
                    }
                    TextButton(
                        onClick = { showCancel = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cancel_workout), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        restTimer?.let { timer ->
            RestTimerBar(
                state = timer,
                onAdjust = viewModel::adjustRestTimer,
                onSkip = viewModel::skipRestTimer,
                onElapsed = viewModel::onRestTimerElapsed,
            )
        }
    }

    if (showFinish) {
        val anyCompleted = workout.exercises.any { e -> e.sets.any { it.completed } }
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Text(stringResource(if (anyCompleted) R.string.finish_message else R.string.finish_empty_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinish = false
                    viewModel.finishWorkout(workout)
                }) { Text(stringResource(R.string.finish)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinish = false }) { Text(stringResource(R.string.keep_going)) }
            },
        )
    }

    if (showCancel) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text(stringResource(R.string.cancel_workout_title)) },
            text = { Text(stringResource(R.string.cancel_workout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancel = false
                    viewModel.discardWorkout(workout.workout.id)
                }) { Text(stringResource(R.string.cancel_workout), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancel = false }) { Text(stringResource(R.string.keep_going)) }
            },
        )
    }

    if (showRename) {
        var name by remember { mutableStateOf(workout.workout.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.workout_name)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        showRename = false
                        viewModel.renameWorkout(workout.workout.id, name.trim())
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ElapsedText(startedAt: Long) {
    val now by produceState(System.currentTimeMillis(), startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        formatElapsed((now - startedAt) / 1_000),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ExerciseCard(
    item: WorkoutExerciseWithSets,
    title: String,
    viewModel: WorkoutViewModel,
) {
    val exerciseId = item.workoutExercise.exerciseId
    val previousFlow = remember(exerciseId) { viewModel.previousSets(exerciseId) }
    val previous by previousFlow.collectAsStateWithLifecycle(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var restDialogOpen by remember { mutableStateOf(false) }
    val restSeconds = resolveRestSeconds(item.workoutExercise.restSeconds, item.exercise?.defaultRestSeconds)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rest_time_value, restLabel(restSeconds))) },
                        onClick = {
                            menuOpen = false
                            restDialogOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_exercise)) },
                        onClick = {
                            menuOpen = false
                            viewModel.removeExercise(item.workoutExercise.id)
                        },
                    )
                }
            }
        }
        SetHeader()
        item.sets.forEachIndexed { index, set ->
            // Keyed so a swiped-away row doesn't hand its state to the next one.
            key(set.id) {
                val previousSet = previous.getOrNull(index)
                SetRow(
                    index = index,
                    set = set,
                    previous = previousSet,
                    onToggle = { viewModel.toggleSet(item, set, previousSet, title) },
                    viewModel = viewModel,
                )
            }
        }
        TextButton(onClick = { viewModel.addSet(item.workoutExercise.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_set))
        }
    }

    if (restDialogOpen) {
        AlertDialog(
            onDismissRequest = { restDialogOpen = false },
            title = { Text(stringResource(R.string.rest_timer)) },
            text = {
                Column {
                    REST_TIME_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    restDialogOpen = false
                                    viewModel.setRestSeconds(item.workoutExercise.id, option)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == restSeconds, onClick = null)
                            Text(restLabel(option), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restDialogOpen = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun restLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(R.string.rest_off) else formatElapsed(seconds.toLong())

private val SetColumn = 40.dp
private val ValueColumn = 72.dp
private val CheckColumn = 48.dp

@Composable
private fun SetHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderText(stringResource(R.string.col_set), Modifier.width(SetColumn))
        HeaderText(stringResource(R.string.col_previous), Modifier.weight(1f))
        HeaderText(stringResource(R.string.col_kg), Modifier.width(ValueColumn))
        HeaderText(stringResource(R.string.col_reps), Modifier.width(ValueColumn))
        Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null)
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSetEntity,
    previous: WorkoutSetEntity?,
    onToggle: () -> Unit,
    viewModel: WorkoutViewModel,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteSet(set.id)
                true
            } else {
                false
            }
        },
    )
    val rowColor = if (set.completed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(R.string.delete_set), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowColor)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${index + 1}", modifier = Modifier.width(SetColumn), textAlign = TextAlign.Center)
            Text(
                previous?.let { previousLabel(it) } ?: "—",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField(
                value = set.weightKg,
                format = ::formatWeight,
                parse = { it.replace(',', '.').toDoubleOrNull() },
                placeholder = previous?.weightKg?.let(::formatWeight),
                decimal = true,
                onValueChange = { viewModel.setWeight(set.id, it) },
            )
            NumberField(
                value = set.reps,
                format = { it.toString() },
                parse = { it.toIntOrNull() },
                placeholder = previous?.reps?.toString(),
                decimal = false,
                onValueChange = { viewModel.setReps(set.id, it) },
            )
            Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = { onToggle() },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            }
        }
    }
}

private fun previousLabel(set: WorkoutSetEntity): String = when {
    set.weightKg != null && set.reps != null -> "${formatWeight(set.weightKg)} kg × ${set.reps}"
    set.reps != null -> "× ${set.reps}"
    set.weightKg != null -> "${formatWeight(set.weightKg)} kg"
    else -> "—"
}

/** A compact numeric input that keeps the user's text while typing and saves the parsed value. */
@Composable
private fun <T : Any> NumberField(
    value: T?,
    format: (T) -> String,
    parse: (String) -> T?,
    placeholder: String?,
    decimal: Boolean,
    onValueChange: (T?) -> Unit,
) {
    var text by remember { mutableStateOf(value?.let(format) ?: "") }
    // Pick up changes made elsewhere (e.g. filled from the previous session) without fighting the cursor.
    LaunchedEffect(value) {
        if (parse(text) != value) text = value?.let(format) ?: ""
    }
    Box(
        Modifier
            .width(ValueColumn)
            .padding(horizontal = 4.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty() && placeholder != null) {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val allowed = if (decimal) input.filter { it.isDigit() || it == '.' || it == ',' } else input.filter(Char::isDigit)
                text = allowed
                onValueChange(parse(allowed))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center,
                color = LocalContentColor.current,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
