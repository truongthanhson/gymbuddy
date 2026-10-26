package com.gymbuddy.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.csv.CsvFormatException
import com.gymbuddy.app.data.csv.CsvRepository
import com.gymbuddy.app.data.settings.AppSettings
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.domain.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of a CSV export or import, shown once in the settings screen. */
sealed interface CsvMessage {
    data class Exported(val workouts: Int) : CsvMessage
    data class Imported(val imported: Int, val skipped: Int) : CsvMessage
    data object NotACsvExport : CsvMessage
    data object Failed : CsvMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val csv: CsvRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    private val csvMessage = MutableStateFlow<CsvMessage?>(null)
    val message: StateFlow<CsvMessage?> = csvMessage.asStateFlow()

    fun setWeightUnit(unit: WeightUnit) = repository.setWeightUnit(unit)

    fun setDefaultRestSeconds(seconds: Int) = repository.setDefaultRestSeconds(seconds)

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            csvMessage.value = runCatching { CsvMessage.Exported(csv.export(uri, settings.value.weightUnit)) }
                .getOrDefault(CsvMessage.Failed)
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            csvMessage.value = try {
                val result = csv.import(uri, settings.value.weightUnit)
                if (result.imported > 0) sync.requestSync()
                CsvMessage.Imported(result.imported, result.skipped)
            } catch (e: CsvFormatException) {
                CsvMessage.NotACsvExport
            } catch (e: Exception) {
                CsvMessage.Failed
            }
        }
    }

    fun messageShown() {
        csvMessage.value = null
    }
}
