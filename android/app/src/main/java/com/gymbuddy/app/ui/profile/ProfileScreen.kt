package com.gymbuddy.app.ui.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.charts.AnimatedBarChart
import com.gymbuddy.app.ui.charts.InteractiveLineChart
import com.gymbuddy.app.ui.common.Card
import com.gymbuddy.app.ui.common.GymCard
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.ui.common.color
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.common.useVietnameseNames
import com.gymbuddy.app.ui.settings.AccountViewModel
import com.gymbuddy.app.ui.theme.GymBuddyTheme
import com.gymbuddy.app.ui.theme.Spacing
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale

/**
 * Strong's Profile tab: who you are and how your training is going. The numbers come ready-made from
 * [ProfileViewModel]; this screen only lays them out. Settings are behind the gear.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    accountViewModel: AccountViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by accountViewModel.session.collectAsStateWithLifecycle()
    val stats by profileViewModel.stats.collectAsStateWithLifecycle()
    val bodyWeights by profileViewModel.bodyWeights.collectAsStateWithLifecycle()
    val isPro = LocalIsPro.current
    val unit = LocalAppSettings.current.weightUnit

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
                }
            }
        }
        item(key = "account") {
            GymCard {
                Text(session?.email ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                val first = stats?.firstWorkoutAt
                if (first != null) {
                    val since = remember(first) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(first)) }
                    Text(stringResource(R.string.training_since, since), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "pro") {
            Card(onClick = onOpenPro, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = Spacing.md).weight(1f)) {
                        Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (isPro) R.string.pro_active_short else R.string.pro_upgrade_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val s = stats ?: return@LazyColumn

        item(key = "overview") {
            GymCard {
                StatPair(
                    formatCompact(s.totals.workouts.toDouble()),
                    stringResource(R.string.stat_workouts),
                    stringResource(R.string.hours_short, (s.totals.durationSeconds / 3600).toInt()),
                    stringResource(R.string.stat_total_time),
                )
                StatPair(
                    "${formatCompact(unit.display(s.totals.volumeKg))} ${unit.symbol}",
                    stringResource(R.string.stat_total_volume),
                    formatCompact(s.totals.sets.toDouble()),
                    stringResource(R.string.stat_total_sets),
                )
            }
        }
        item(key = "consistency") {
            GymCard {
                Text(stringResource(R.string.consistency), style = MaterialTheme.typography.titleMedium)
                StatPair(
                    pluralStringResource(R.plurals.weeks_count, s.streaks.current, s.streaks.current),
                    stringResource(R.string.streak_current),
                    pluralStringResource(R.plurals.weeks_count, s.streaks.best, s.streaks.best),
                    stringResource(R.string.streak_best),
                )
                Heatmap(s.heatmap, s.today, Modifier.padding(top = Spacing.sm))
                Text(
                    stringResource(R.string.heatmap_caption, HEATMAP_WEEKS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "perWeek") {
            GymCard {
                Text(stringResource(R.string.workouts_per_week), style = MaterialTheme.typography.titleMedium)
                WeekBars(
                    values = s.weekCounts,
                    firstWeekStart = s.firstWeekStart,
                    tooltipValue = { pluralStringResource(R.plurals.workouts_count, it, it) },
                )
            }
        }

        if (!isPro) {
            item(key = "locked") {
                Card(onClick = onOpenPro, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.pro_dashboard_locked), modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "volume") {
            GymCard {
                Text(stringResource(R.string.volume_per_week), style = MaterialTheme.typography.titleMedium)
                val values = remember(s.weeklyVolumeKg, unit) { s.weeklyVolumeKg.map { unit.display(it).toInt() } }
                WeekBars(
                    values = values,
                    firstWeekStart = s.firstWeekStart - (VOLUME_WEEKS - CHART_WEEKS) * WEEK_MILLIS,
                    tooltipValue = { "${formatCompact(it.toDouble())} ${unit.symbol}" },
                    showAverage = false,
                    valueLabel = ::formatShort,
                )
            }
        }
        item(key = "muscles") {
            var days by rememberSaveable { mutableIntStateOf(30) }
            val parts = s.muscleGroups[days].orEmpty()
            GymCard {
                Text(stringResource(R.string.sets_per_muscle_group_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MUSCLE_PERIODS.forEach { period ->
                        FilterChip(
                            selected = days == period,
                            onClick = { days = period },
                            label = { Text(stringResource(R.string.days_short, period)) },
                        )
                    }
                }
                if (parts.isEmpty()) {
                    Text(stringResource(R.string.no_sets_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val most = parts.firstOrNull()?.second ?: 1
                parts.forEachIndexed { index, (part, sets) ->
                    BodyPartBar(stringResource(part.label), sets, sets.toFloat() / most, index, part.color(), key = days)
                }
            }
        }
        item(key = "records") {
            val vietnamese = useVietnameseNames()
            val dates = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
            GymCard {
                Text(stringResource(R.string.personal_records), style = MaterialTheme.typography.titleMedium)
                if (s.recentRecords.isEmpty()) {
                    Text(stringResource(R.string.no_records_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        pluralStringResource(R.plurals.records_total, s.recordCount, s.recordCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                s.recentRecords.forEach { row ->
                    StatRow(
                        title = row.exercise.displayName(vietnamese),
                        subtitle = dates.format(Date(row.record.startedAt)),
                        value = "${formatWeight(unit.display(row.record.weightKg))} ${unit.symbol} × ${row.record.reps}",
                        detail = stringResource(R.string.estimated_one_rep_max_short, weightText(row.record.estimatedOneRepMax, unit)),
                    )
                }
            }
        }
        item(key = "top") {
            val vietnamese = useVietnameseNames()
            GymCard {
                Text(stringResource(R.string.top_exercises), style = MaterialTheme.typography.titleMedium)
                if (s.topExercises.isEmpty()) {
                    Text(stringResource(R.string.no_sets_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.topExercises.forEach { row ->
                    val recent = row.top.recentOneRepMax
                    val previous = row.top.previousOneRepMax
                    val change = if (recent != null && previous != null) unit.display(recent) - unit.display(previous) else null
                    StatRow(
                        title = row.exercise.displayName(vietnamese),
                        subtitle = pluralStringResource(R.plurals.workouts_count, row.top.workouts, row.top.workouts),
                        value = recent?.let { stringResource(R.string.estimated_one_rep_max_short, weightText(it, unit)) } ?: "",
                        detail = change?.let { stringResource(R.string.vs_previous_months, signed(it, unit)) },
                        detailColor = when {
                            change == null || change == 0.0 -> null
                            change > 0 -> GymBuddyTheme.colors.success
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
        item(key = "habits") {
            GymCard {
                Text(stringResource(R.string.training_habits), style = MaterialTheme.typography.titleMedium)
                val habits = s.habits
                StatRow(
                    title = stringResource(R.string.average_duration),
                    value = stringResource(R.string.minutes_short, (habits.averageDurationSeconds / 60).toInt()),
                )
                habits.favoriteWeekday?.let { day ->
                    StatRow(
                        title = stringResource(R.string.favorite_day),
                        value = DayOfWeek.of(day).getDisplayName(DayStyle.FULL, Locale.getDefault())
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    )
                }
                habits.averageRestDays?.let { rest ->
                    StatRow(
                        title = stringResource(R.string.average_rest_days),
                        value = stringResource(R.string.days_decimal, "%.1f".format(rest)),
                    )
                }
            }
        }
        item(key = "bodyWeight") {
            GymCard {
                Text(stringResource(R.string.measure_weight), style = MaterialTheme.typography.titleMedium)
                if (bodyWeights.size < 2) {
                    Text(stringResource(R.string.not_enough_weights), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val dates = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
                    val points = remember(bodyWeights, unit) { bodyWeights.map { it.measuredAt to unit.display(it.value) } }
                    InteractiveLineChart(
                        points = points,
                        formatValue = { "${formatWeight(it)} ${unit.symbol}" },
                        formatTime = { dates.format(Date(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        chartHeight = 160.dp,
                    )
                }
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val WEEK_MILLIS = 7 * DAY_MILLIS

/** 1234 → "1,234", 245300 → "245.3k", 1234567 → "1.23M", in the user's locale. */
private fun formatCompact(value: Double): String = when {
    value < 10_000 -> "%,d".format(value.toLong())
    value < 1_000_000 -> "%.1fk".format(value / 1_000)
    else -> "%.2fM".format(value / 1_000_000)
}

/** "850", "4.2k", "26k", "1.2M": short enough to sit above a narrow bar. */
private fun formatShort(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 9_950 -> "%.1fk".format(value / 1_000.0)
    value < 999_500 -> "${(value + 500) / 1_000}k"
    else -> "%.1fM".format(value / 1_000_000.0)
}

private fun weightText(kg: Double, unit: WeightUnit): String = "${formatWeight(Math.round(unit.display(kg) * 10) / 10.0)} ${unit.symbol}"

private fun signed(value: Double, unit: WeightUnit): String {
    val rounded = Math.round(value * 10) / 10.0
    return (if (rounded > 0) "+" else if (rounded < 0) "−" else "±") + "${formatWeight(kotlin.math.abs(rounded))} ${unit.symbol}"
}

/** Two big numbers side by side, each with a caption. */
@Composable
private fun StatPair(firstValue: String, firstLabel: String, secondValue: String, secondLabel: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        StatCell(firstValue, firstLabel, Modifier.weight(1f))
        StatCell(secondValue, secondLabel, Modifier.weight(1f))
    }
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A list line: name and a gray subtitle on the left, a value and an optional detail on the right. */
@Composable
private fun StatRow(
    title: String,
    value: String,
    subtitle: String? = null,
    detail: String? = null,
    detailColor: Color? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge.merge(Tabular))
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.merge(Tabular),
                    color = detailColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * GitHub-style grid of the last weeks: one column per week, Monday on top, darker for more workouts.
 * Days after [today] are left empty.
 */
@Composable
private fun Heatmap(days: List<Int>, today: Int, modifier: Modifier = Modifier) {
    val weeks = days.size / 7
    val active = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.heatmap_caption, weeks)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(weeks / 7f)
            .semantics { contentDescription = description },
    ) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap * (weeks - 1)) / weeks
        val radius = CornerRadius(cell / 4)
        for (index in 0..minOf(today, days.lastIndex)) {
            val color = when (days[index]) {
                0 -> empty
                1 -> active.copy(alpha = 0.6f)
                else -> active
            }
            drawRoundRect(
                color = color,
                topLeft = Offset((index / 7) * (cell + gap), (index % 7) * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = radius,
            )
        }
    }
}

/** One muscle group's bar; the bars grow in one after another, [index] sets the delay, [key] restarts it. */
@Composable
private fun BodyPartBar(label: String, sets: Int, fraction: Float, index: Int, color: Color, key: Any) {
    var started by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { started = true }
    val shown by animateFloatAsState(
        if (started) fraction.coerceIn(0.02f, 1f) else 0f,
        tween(durationMillis = 600, delayMillis = index * 40, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
        ) {
            if (shown > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(shown)
                        .background(color, RoundedCornerShape(6.dp)),
                )
            }
        }
        Text(sets.toString(), style = MaterialTheme.typography.labelLarge.merge(Tabular))
    }
}

@Composable
private fun WeekBars(
    values: List<Int>,
    firstWeekStart: Long,
    tooltipValue: @Composable (Int) -> String,
    showAverage: Boolean = true,
    valueLabel: (Int) -> String = { it.toString() },
) {
    val zone = remember { ZoneId.systemDefault() }
    val format = remember { DateTimeFormatter.ofPattern("d/M") }
    // Approximate across DST changes; only the labels use it.
    val labels = remember(values, firstWeekStart) {
        values.indices.map { index ->
            Instant.ofEpochMilli(firstWeekStart + index * WEEK_MILLIS).atZone(zone).toLocalDate().format(format)
        }
    }
    val tooltips = values.mapIndexed { index, value ->
        stringResource(R.string.chart_week_tooltip, tooltipValue(value), labels[index])
    }
    val average = stringResource(R.string.chart_average, "%s")
    AnimatedBarChart(
        values = values,
        labels = labels,
        tooltip = { tooltips[it] },
        averageLabel = if (showAverage) ({ average.format("%.1f".format(it)) }) else null,
        valueLabel = valueLabel,
        description = tooltips.joinToString(", "),
        chartHeight = 180.dp,
    )
}
