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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.gymbuddy.app.domain.startOfWeekMillis
import com.gymbuddy.app.domain.workoutsPerWeek
import com.gymbuddy.app.ui.history.HistoryViewModel
import com.gymbuddy.app.ui.settings.AccountViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHART_WEEKS = 8

/** Strong's Profile tab: who you are, how many workouts you have done, and workouts per week. Settings are behind the gear. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    historyViewModel: HistoryViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val workouts by historyViewModel.workouts.collectAsStateWithLifecycle()
    val session by accountViewModel.session.collectAsStateWithLifecycle()
    val starts = workouts.orEmpty().map { it.workout.startedAt }
    val zone = ZoneId.systemDefault()
    val now = remember { System.currentTimeMillis() }
    val counts = remember(starts) { workoutsPerWeek(starts, now, zone, CHART_WEEKS) }

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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.workouts_per_week), style = MaterialTheme.typography.titleMedium)
                WeekBars(counts, firstWeekStart = startOfWeekMillis(now, zone) - (CHART_WEEKS - 1) * WEEK_MILLIS, zone = zone)
            }
        }
    }
}

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

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
