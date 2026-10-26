package com.gymbuddy.app.ui.measurements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.MeasurementKind
import com.gymbuddy.app.domain.formatWeight
import com.gymbuddy.app.ui.common.LineChart
import com.gymbuddy.app.ui.common.LocalAppSettings
import java.text.DateFormat
import java.util.Date

private fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

/** The Measure tab: every kind with its latest value. */
@Composable
fun MeasurementsScreen(onOpenKind: (MeasurementKind) -> Unit, viewModel: MeasurementsViewModel = hiltViewModel()) {
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.weightUnit
    LazyColumn(Modifier.fillMaxSize()) {
        items(MeasurementKind.entries) { kind ->
            val entry = latest[kind]
            ListItem(
                modifier = Modifier.clickable { onOpenKind(kind) },
                headlineContent = { Text(stringResource(kind.label)) },
                supportingContent = entry?.let { { Text(formatDate(it.measuredAt)) } },
                trailingContent = {
                    Text(entry?.let { kind.format(it.value, unit) } ?: "—", style = MaterialTheme.typography.titleMedium)
                },
            )
            HorizontalDivider()
        }
    }
}

/** History and chart of one kind, with adding and deleting entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(onBack: () -> Unit, viewModel: MeasurementDetailViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val unit = LocalAppSettings.current.weightUnit
    val kind = viewModel.kind
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_measurement))
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_measurements), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
                if (entries.size >= 2) {
                    item {
                        LineChart(
                            points = entries.reversed().map { it.measuredAt to kind.display(it.value, unit) },
                            formatValue = { "${formatWeight(it)} ${kind.symbol(unit)}" },
                            formatTime = ::formatDate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(kind.format(entry.value, unit)) },
                        supportingContent = { Text(formatDate(entry.measuredAt)) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.delete(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        AddMeasurementDialog(
            kind = kind,
            initial = entries.firstOrNull()?.let { formatWeight(kind.display(it.value, unit)) } ?: "",
            symbol = kind.symbol(unit),
            onSave = { typed -> viewModel.add(kind.toStored(typed, unit)) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun AddMeasurementDialog(kind: MeasurementKind, initial: String, symbol: String, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(kind.label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' || it == ',' } },
                suffix = { Text(symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value != null,
                onClick = {
                    value?.let(onSave)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
