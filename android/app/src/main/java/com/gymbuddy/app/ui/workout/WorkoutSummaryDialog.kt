package com.gymbuddy.app.ui.workout

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.ui.buddy.BuddyNextTime
import com.gymbuddy.app.data.workout.WorkoutSetEntity
import com.gymbuddy.app.data.workout.WorkoutWithExercises
import com.gymbuddy.app.domain.ShareExercise
import com.gymbuddy.app.domain.ShareSet
import com.gymbuddy.app.domain.SetValues
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.formatElapsed
import com.gymbuddy.app.domain.volumeKg
import com.gymbuddy.app.domain.workoutShareText
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import java.text.DateFormat
import java.util.Date

private fun WorkoutSetEntity.toShareSet() = ShareSet(setType.name.lowercase(), weightKg, reps, durationSec, distanceM)

/** The workout's completed sets per exercise, for sharing. */
fun WorkoutWithExercises.shareExercises(vietnamese: Boolean): List<ShareExercise> = exercises.map { e ->
    ShareExercise(e.exercise.displayName(vietnamese), e.exercise?.category.orEmpty(), e.sets.filter { it.completed }.map { it.toShareSet() })
}

fun shareDate(startedAt: Long): String = DateFormat.getDateInstance(DateFormat.FULL).format(Date(startedAt))

/** Opens the share sheet with the workout as text. */
fun shareWorkout(context: Context, workout: WorkoutWithExercises, vietnamese: Boolean, unit: WeightUnit, chooserTitle: String) {
    val text = workoutShareText(workout.workout.name, shareDate(workout.workout.startedAt), workout.shareExercises(vietnamese), unit)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Strong's "workout complete" screen: the workout's number, time, volume and records, with sharing. */
@Composable
fun WorkoutSummaryDialog(summary: WorkoutSummary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val unit = LocalAppSettings.current.weightUnit
    val vietnamese = useVietnameseNames()
    val workout = summary.workout.workout
    val exercises = summary.workout.shareExercises(vietnamese)
    val completedSets = summary.workout.exercises.flatMap { e -> e.sets.filter { it.completed } }
    val volume = volumeKg(completedSets.map { SetValues(it.weightKg, it.reps, completed = true) })
    val duration = formatElapsed(((workout.endedAt ?: workout.startedAt) - workout.startedAt) / 1_000)
    val date = shareDate(workout.startedAt)
    val shareTitle = stringResource(R.string.share_workout)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
        title = { Text(stringResource(R.string.workout_complete), textAlign = TextAlign.Center) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.workout_number, summary.number),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(workout.name, style = MaterialTheme.typography.titleMedium)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(stringResource(R.string.duration), duration)
                    Stat(stringResource(R.string.volume), unit.format(volume))
                    Stat(stringResource(R.string.records), summary.records.toString())
                }
                HorizontalDivider()
                exercises.filter { it.sets.isNotEmpty() }.forEach { exercise ->
                    Text(
                        pluralStringResource(R.plurals.sets_of_exercise, exercise.sets.size, exercise.sets.size, exercise.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                BuddyNextTime(summary.workout.exercises.map { it.workoutExercise.exerciseId })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = { shareWorkout(context, summary.workout, vietnamese, unit, shareTitle) }) {
                Text(stringResource(R.string.share))
            }
        },
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
