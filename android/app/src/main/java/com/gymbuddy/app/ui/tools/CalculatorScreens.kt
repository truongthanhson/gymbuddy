package com.gymbuddy.app.ui.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.defaultBar
import com.gymbuddy.app.domain.defaultPlates
import com.gymbuddy.app.domain.estimatedOneRepMax
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.domain.plateLoad
import com.gymbuddy.app.domain.repMaxWeight
import com.gymbuddy.app.ui.common.LocalAppSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/** Rep counts shown in the 1RM calculator's table. */
private val REP_MAX_ROWS = (1..12).toList()

private fun parseNumber(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun NumberField(@StringRes label: Int, value: String, decimal: Boolean, suffix: String?, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValue(if (decimal) input.filter { it.isDigit() || it == '.' || it == ',' } else input.filter(Char::isDigit))
        },
        label = { Text(stringResource(label)) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Which plates to load on each side of the bar for a target weight, in the user's unit. */
@Composable
fun PlateCalculatorScreen(onBack: () -> Unit) {
    val unit = LocalAppSettings.current.weightUnit
    var target by rememberSaveable { mutableStateOf("") }
    var bar by rememberSaveable(unit) { mutableStateOf(formatWeight(unit.defaultBar)) }

    ToolScaffold(R.string.plate_calculator, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(R.string.target_weight, target, decimal = true, suffix = unit.symbol, modifier = Modifier.weight(1f)) { target = it }
            NumberField(R.string.bar_weight, bar, decimal = true, suffix = unit.symbol, modifier = Modifier.weight(1f)) { bar = it }
        }
        val targetValue = parseNumber(target) ?: return@ToolScaffold
        val load = plateLoad(targetValue, parseNumber(bar) ?: 0.0, unit.defaultPlates)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plates_per_side), style = MaterialTheme.typography.titleMedium)
                if (load.perSide.isEmpty()) {
                    Text(stringResource(R.string.no_plates_needed))
                } else {
                    load.perSide.groupingBy { it }.eachCount().forEach { (plate, count) ->
                        Text("${formatWeight(plate)} ${unit.symbol} × $count", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(stringResource(R.string.loaded_weight, "${formatWeight(load.loaded)} ${unit.symbol}"))
                if (abs(load.remainder) >= 0.01) {
                    Text(
                        stringResource(R.string.plate_remainder, "${formatWeight(abs(load.remainder))} ${unit.symbol}"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Estimated one-rep max from any set, plus what that predicts for 1–12 reps. */
@Composable
fun OneRepMaxCalculatorScreen(onBack: () -> Unit) {
    val unit = LocalAppSettings.current.weightUnit
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }

    ToolScaffold(R.string.one_rep_max_calculator, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(R.string.weight, weight, decimal = true, suffix = unit.symbol, modifier = Modifier.weight(1f)) { weight = it }
            NumberField(R.string.col_reps, reps, decimal = false, suffix = null, modifier = Modifier.weight(1f)) { reps = it }
        }
        val weightValue = parseNumber(weight) ?: return@ToolScaffold
        val repsValue = reps.toIntOrNull()?.takeIf { it > 0 } ?: return@ToolScaffold
        // The formula is unit-free, so the typed value is used as is.
        val oneRepMax = estimatedOneRepMax(weightValue, repsValue)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.metric_one_rep_max), style = MaterialTheme.typography.titleMedium)
                Text("${formatWeight(round2(oneRepMax))} ${unit.symbol}", style = MaterialTheme.typography.displaySmall)
            }
        }
        Column {
            REP_MAX_ROWS.forEach { count ->
                val value = repMaxWeight(oneRepMax, count)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.rep_max_row, count)) },
                    supportingContent = { Text("${(value / oneRepMax * 100).roundToInt()}%") },
                    trailingContent = { Text("${formatWeight(round2(value))} ${unit.symbol}", style = MaterialTheme.typography.titleMedium) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun round2(value: Double) = kotlin.math.round(value * 100) / 100
