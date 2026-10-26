package com.gymbuddy.app.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R

/** Account and sync rows of the settings screen. */
@Composable
fun AccountSection(viewModel: AccountViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    val signOutError by viewModel.signOutError.collectAsStateWithLifecycle()
    var signingIn by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val current = session
    when {
        !viewModel.isConfigured -> ListItem(
            headlineContent = { Text(stringResource(R.string.account)) },
            supportingContent = { Text(stringResource(R.string.sync_not_configured)) },
        )
        current == null -> ListItem(
            modifier = Modifier.clickable {
                viewModel.resetForm()
                signingIn = true
            },
            headlineContent = { Text(stringResource(R.string.sign_in)) },
            supportingContent = { Text(stringResource(R.string.sign_in_hint)) },
        )
        else -> Column {
            ListItem(
                headlineContent = { Text(current.email ?: stringResource(R.string.account)) },
                supportingContent = {
                    val lastSynced = status.lastSyncedAt
                    Text(
                        when {
                            status.running -> stringResource(R.string.syncing)
                            status.error != null -> stringResource(R.string.sync_failed, status.error ?: "")
                            lastSynced != null -> stringResource(
                                R.string.last_synced,
                                DateUtils.getRelativeTimeSpanString(lastSynced).toString(),
                            )
                            else -> stringResource(R.string.never_synced)
                        },
                        color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::syncNow, enabled = !status.running) {
                    Text(stringResource(R.string.sync_now))
                }
                TextButton(onClick = { confirmSignOut = true }, enabled = !status.running) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }

    if (signingIn) {
        SignInDialog(viewModel, onDismiss = { signingIn = false })
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    signOutError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOutError,
            title = { Text(stringResource(R.string.sign_out_sync_failed_title)) },
            text = { Text(stringResource(R.string.sign_out_sync_failed_message, error)) },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(force = true) }) {
                    Text(stringResource(R.string.sign_out_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOutError) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SignInDialog(viewModel: AccountViewModel, onDismiss: () -> Unit) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = !form.busy && email.isNotBlank() && password.length >= MIN_PASSWORD

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_in)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    supportingText = { Text(stringResource(R.string.password_hint, MIN_PASSWORD)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    form.confirmEmail -> Text(stringResource(R.string.confirm_email), color = MaterialTheme.colorScheme.primary)
                    form.error != null -> Text(form.error ?: "", color = MaterialTheme.colorScheme.error)
                    form.busy -> Text(stringResource(R.string.please_wait))
                }
                OutlinedButton(
                    onClick = { viewModel.signUp(email, password, onDismiss) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.create_account)) }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.signIn(email, password, onDismiss) }, enabled = canSubmit) {
                Text(stringResource(R.string.sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Supabase Auth's default minimum. */
private const val MIN_PASSWORD = 6
