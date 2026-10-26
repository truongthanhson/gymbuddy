package com.gymbuddy.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.REST_TIME_OPTIONS
import com.gymbuddy.app.domain.WeightUnit
import com.gymbuddy.app.ui.common.restLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPlateCalculator: () -> Unit,
    onOpenOneRepMaxCalculator: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var restDialogOpen by remember { mutableStateOf(false) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCsv)
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(viewModel.health.permissionContract()) { granted ->
        viewModel.onHealthPermissionResult(granted)
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(R.string.account)
        AccountSection()
        HorizontalDivider()

        SectionTitle(R.string.settings_units)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_weight_unit)) },
            trailingContent = {
                SingleChoiceSegmentedButtonRow {
                    WeightUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = settings.weightUnit == unit,
                            onClick = { viewModel.setWeightUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                        ) { Text(unit.symbol) }
                    }
                }
            },
        )
        HorizontalDivider()

        SectionTitle(R.string.settings_general)
        ListItem(
            modifier = Modifier.clickable { restDialogOpen = true },
            headlineContent = { Text(stringResource(R.string.settings_default_rest)) },
            supportingContent = { Text(stringResource(R.string.settings_default_rest_hint)) },
            trailingContent = { Text(restLabel(settings.defaultRestSeconds)) },
        )
        // Android 13+ has per-app languages in system settings; older versions follow the system language.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ListItem(
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(stringResource(R.string.settings_language_hint)) },
            )
        }
        HorizontalDivider()

        SectionTitle(R.string.settings_tools)
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenPlateCalculator),
            headlineContent = { Text(stringResource(R.string.plate_calculator)) },
            supportingContent = { Text(stringResource(R.string.plate_calculator_hint)) },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenOneRepMaxCalculator),
            headlineContent = { Text(stringResource(R.string.one_rep_max_calculator)) },
            supportingContent = { Text(stringResource(R.string.one_rep_max_calculator_hint)) },
        )
        HorizontalDivider()

        SectionTitle(R.string.settings_data)
        if (viewModel.health.isAvailable) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.health_connect)) },
                supportingContent = { Text(stringResource(R.string.health_connect_hint)) },
                trailingContent = {
                    Switch(
                        checked = settings.healthEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                viewModel.turnOnHealthIfAllowed { healthPermissionLauncher.launch(viewModel.health.permissions) }
                            } else {
                                viewModel.turnOffHealth()
                            }
                        },
                    )
                },
            )
        }
        ListItem(
            modifier = Modifier.clickable { exportLauncher.launch("gymbuddy-workouts.csv") },
            headlineContent = { Text(stringResource(R.string.export_csv)) },
            supportingContent = { Text(stringResource(R.string.export_csv_hint)) },
        )
        ListItem(
            // Some providers report CSV files as text/plain or a generic type.
            modifier = Modifier.clickable { importLauncher.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
            headlineContent = { Text(stringResource(R.string.import_csv)) },
            supportingContent = { Text(stringResource(R.string.import_csv_hint)) },
        )
        HorizontalDivider()

        SectionTitle(R.string.settings_about)
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.settings_version, version)) },
        )
    }

    message?.let { current ->
        AlertDialog(
            onDismissRequest = viewModel::messageShown,
            text = {
                Text(
                    when (current) {
                        is CsvMessage.Exported -> pluralStringResource(R.plurals.csv_exported, current.workouts, current.workouts)
                        is CsvMessage.Imported -> buildString {
                            append(pluralStringResource(R.plurals.csv_imported, current.imported, current.imported))
                            if (current.skipped > 0) {
                                append(' ').append(pluralStringResource(R.plurals.csv_skipped, current.skipped, current.skipped))
                            }
                        }
                        CsvMessage.NotACsvExport -> stringResource(R.string.csv_not_an_export)
                        CsvMessage.Failed -> stringResource(R.string.csv_failed)
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::messageShown) { Text(stringResource(R.string.close)) } },
        )
    }

    if (restDialogOpen) {
        AlertDialog(
            onDismissRequest = { restDialogOpen = false },
            title = { Text(stringResource(R.string.settings_default_rest)) },
            text = {
                Column {
                    REST_TIME_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    restDialogOpen = false
                                    viewModel.setDefaultRestSeconds(option)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == settings.defaultRestSeconds, onClick = null)
                            Text(restLabel(option), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restDialogOpen = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
