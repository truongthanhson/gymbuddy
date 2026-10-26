package com.gymbuddy.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.R
import com.gymbuddy.app.data.csv.CsvRepository
import com.gymbuddy.app.data.csv.IncomingCsv
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ImportFileViewModel @Inject constructor(
    private val incoming: IncomingCsv,
    private val csv: CsvRepository,
    private val settings: SettingsRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val pending = incoming.pending

    private val outcome = MutableStateFlow<CsvMessage?>(null)
    val message: StateFlow<CsvMessage?> = outcome.asStateFlow()

    fun import() {
        val uri = incoming.take() ?: return
        viewModelScope.launch {
            outcome.value = csv.importMessage(uri, settings.settings.value.weightUnit) { sync.requestSync() }
        }
    }

    fun cancel() {
        incoming.take()
    }

    fun messageShown() {
        outcome.value = null
    }
}

/** Asks before importing a CSV file shared from another app, like Strong's export, then shows the outcome. */
@Composable
fun ImportFileDialog(viewModel: ImportFileViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(R.string.import_file_title)) },
            text = { Text(stringResource(R.string.import_file_text)) },
            confirmButton = { TextButton(onClick = viewModel::import) { Text(stringResource(R.string.import_file_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    message?.let { CsvMessageDialog(it, viewModel::messageShown) }
}
