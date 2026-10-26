package com.gymbuddy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.workout.SetType
import com.gymbuddy.app.domain.BodyPartSet
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.domain.setsPerBodyPart
import com.gymbuddy.app.ui.common.LineChart
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.domain.startOfWeekMillis
import com.gymbuddy.app.domain.workoutsPerWeek
import com.gymbuddy.app.ui.history.HistoryViewModel
import com.gymbuddy.app.ui.settings.AccountViewModel
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHART_WEEKS = 8

/** Strong's Profile tab: who you are, how many workouts you have done, and workouts per week. Settings are behind the gear. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    historyViewModel: HistoryViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val workouts by historyViewModel.workouts.collectAsStateWithLifecycle()
    val session by accountViewModel.session.collectAsStateWithLifecycle()
    val starts = workouts.orEmpty().map { it.workout.startedAt }
    val zone = ZoneId.systemDefault()
    val now = remember { System.currentTimeMillis() }
    val counts = remember(starts) { workoutsPerWeek(starts, now, zone, CHART_WEEKS) }
    val bodyWeights by profileViewModel.bodyWeights.collectAsStateWithLifecycle()
    val bodyParts = remember(workouts) {
        val sets = workouts.orEmpty().flatMap { item ->
            item.exercises.flatMap { exercise ->
                exercise.sets.filter { it.completed }.map {
                    BodyPartSet(exercise.exercise?.bodyPart.orEmpty(), item.workout.startedAt, it.setType == SetType.WARMUP)
                }
            }
        }
        setsPerBodyPart(sets, now - MUSCLE_DAYS * DAY_MILLIS)
    }
    val isPro = LocalIsPro.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session?.email ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(R.plurals.workouts_count, starts.size, starts.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(onClick = onOpenPro, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (LocalIsPro.current) R.string.pro_active_short else R.string.pro_upgrade_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.workouts_per_week), style = MaterialTheme.typography.titleMedium)
                WeekBars(counts, firstWeekStart = startOfWeekMillis(now, zone) - (CHART_WEEKS - 1) * WEEK_MILLIS, zone = zone)
            }
        }
        if (isPro) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sets_per_muscle_group, MUSCLE_DAYS), style = MaterialTheme.typography.titleMedium)
                    if (bodyParts.isEmpty()) {
                        Text(stringResource(R.string.no_sets_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val most = bodyParts.firstOrNull()?.second ?: 1
                    bodyParts.forEach { (part, sets) -> BodyPartBar(stringResource(part.label), sets, sets.toFloat() / most) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.measure_weight), style = MaterialTheme.typography.titleMedium)
                    if (bodyWeights.size < 2) {
                        Text(stringResource(R.string.not_enough_weights), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val unit = LocalAppSettings.current.weightUnit
                        val dates = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
                        LineChart(
                            points = bodyWeights.map { it.measuredAt to unit.display(it.value) },
                            formatValue = { "${formatWeight(it)} ${unit.symbol}" },
                            formatTime = { dates.format(Date(it)) },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                        )
                    }
                }
            }
        } else {
            Card(onClick = onOpenPro, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.pro_dashboard_locked), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val MUSCLE_DAYS = 30

@Composable
private fun BodyPartBar(label: String, sets: Int, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Box(Modifier.weight(1f).height(12.dp)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        Text(sets.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WeekBars(counts: List<Int>, firstWeekStart: Long, zone: ZoneId) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val format = remember { DateTimeFormatter.ofPattern("d/M") }
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.forEachIndexed { index, count ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val fraction = (count.toFloat() / max).coerceIn(0.02f, 1f)
                // Only weighted children share the free height, so a spacer takes what the bar doesn't.
                Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(fraction)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                )
                // Approximate across DST changes; only the label uses it.
                val weekStart = Instant.ofEpochMilli(firstWeekStart + index * WEEK_MILLIS).atZone(zone).toLocalDate()
                Text(
                    weekStart.format(format),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
