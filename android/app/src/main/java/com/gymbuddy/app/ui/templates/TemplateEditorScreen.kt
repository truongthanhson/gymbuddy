package com.gymbuddy.app.ui.templates

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.template.DraftExercise
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames

/**
 * Creates or edits a template. [pickedExercises] carries ids back from the exercise picker;
 * [onPickedConsumed] clears them once added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    pickedExercises: List<String>?,
    onPickedConsumed: () -> Unit,
    onAddExercises: () -> Unit,
    onClose: () -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val exercisesById by viewModel.exercisesById.collectAsStateWithLifecycle()
    val vietnamese = useVietnameseNames()
    val canSave = draft?.let { it.name.isNotBlank() && it.exercises.isNotEmpty() } == true

    LaunchedEffect(pickedExercises, draft != null) {
        if (!pickedExercises.isNullOrEmpty() && draft != null) {
            viewModel.addExercises(pickedExercises)
            onPickedConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (draft?.id == null) R.string.new_template else R.string.edit_template)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(enabled = canSave, onClick = { viewModel.save(onClose) }) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        val d = draft
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = d.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (folders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = d.folderId == null,
                            onClick = { viewModel.setFolder(null) },
                            label = { Text(stringResource(R.string.no_folder)) },
                        )
                        folders.forEach { folder ->
                            FilterChip(
                                selected = d.folderId == folder.id,
                                onClick = { viewModel.setFolder(folder.id) },
                                label = { Text(folder.name) },
                            )
                        }
                    }
                }
            }
            if (d.exercises.isEmpty()) {
                item {
                    Text(stringResource(R.string.unsaved_name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(d.exercises, key = { it.id }) { exercise ->
                DraftExerciseCard(
                    exercise = exercise,
                    title = exercisesById[exercise.exerciseId].displayName(vietnamese),
                    viewModel = viewModel,
                )
            }
            item {
                OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_exercises))
                }
            }
        }
    }
}

@Composable
private fun DraftExerciseCard(exercise: DraftExercise, title: String, viewModel: TemplateEditorViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val unit = LocalAppSettings.current.weightUnit
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_exercise)) }, onClick = {
                        menuOpen = false
                        viewModel.removeExercise(exercise.id)
                    })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.col_set), Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            Text(unit.symbol, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.col_reps), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            Box(Modifier.width(48.dp))
        }
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}", Modifier.width(32.dp), textAlign = TextAlign.Center)
                    DraftNumberField(
                        initial = set.targetWeightKg?.let { formatWeight(unit.display(it)) } ?: "",
                        decimal = true,
                        modifier = Modifier.weight(1f),
                        onValue = { viewModel.setWeight(exercise.id, set.id, it.replace(',', '.').toDoubleOrNull()?.let(unit::toKg)) },
                    )
                    DraftNumberField(
                        initial = set.targetReps?.toString() ?: "",
                        decimal = false,
                        modifier = Modifier.weight(1f),
                        onValue = { viewModel.setReps(exercise.id, set.id, it.toIntOrNull()) },
                    )
                    IconButton(onClick = { viewModel.removeSet(exercise.id, set.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_set))
                    }
                }
            }
        }
        TextButton(onClick = { viewModel.addSet(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_set))
        }
    }
}

@Composable
private fun DraftNumberField(initial: String, decimal: Boolean, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = if (decimal) input.filter { it.isDigit() || it == '.' || it == ',' } else input.filter(Char::isDigit)
            onValue(text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
