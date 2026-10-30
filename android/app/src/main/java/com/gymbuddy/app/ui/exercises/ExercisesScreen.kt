package com.gymbuddy.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.ui.common.ScreenHeader
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames

@Composable
fun ExercisesScreen(onOpenExercise: (String) -> Unit, viewModel: ExercisesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_exercise))
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader(stringResource(R.string.tab_exercises))
            ExerciseBrowser(
                query = viewModel.query,
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onBodyPartSelected = viewModel::onBodyPartSelected,
                onExerciseClick = { onOpenExercise(it.id) },
            )
        }
    }

    if (creating) {
        ExerciseEditorDialog(
            exercise = null,
            onSave = { name, bodyPart, category -> viewModel.createCustom(name, bodyPart, category) },
            onDismiss = { creating = false },
        )
    }
}

/** Search field, body-part chips and the exercise list; used by the Exercises tab and the exercise picker. */
@Composable
fun ExerciseBrowser(
    query: String,
    state: ExercisesUiState,
    onQueryChange: (String) -> Unit,
    onBodyPartSelected: (BodyPart?) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (ExerciseEntity) -> Boolean = { false },
    onExerciseClick: ((ExerciseEntity) -> Unit)? = null,
) {
    val vietnamese = useVietnameseNames()

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_exercises)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.bodyPart == null,
                    onClick = { onBodyPartSelected(null) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
            items(BodyPart.entries) { part ->
                FilterChip(
                    selected = state.bodyPart == part,
                    onClick = { onBodyPartSelected(part) },
                    label = { Text(stringResource(part.label)) },
                )
            }
        }
        if (state.exercises.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_exercises), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.exercises, key = { it.id }) { exercise ->
                    val rowModifier = if (onExerciseClick != null) {
                        Modifier.clickable { onExerciseClick(exercise) }
                    } else {
                        Modifier
                    }
                    val name = exercise.displayName(vietnamese)
                    ListItem(
                        modifier = rowModifier,
                        headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = {
                            val bodyPart = stringResource(BodyPart.fromKey(exercise.bodyPart).label)
                            Text(if (exercise.isCustom) "$bodyPart · ${stringResource(R.string.custom_exercise)}" else bodyPart)
                        },
                        trailingContent = if (isSelected(exercise)) {
                            { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
