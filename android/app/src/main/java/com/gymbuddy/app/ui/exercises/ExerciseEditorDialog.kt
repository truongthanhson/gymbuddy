package com.gymbuddy.app.ui.exercises

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.domain.ExerciseCategory

/**
 * Create ([exercise] null) or edit a custom exercise.
 * [onDelete] is only offered when editing.
 */
@Composable
fun ExerciseEditorDialog(
    exercise: ExerciseEntity?,
    onSave: (name: String, bodyPart: BodyPart, category: ExerciseCategory) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(exercise?.name ?: "") }
    var bodyPart by remember { mutableStateOf(exercise?.let { BodyPart.fromKey(it.bodyPart) } ?: BodyPart.CHEST) }
    var category by remember { mutableStateOf(exercise?.let { ExerciseCategory.fromKey(it.category) } ?: ExerciseCategory.BARBELL) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (exercise == null) R.string.new_exercise else R.string.edit_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(
                    label = R.string.exercise_body_part,
                    options = BodyPart.entries,
                    selected = bodyPart,
                    optionLabel = { it.label },
                    onSelect = { bodyPart = it },
                )
                ChoiceField(
                    label = R.string.exercise_category,
                    options = ExerciseCategory.entries,
                    selected = category,
                    optionLabel = { it.label },
                    onSelect = { category = it },
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, bodyPart, category)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_exercise_title)) },
            text = { Text(stringResource(R.string.delete_exercise_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun <T> ChoiceField(
    @StringRes label: Int,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(optionLabel(selected)), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(optionLabel(option))) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
