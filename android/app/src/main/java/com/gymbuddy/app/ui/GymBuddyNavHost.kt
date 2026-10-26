package com.gymbuddy.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymbuddy.app.R
import com.gymbuddy.app.ui.exercise.ExerciseDetailScreen
import com.gymbuddy.app.ui.exercises.ExercisesScreen
import com.gymbuddy.app.ui.exercises.ExercisePickerScreen
import com.gymbuddy.app.ui.history.CalendarScreen
import com.gymbuddy.app.ui.history.HistoryScreen
import com.gymbuddy.app.ui.history.WorkoutEditScreen
import com.gymbuddy.app.ui.measurements.MeasurementDetailScreen
import com.gymbuddy.app.ui.measurements.MeasurementsScreen
import com.gymbuddy.app.ui.settings.SettingsScreen
import com.gymbuddy.app.ui.templates.TemplateEditorScreen
import com.gymbuddy.app.ui.tools.OneRepMaxCalculatorScreen
import com.gymbuddy.app.ui.tools.PlateCalculatorScreen
import com.gymbuddy.app.ui.tools.WarmupCalculatorScreen
import com.gymbuddy.app.ui.workout.WorkoutTab
import com.gymbuddy.app.ui.workout.WorkoutViewModel
import kotlinx.serialization.Serializable

@Serializable object HistoryRoute
@Serializable object WorkoutRoute
@Serializable object ExercisesRoute
@Serializable object SettingsRoute
@Serializable object MeasurementsRoute
/** With [replaceWorkoutExerciseId] the picker picks one exercise to put in that one's place. */
@Serializable data class ExercisePickerRoute(val workoutId: String? = null, val replaceWorkoutExerciseId: String? = null)
@Serializable data class TemplateEditorRoute(val templateId: String? = null)
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
@Serializable object PlateCalculatorRoute
@Serializable object OneRepMaxCalculatorRoute
@Serializable object WarmupCalculatorRoute
@Serializable data class MeasurementDetailRoute(val kind: String)
@Serializable data class WorkoutEditRoute(val workoutId: String)
@Serializable object CalendarRoute

/** Picker → template editor: the picked exercise ids, set on the editor's back stack entry. */
private const val PICKED_EXERCISES = "picked_exercises"

private enum class Tab(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    History(HistoryRoute, R.string.tab_history, Icons.Filled.History),
    Workout(WorkoutRoute, R.string.tab_workout, Icons.Filled.Add),
    Exercises(ExercisesRoute, R.string.tab_exercises, Icons.Filled.FitnessCenter),
    Measure(MeasurementsRoute, R.string.tab_measure, Icons.Filled.MonitorWeight),
    Settings(SettingsRoute, R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun GymBuddyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        // Type-safe routes of argument-less objects use the class's qualified name as the route.
                        selected = destination?.route == tab.route::class.qualifiedName,
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = WorkoutRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HistoryRoute> {
                HistoryScreen(
                    onOpenWorkout = { navController.navigate(WorkoutEditRoute(it)) },
                    onOpenCalendar = { navController.navigate(CalendarRoute) },
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    onOpenWorkout = { navController.navigate(WorkoutEditRoute(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<WorkoutEditRoute> { entry ->
                val workoutId = entry.toRoute<WorkoutEditRoute>().workoutId
                // The Workout tab's view model, so saving and deleting finish after this screen is popped.
                val workoutEntry = remember(entry) { navController.getBackStackEntry<WorkoutRoute>() }
                WorkoutEditScreen(
                    workoutId = workoutId,
                    onAddExercises = { navController.navigate(ExercisePickerRoute(workoutId)) },
                    onReplaceExercise = {
                        navController.navigate(ExercisePickerRoute(workoutId, replaceWorkoutExerciseId = it))
                    },
                    onClose = { navController.popBackStack() },
                    onRepeated = {
                        navController.popBackStack()
                        navController.navigateToTab(WorkoutRoute)
                    },
                    viewModel = hiltViewModel(workoutEntry),
                )
            }
            composable<WorkoutRoute> {
                WorkoutTab(
                    onAddExercises = { navController.navigate(ExercisePickerRoute(it)) },
                    onReplaceExercise = { workoutId, workoutExerciseId ->
                        navController.navigate(ExercisePickerRoute(workoutId, replaceWorkoutExerciseId = workoutExerciseId))
                    },
                    onEditTemplate = { navController.navigate(TemplateEditorRoute(it)) },
                )
            }
            composable<TemplateEditorRoute> { entry ->
                val picked by entry.savedStateHandle.getStateFlow<ArrayList<String>?>(PICKED_EXERCISES, null)
                    .collectAsStateWithLifecycle()
                TemplateEditorScreen(
                    pickedExercises = picked,
                    onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISES] = null },
                    onAddExercises = { navController.navigate(ExercisePickerRoute()) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable<ExercisesRoute> { ExercisesScreen(onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) }) }
            composable<ExerciseDetailRoute> { ExerciseDetailScreen(onBack = { navController.popBackStack() }) }
            composable<MeasurementsRoute> {
                MeasurementsScreen(onOpenKind = { navController.navigate(MeasurementDetailRoute(it.key)) })
            }
            composable<MeasurementDetailRoute> { MeasurementDetailScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenPlateCalculator = { navController.navigate(PlateCalculatorRoute) },
                    onOpenOneRepMaxCalculator = { navController.navigate(OneRepMaxCalculatorRoute) },
                    onOpenWarmupCalculator = { navController.navigate(WarmupCalculatorRoute) },
                )
            }
            composable<PlateCalculatorRoute> { PlateCalculatorScreen(onBack = { navController.popBackStack() }) }
            composable<OneRepMaxCalculatorRoute> { OneRepMaxCalculatorScreen(onBack = { navController.popBackStack() }) }
            composable<WarmupCalculatorRoute> { WarmupCalculatorScreen(onBack = { navController.popBackStack() }) }
            composable<ExercisePickerRoute> { entry ->
                val route = entry.toRoute<ExercisePickerRoute>()
                val workoutId = route.workoutId
                val replaceId = route.replaceWorkoutExerciseId
                // Scope the view model to the Workout tab so the insert survives popping this screen.
                val workoutEntry = remember(entry) { navController.getBackStackEntry<WorkoutRoute>() }
                val workoutViewModel: WorkoutViewModel = hiltViewModel(workoutEntry)
                ExercisePickerScreen(
                    single = replaceId != null,
                    onDone = { ids ->
                        if (replaceId != null) {
                            ids.firstOrNull()?.let { workoutViewModel.replaceExercise(replaceId, it) }
                        } else if (workoutId != null) {
                            workoutViewModel.addExercises(workoutId, ids)
                        } else {
                            navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISES, ArrayList(ids))
                        }
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
