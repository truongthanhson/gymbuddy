package com.gymbuddy.app.ui.history

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.WorkoutEntity
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.workout.shareWorkout
import com.gymbuddy.app.ui.workout.ActiveWorkoutScreen
import com.gymbuddy.app.ui.workout.WorkoutViewModel
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

/** A finished workout from history, edited in place like Strong's "Edit workout". */
@Composable
fun WorkoutEditScreen(
    workoutId: String,
    onAddExercises: () -> Unit,
    onReplaceExercise: (workoutExerciseId: String) -> Unit,
    onClose: () -> Unit,
    onRepeated: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val flow = remember(workoutId) { viewModel.workout(workoutId) }
    val workout by flow.collectAsStateWithLifecycle(null)
    val active by viewModel.state.collectAsStateWithLifecycle()
    val close = {
        viewModel.finishEditing(workoutId)
        onClose()
    }
    BackHandler(onBack = close)

    val context = LocalContext.current
    val vietnamese = useVietnameseNames()
    val unit = LocalAppSettings.current.weightUnit
    val shareTitle = stringResource(R.string.share_workout)
    val current = workout
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ActiveWorkoutScreen(
        workout = current,
        viewModel = viewModel,
        onAddExercises = onAddExercises,
        onReplaceExercise = onReplaceExercise,
        editHeader = { onRename ->
            EditHeader(
                workout = current.workout,
                onShare = { shareWorkout(context, current, vietnamese, unit, shareTitle) },
                canRepeat = active?.workout == null,
                viewModel = viewModel,
                onRename = onRename,
                onSave = close,
                onDeleted = onClose,
                onRepeat = {
                    viewModel.finishEditing(workoutId)
                    viewModel.repeatWorkout(workoutId)
                    onRepeated()
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHeader(
    workout: WorkoutEntity,
    onShare: () -> Unit,
    canRepeat: Boolean,
    viewModel: WorkoutViewModel,
    onRename: () -> Unit,
    onSave: () -> Unit,
    onDeleted: () -> Unit,
    onRepeat: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showDuration by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val durationMillis = (workout.endedAt ?: workout.startedAt) - workout.startedAt
    val templateSaved = stringResource(R.string.template_saved)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRename, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Button(onClick = onSave) { Text(stringResource(R.string.save)) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_as_template)) },
                        onClick = {
                            menuOpen = false
                            viewModel.saveAsTemplate(workout.id)
                            Toast.makeText(context, templateSaved, Toast.LENGTH_SHORT).show()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    if (canRepeat) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.perform_again)) },
                            onClick = {
                                menuOpen = false
                                onRepeat()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_workout), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            showDelete = true
                        },
                    )
                }
            }
        }
        Row {
            TextButton(onClick = { showDate = true }) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(workout.startedAt)))
            }
            TextButton(onClick = { showDuration = true }) { Text(formatElapsed(durationMillis / 1_000)) }
        }
        Text(
            stringResource(R.string.edit_workout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDate) {
        // The picker works in UTC days; the time of day stays as it was.
        val start = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val startedAt = start.with(day).toInstant().toEpochMilli()
                        viewModel.setWorkoutTimes(workout.id, startedAt, startedAt + durationMillis)
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        ) { DatePicker(state) }
    }

    if (showDuration) {
        var minutes by remember { mutableStateOf((durationMillis / 60_000).toString()) }
        val parsed = minutes.toLongOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showDuration = false },
            title = { Text(stringResource(R.string.workout_duration_minutes)) },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    showDuration = false
                    parsed?.let { viewModel.setWorkoutTimes(workout.id, workout.startedAt, workout.startedAt + it * 60_000) }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDuration = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_workout)) },
            text = { Text(stringResource(R.string.delete_workout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteWorkout(workout.id)
                    onDeleted()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
