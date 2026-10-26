package com.gymbuddy.app.ui.buddy

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.R
import com.gymbuddy.app.data.coach.CoachRepository
import com.gymbuddy.app.data.coach.CoachSnapshot
import com.gymbuddy.app.domain.CoachInsight
import com.gymbuddy.app.domain.RecoveryStatus
import com.gymbuddy.app.domain.SetTarget
import com.gymbuddy.app.domain.TargetKind
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.useVietnameseNames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BuddyViewModel @Inject constructor(coach: CoachRepository) : ViewModel() {
    /** Null until the history has loaded. */
    val snapshot: StateFlow<CoachSnapshot?> = coach.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun CoachSnapshot.exerciseName(id: String): String {
    val (en, vi) = names[id] ?: return "?"
    return if (useVietnameseNames() && vi != null) vi else en
}

/** One of Buddy's tips as a sentence. */
@Composable
fun insightText(insight: CoachInsight, snapshot: CoachSnapshot): String {
    val unit = LocalAppSettings.current.weightUnit
    return when (insight) {
        is CoachInsight.Inactive -> stringResource(R.string.buddy_inactive, insight.days.toInt())
        is CoachInsight.Deload ->
            stringResource(R.string.buddy_deload, snapshot.exerciseName(insight.exerciseId), unit.format(insight.weightKg), insight.reps)
        is CoachInsight.VolumeSpike ->
            stringResource(R.string.buddy_volume_spike, stringResource(insight.bodyPart.label).lowercase(), insight.sets)
        is CoachInsight.Neglected ->
            stringResource(R.string.buddy_neglected, stringResource(insight.bodyPart.label).lowercase(), insight.days.toInt())
        is CoachInsight.Streak -> stringResource(R.string.buddy_streak, insight.weeks)
    }
}

/** Buddy's card on the start screen: the most urgent tips and which muscles are rested. */
@Composable
fun BuddyTodayCard(
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: (() -> Unit)? = null,
    viewModel: BuddyViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val current = snapshot ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.buddy_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            when {
                current.workoutCount == 0 -> Text(stringResource(R.string.buddy_first_workout))
                current.insights.isEmpty() -> Text(stringResource(R.string.buddy_all_good))
                else -> {
                    val shown = if (expanded) current.insights else current.insights.take(1)
                    shown.forEach { Text(insightText(it, current)) }
                    if (current.insights.size > 1) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(0.dp)) {
                            Text(if (expanded) stringResource(R.string.buddy_less) else stringResource(R.string.buddy_more, current.insights.size - 1))
                        }
                    }
                }
            }
            if (current.recovery.isNotEmpty()) {
                HorizontalDivider()
                val ready = current.recovery.filter { it.status == RecoveryStatus.READY }
                val recovering = current.recovery.filter { it.status == RecoveryStatus.RECOVERING }
                if (ready.isNotEmpty()) {
                    val parts = ready.map { stringResource(it.bodyPart.label) }.joinToString(", ")
                    Text(stringResource(R.string.buddy_ready, parts), style = MaterialTheme.typography.bodyMedium)
                }
                if (recovering.isNotEmpty()) {
                    val parts = recovering.map { stringResource(R.string.buddy_hours, stringResource(it.bodyPart.label), it.hoursSince.toInt()) }.joinToString(", ")
                    Text(
                        stringResource(R.string.buddy_recovering, parts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAsk, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.buddy_ask), modifier = Modifier.padding(start = 8.dp))
                }
                if (onReport != null && current.workoutCount > 0) {
                    OutlinedButton(onClick = onReport, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.buddy_weekly_report))
                    }
                }
            }
        }
    }
}

@Composable
private fun targetKindLabel(kind: TargetKind): String = stringResource(
    when (kind) {
        TargetKind.INCREASE -> R.string.buddy_target_increase
        TargetKind.REPEAT -> R.string.buddy_target_repeat
        TargetKind.DELOAD -> R.string.buddy_target_deload
    },
)

/** "Buddy: 62.5 kg × 8 · add weight" under an exercise while logging. */
@Composable
fun BuddyTargetHint(exerciseId: String, viewModel: BuddyViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val target: SetTarget = snapshot?.targets?.get(exerciseId) ?: return
    val unit = LocalAppSettings.current.weightUnit
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
        Text(
            stringResource(R.string.buddy_target, unit.format(target.weightKg), target.reps) + " · " + targetKindLabel(target.kind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The targets for the exercises of a workout just finished, for its summary. */
@Composable
fun BuddyNextTime(exerciseIds: List<String>, viewModel: BuddyViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val current = snapshot ?: return
    val targets = exerciseIds.distinct().mapNotNull { id -> current.targets[id]?.let { id to it } }
    if (targets.isEmpty()) return
    val unit = LocalAppSettings.current.weightUnit
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.buddy_next_time),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        targets.forEach { (id, target) ->
            Text(
                stringResource(R.string.buddy_next_time_line, current.exerciseName(id), unit.format(target.weightKg), target.reps) +
                    " · " + targetKindLabel(target.kind),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
