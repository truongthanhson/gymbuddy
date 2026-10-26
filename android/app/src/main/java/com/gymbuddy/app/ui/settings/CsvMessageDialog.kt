package com.gymbuddy.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gymbuddy.app.R

/** The outcome of a CSV export or import. */
@Composable
fun CsvMessageDialog(message: CsvMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                when (message) {
                    is CsvMessage.Exported -> pluralStringResource(R.plurals.csv_exported, message.workouts, message.workouts)
                    is CsvMessage.Imported -> buildString {
                        append(pluralStringResource(R.plurals.csv_imported, message.imported, message.imported))
                        if (message.skipped > 0) {
                            append(' ').append(pluralStringResource(R.plurals.csv_skipped, message.skipped, message.skipped))
                        }
                    }
                    CsvMessage.NotACsvExport -> stringResource(R.string.csv_not_an_export)
                    CsvMessage.Failed -> stringResource(R.string.csv_failed)
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
