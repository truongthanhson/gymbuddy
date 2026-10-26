package com.gymbuddy.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

@Composable
fun WorkoutTab(
    onAddExercises: (workoutId: String) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    when {
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        current.workout == null -> {
            val defaultName = stringResource(R.string.default_workout_name)
            StartWorkoutScreen(onStartEmpty = { viewModel.startEmptyWorkout(defaultName) })
        }
        else -> ActiveWorkoutScreen(
            workout = current.workout,
            viewModel = viewModel,
            onAddExercises = { onAddExercises(current.workout.workout.id) },
        )
    }
}

@Composable
private fun StartWorkoutScreen(onStartEmpty: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.quick_start), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onStartEmpty, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_empty_workout))
        }
    }
}
