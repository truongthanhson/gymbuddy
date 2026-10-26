package com.gymbuddy.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.exercise.ExerciseEntity
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames

@Composable
fun ExercisesScreen(viewModel: ExercisesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseBrowser(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBodyPartSelected = viewModel::onBodyPartSelected,
    )
}

/** Search field, body-part chips and the exercise list; used by the Exercises tab and the exercise picker. */
@Composable
fun ExerciseBrowser(
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
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_exercises)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
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
                Text(stringResource(R.string.no_exercises))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.exercises, key = { it.id }) { exercise ->
                    val rowModifier = if (onExerciseClick != null) {
                        Modifier.clickable { onExerciseClick(exercise) }
                    } else {
                        Modifier
                    }
                    ListItem(
                        modifier = rowModifier,
                        headlineContent = { Text(exercise.displayName(vietnamese)) },
                        supportingContent = { Text(stringResource(BodyPart.fromKey(exercise.bodyPart).label)) },
                        trailingContent = if (isSelected(exercise)) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
