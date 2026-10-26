package com.gymbuddy.app.ui.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.canAddTemplate
import com.gymbuddy.app.ui.buddy.BuddyTodayCard
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.templates.TemplateActions
import com.gymbuddy.app.ui.templates.TemplatesUiState
import com.gymbuddy.app.ui.templates.TemplatesViewModel
import com.gymbuddy.app.ui.templates.templateItems
import com.gymbuddy.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutTab(
    onAddExercises: (workoutId: String) -> Unit,
    onReplaceExercise: (workoutId: String, workoutExerciseId: String) -> Unit,
    onEditTemplate: (templateId: String?) -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenBuddy: () -> Unit,
    onOpenBuddyReport: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
    templatesViewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val current = state
    summary?.let { WorkoutSummaryDialog(it, onDismiss = viewModel::summaryShown) }
    when {
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        current.workout == null -> {
            val defaultName = stringResource(R.string.default_workout_name)
            // The rest timer needs notifications; ask when the first workout starts.
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            val askNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val templates by templatesViewModel.state.collectAsStateWithLifecycle()
            // Free users keep a few routines, like Strong; Pro removes the limit.
            val canAdd = canAddTemplate(templates.templates.size, LocalIsPro.current)
            StartWorkoutScreen(
                templates = templates,
                onOpenBuddy = onOpenBuddy,
                onOpenBuddyReport = onOpenBuddyReport,
                onStartEmpty = {
                    askNotifications()
                    viewModel.startEmptyWorkout(defaultName)
                },
                actions = TemplateActions(
                    onStart = {
                        askNotifications()
                        templatesViewModel.startWorkout(it.template.id) {}
                    },
                    onEdit = { onEditTemplate(it) },
                    onNew = { if (canAdd) onEditTemplate(null) else onOpenPaywall() },
                    onDuplicate = { id, suffix -> if (canAdd) templatesViewModel.duplicate(id, suffix) else onOpenPaywall() },
                    onDelete = templatesViewModel::delete,
                    onMove = templatesViewModel::move,
                    onCreateFolder = templatesViewModel::createFolder,
                    onRenameFolder = templatesViewModel::renameFolder,
                    onDeleteFolder = templatesViewModel::deleteFolder,
                ),
            )
        }
        else -> ActiveWorkoutScreen(
            workout = current.workout,
            viewModel = viewModel,
            onAddExercises = { onAddExercises(current.workout.workout.id) },
            onReplaceExercise = { onReplaceExercise(current.workout.workout.id, it) },
        )
    }
}

@Composable
private fun StartWorkoutScreen(
    templates: TemplatesUiState,
    onOpenBuddy: () -> Unit,
    onOpenBuddyReport: () -> Unit,
    onStartEmpty: () -> Unit,
    actions: TemplateActions,
) {
    val vietnamese = useVietnameseNames()
    val today = remember { DateFormat.getDateInstance(DateFormat.FULL).format(Date()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.md)) {
                Text(today, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.quick_start), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { BuddyTodayCard(onAsk = onOpenBuddy, onReport = onOpenBuddyReport) }
        item {
            Button(
                onClick = onStartEmpty,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.start_empty_workout), style = MaterialTheme.typography.titleMedium)
            }
        }
        templateItems(templates, vietnamese, actions)
    }
}
