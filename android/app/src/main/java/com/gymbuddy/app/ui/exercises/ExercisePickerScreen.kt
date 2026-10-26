package com.gymbuddy.app.ui.exercises

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R

/** Multi-select exercise list; [onDone] receives the picked ids in the order they were tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    onDone: (List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<String>() }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exercises)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_exercise))
                    }
                    TextButton(onClick = { onDone(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text(stringResource(R.string.add_count, selected.size))
                    }
                },
            )
        },
    ) { padding ->
        ExerciseBrowser(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onBodyPartSelected = viewModel::onBodyPartSelected,
            modifier = Modifier.padding(padding),
            isSelected = { it.id in selected },
            onExerciseClick = { if (!selected.remove(it.id)) selected.add(it.id) },
        )
    }

    if (creating) {
        ExerciseEditorDialog(
            exercise = null,
            onSave = { name, bodyPart, category ->
                viewModel.createCustom(name, bodyPart, category) { selected.add(it) }
            },
            onDismiss = { creating = false },
        )
    }
}
