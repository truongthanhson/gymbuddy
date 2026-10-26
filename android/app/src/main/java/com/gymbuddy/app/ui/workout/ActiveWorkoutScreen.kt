package com.gymbuddy.app.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.data.workout.WorkoutExerciseWithSets
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.ExerciseCategory
import com.gymbuddy.app.domain.REST_TIME_OPTIONS
import com.gymbuddy.app.domain.RPE_OPTIONS
import com.gymbuddy.app.domain.RecordKind
import com.gymbuddy.app.domain.SetField
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.distanceUnit
import com.gymbuddy.app.domain.formatDuration
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.domain.formatRpe
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.domain.liveRecords
import com.gymbuddy.app.domain.parseDuration
import com.gymbuddy.app.domain.resolveRestSeconds
import com.gymbuddy.app.domain.setFields
import com.gymbuddy.app.domain.startsRest
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.restLabel
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.theme.GymBuddyTheme
import com.gymbuddy.watchlink.setLabels
import kotlinx.coroutines.delay

/**
 * The workout being logged. With [editHeader] it edits a finished workout from history instead: the header
 * replaces the timer and Finish button (it gets a callback that opens the rename dialog), and there is no rest timer.
 */
@Composable
fun ActiveWorkoutScreen(
    workout: WorkoutWithExercises,
    viewModel: WorkoutViewModel,
    onAddExercises: () -> Unit,
    onReplaceExercise: (workoutExerciseId: String) -> Unit,
    editHeader: (@Composable (onRename: () -> Unit) -> Unit)? = null,
) {
    val editing = editHeader != null
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
                if (editHeader != null) {
                    editHeader { showRename = true }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { showRename = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(workout.workout.name, style = MaterialTheme.typography.headlineMedium)
                            }
                            ElapsedText(startedAt = workout.workout.startedAt)
                        }
                        Button(onClick = { showFinish = true }) { Text(stringResource(R.string.finish)) }
                    }
                }
            }
            item {
                NoteField(
                    note = workout.workout.note,
                    placeholder = stringResource(R.string.workout_note_placeholder),
                    onNoteChange = { viewModel.setWorkoutNote(workout.workout.id, it) },
                )
            }
            val groups = workout.exercises.map { it.workoutExercise.supersetGroup }
            itemsIndexed(workout.exercises, key = { _, it -> it.workoutExercise.id }) { index, item ->
                ExerciseCard(
                    item = item,
                    title = item.exercise.displayName(vietnamese),
                    index = index,
                    groups = groups,
                    workoutId = workout.workout.id,
                    startedAt = workout.workout.startedAt,
                    editing = editing,
                    onReplace = { onReplaceExercise(item.workoutExercise.id) },
                    viewModel = viewModel,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_exercises))
                    }
                    if (!editing) {
                        TextButton(
                            onClick = { showCancel = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.cancel_workout), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        restTimer?.takeIf { !editing }?.let { timer ->
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
        val fromTemplate = workout.workout.templateId != null
        var saveToTemplate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Column {
                    Text(stringResource(if (anyCompleted) R.string.finish_message else R.string.finish_empty_message))
                    if (anyCompleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveToTemplate = !saveToTemplate }
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = saveToTemplate, onCheckedChange = null)
                            Text(
                                stringResource(if (fromTemplate) R.string.update_template else R.string.save_as_template),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinish = false
                    viewModel.finishWorkout(workout, saveToTemplate)
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
    index: Int,
    groups: List<Int?>,
    workoutId: String,
    startedAt: Long,
    editing: Boolean,
    onReplace: () -> Unit,
    viewModel: WorkoutViewModel,
) {
    val group = groups[index]
    val isLast = index == groups.lastIndex
    val exerciseId = item.workoutExercise.exerciseId
    // In an edited workout, "Previous" is the session before that one.
    val previousFlow = remember(exerciseId, startedAt) { viewModel.previousSets(exerciseId, before = startedAt) }
    val previous by previousFlow.collectAsStateWithLifecycle(emptyList())
    val historyFlow = remember(exerciseId, startedAt) { viewModel.historySets(exerciseId, before = startedAt) }
    val history by historyFlow.collectAsStateWithLifecycle(emptyList())
    // Warm-up sets never count as records.
    val records = liveRecords(
        history,
        item.sets.map { SetValues(it.weightKg, it.reps, it.completed && it.setType != SetType.WARMUP) },
    )
    var menuOpen by remember { mutableStateOf(false) }
    var restDialogOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    val settings = LocalAppSettings.current
    val restSeconds = resolveRestSeconds(
        item.workoutExercise.restSeconds,
        item.exercise?.defaultRestSeconds,
        settings.defaultRestSeconds,
    )

    Row(Modifier.height(IntrinsicSize.Min)) {
    if (group != null) {
        // Exercises of one superset share a coloured bar, like Strong.
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(supersetColor(group), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
    }
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (group != null) {
            Text(
                stringResource(R.string.superset),
                style = MaterialTheme.typography.labelSmall,
                color = supersetColor(group),
            )
        }
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
                    if (item.sets.any { it.setType == SetType.NORMAL && it.weightKg != null } &&
                        item.sets.none { it.setType == SetType.WARMUP } &&
                        SetField.WEIGHT in setFields(item.exercise?.category ?: "")
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_warmup_sets)) },
                            onClick = {
                                menuOpen = false
                                viewModel.addWarmupSets(item)
                            },
                        )
                    }
                    if (item.workoutExercise.note == null && !noteOpen) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_note)) },
                            onClick = {
                                menuOpen = false
                                noteOpen = true
                            },
                        )
                    }
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_up)) },
                            onClick = {
                                menuOpen = false
                                viewModel.moveExercise(workoutId, item.workoutExercise.id, -1)
                            },
                        )
                    }
                    if (!isLast) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_down)) },
                            onClick = {
                                menuOpen = false
                                viewModel.moveExercise(workoutId, item.workoutExercise.id, 1)
                            },
                        )
                    }
                    if (!isLast && (group == null || groups[index + 1] != group)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.superset_with_next)) },
                            onClick = {
                                menuOpen = false
                                viewModel.supersetWithNext(workoutId, index)
                            },
                        )
                    }
                    if (group != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_superset)) },
                            onClick = {
                                menuOpen = false
                                viewModel.removeFromSuperset(workoutId, index)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.replace_exercise)) },
                        onClick = {
                            menuOpen = false
                            onReplace()
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
        if (item.workoutExercise.note != null || noteOpen) {
            NoteField(
                note = item.workoutExercise.note,
                placeholder = stringResource(R.string.note_placeholder),
                onNoteChange = {
                    // Keep the field while the user clears it.
                    noteOpen = true
                    viewModel.setExerciseNote(item.workoutExercise.id, it)
                },
            )
        }
        val category = item.exercise?.category ?: ""
        val fields = setFields(category)
        SetHeader(settings.weightUnit, fields, assisted = category == ExerciseCategory.ASSISTED_BODYWEIGHT.key)
        val labels = setLabels(item.sets.map { it.setType.name.lowercase() })
        item.sets.forEachIndexed { setIndex, set ->
            // Keyed so a swiped-away row doesn't hand its state to the next one.
            key(set.id) {
                val previousSet = previous.getOrNull(setIndex)
                SetRow(
                    label = labels[setIndex],
                    set = set,
                    previous = previousSet,
                    records = records[setIndex],
                    onToggle = { viewModel.toggleSet(item, set, previousSet, title, !editing && startsRest(groups, index)) },
                    unit = settings.weightUnit,
                    fields = fields,
                    viewModel = viewModel,
                )
            }
        }
        TextButton(onClick = { viewModel.addSet(item.workoutExercise.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_set))
        }
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
private fun supersetColor(group: Int) = listOf(
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.error,
)[Math.floorMod(group, 4)]

private val SetColumn = 40.dp
private val ValueColumn = 72.dp
private val CheckColumn = 48.dp

@Composable
private fun SetHeader(unit: WeightUnit, fields: List<SetField>, assisted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderText(stringResource(R.string.col_set), Modifier.width(SetColumn))
        HeaderText(stringResource(R.string.col_previous), Modifier.weight(1f))
        fields.forEach { field ->
            val title = when (field) {
                // Assistance makes the lift lighter, so Strong shows it as a negative weight.
                SetField.WEIGHT -> if (assisted) "-${unit.symbol}" else unit.symbol
                SetField.REPS -> stringResource(R.string.col_reps)
                SetField.DISTANCE -> unit.distanceUnit.symbol
                SetField.DURATION -> stringResource(R.string.col_time)
            }
            HeaderText(title, Modifier.width(ValueColumn))
        }
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
    label: String,
    set: WorkoutSetEntity,
    previous: WorkoutSetEntity?,
    records: Set<RecordKind>,
    onToggle: () -> Unit,
    unit: WeightUnit,
    fields: List<SetField>,
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
    // Done sets turn green, like ticking them off on paper.
    val rowColor by animateColorAsState(
        if (set.completed) GymBuddyTheme.colors.successContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "setRow",
    )

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
            SetLabel(label, set, viewModel)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    previous?.let { previousLabel(it, unit, fields) } ?: "—",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (records.isNotEmpty()) RecordBadge(records)
            }
            fields.forEach { field ->
                when (field) {
                    SetField.WEIGHT -> NumberField(
                        value = set.weightKg?.let(unit::display),
                        format = ::formatWeight,
                        parse = ::parseDecimal,
                        placeholder = previous?.weightKg?.let { formatWeight(unit.display(it)) },
                        input = InputKind.DECIMAL,
                        onValueChange = { viewModel.setWeight(set.id, it?.let(unit::toKg)) },
                    )
                    SetField.REPS -> NumberField(
                        value = set.reps,
                        format = { it.toString() },
                        parse = { it.toIntOrNull() },
                        placeholder = previous?.reps?.toString(),
                        input = InputKind.INTEGER,
                        onValueChange = { viewModel.setReps(set.id, it) },
                    )
                    SetField.DISTANCE -> NumberField(
                        value = set.distanceM?.let(unit.distanceUnit::display),
                        format = ::formatWeight,
                        parse = ::parseDecimal,
                        placeholder = previous?.distanceM?.let { formatWeight(unit.distanceUnit.display(it)) },
                        input = InputKind.DECIMAL,
                        onValueChange = { viewModel.setDistance(set.id, it?.let(unit.distanceUnit::toMeters)) },
                    )
                    SetField.DURATION -> NumberField(
                        value = set.durationSec,
                        format = ::formatDuration,
                        parse = ::parseDuration,
                        placeholder = previous?.durationSec?.let(::formatDuration),
                        input = InputKind.DURATION,
                        onValueChange = { viewModel.setDuration(set.id, it) },
                    )
                }
            }
            Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = { onToggle() },
                    shape = MaterialTheme.shapes.small,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = GymBuddyTheme.colors.success,
                        checkedContentColor = GymBuddyTheme.colors.onSuccess,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            }
        }
    }
}

/** Strong-style medal on a set that beat a personal record, naming which ones. */
@Composable
private fun RecordBadge(records: Set<RecordKind>) {
    val names = records.sorted().map {
        stringResource(
            when (it) {
                RecordKind.ONE_REP_MAX -> R.string.pr_one_rep_max
                RecordKind.WEIGHT -> R.string.pr_weight
                RecordKind.VOLUME -> R.string.pr_volume
                RecordKind.REPS -> R.string.pr_reps
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = stringResource(R.string.personal_record),
            tint = GymBuddyTheme.colors.gold,
            modifier = Modifier.size(14.dp),
        )
        Text(
            names.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = GymBuddyTheme.colors.gold,
            textAlign = TextAlign.Center,
        )
    }
}

/** The set's number or W/D/F; tapping it changes the set type or the RPE. */
@Composable
private fun SetLabel(label: String, set: WorkoutSetEntity, viewModel: WorkoutViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var rpeOpen by remember { mutableStateOf(false) }
    val color = when (set.setType) {
        SetType.WARMUP -> MaterialTheme.colorScheme.tertiary
        SetType.DROP -> MaterialTheme.colorScheme.secondary
        SetType.FAILURE -> MaterialTheme.colorScheme.error
        SetType.NORMAL -> LocalContentColor.current
    }
    Box(Modifier.width(SetColumn)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = color, fontWeight = FontWeight.Bold)
            set.rpe?.let {
                Text("@${formatRpe(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SetType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    leadingIcon = {
                        if (type == set.setType) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        viewModel.setSetType(set.id, type)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_rpe)) },
                onClick = {
                    menuOpen = false
                    rpeOpen = true
                },
            )
        }
    }

    if (rpeOpen) {
        AlertDialog(
            onDismissRequest = { rpeOpen = false },
            title = { Text(stringResource(R.string.rpe)) },
            text = {
                Column {
                    Text(stringResource(R.string.rpe_hint), style = MaterialTheme.typography.bodySmall)
                    (listOf<Double?>(null) + RPE_OPTIONS).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    rpeOpen = false
                                    viewModel.setRpe(set.id, option)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == set.rpe, onClick = null)
                            Text(
                                option?.let(::formatRpe) ?: stringResource(R.string.rpe_none),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { rpeOpen = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

private fun SetType.labelRes(): Int = when (this) {
    SetType.WARMUP -> R.string.set_type_warmup
    SetType.NORMAL -> R.string.set_type_normal
    SetType.DROP -> R.string.set_type_drop
    SetType.FAILURE -> R.string.set_type_failure
}

/** A note that saves as the user types; it keeps its own text so saving doesn't move the cursor. */
@Composable
private fun NoteField(note: String?, placeholder: String, onNoteChange: (String) -> Unit) {
    var text by remember { mutableStateOf(note ?: "") }
    LaunchedEffect(note) {
        if (text.ifBlank { null } != note) text = note ?: ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onNoteChange(it)
        },
        placeholder = { Text(placeholder) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "60 kg × 8", "× 12", "5 km in 25:00" or "1:00", depending on what the exercise logs. */
private fun previousLabel(set: WorkoutSetEntity, unit: WeightUnit, fields: List<SetField>): String {
    val parts = fields.mapNotNull { field ->
        when (field) {
            SetField.WEIGHT -> set.weightKg?.let(unit::format)
            SetField.REPS -> set.reps?.let { "× $it" }
            SetField.DISTANCE -> set.distanceM?.let { "${formatWeight(unit.distanceUnit.display(it))} ${unit.distanceUnit.symbol}" }
            SetField.DURATION -> set.durationSec?.let(::formatDuration)
        }
    }
    return parts.joinToString(" ").ifEmpty { "—" }
}

private fun parseDecimal(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

private enum class InputKind(val keyboard: KeyboardType, val allows: (Char) -> Boolean) {
    DECIMAL(KeyboardType.Decimal, { it.isDigit() || it == '.' || it == ',' }),
    INTEGER(KeyboardType.Number, Char::isDigit),
    // Times are typed as 1:30; not every number pad has a colon.
    DURATION(KeyboardType.Text, { it.isDigit() || it == ':' }),
}

/** A compact numeric input that keeps the user's text while typing and saves the parsed value. */
@Composable
private fun <T : Any> NumberField(
    value: T?,
    format: (T) -> String,
    parse: (String) -> T?,
    placeholder: String?,
    input: InputKind,
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty() && placeholder != null) {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                val allowed = typed.filter(input.allows)
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
                keyboardType = input.keyboard,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
