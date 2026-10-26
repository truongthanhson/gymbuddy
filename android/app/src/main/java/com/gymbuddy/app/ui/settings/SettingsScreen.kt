package com.gymbuddy.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var restDialogOpen by remember { mutableStateOf(false) }
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

        SectionTitle(R.string.settings_about)
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.settings_version, version)) },
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
