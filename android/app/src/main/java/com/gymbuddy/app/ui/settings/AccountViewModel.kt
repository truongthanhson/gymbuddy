package com.gymbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.sync.AuthRepository
import com.gymbuddy.app.data.sync.Session
import com.gymbuddy.app.data.sync.SignUpResult
import com.gymbuddy.app.data.sync.SupabaseConfig
import com.gymbuddy.app.data.sync.SupabaseException
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the sign-in form shows besides its fields. */
data class AuthFormState(
    val busy: Boolean = false,
    val error: String? = null,
    /** Sign up worked but the project wants the email address confirmed before signing in. */
    val confirmEmail: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val config: SupabaseConfig,
    private val auth: AuthRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val isConfigured: Boolean get() = config.isConfigured
    val session: StateFlow<Session?> = auth.session
    val syncStatus: StateFlow<SyncStatus> = sync.status

    private val form = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = form.asStateFlow()

    /** Set when signing out could not sync first, so the user can choose to lose unsynced changes. */
    private val signOutFailed = MutableStateFlow<String?>(null)
    val signOutError: StateFlow<String?> = signOutFailed.asStateFlow()

    fun signIn(email: String, password: String, onDone: () -> Unit) = submit(onDone) {
        auth.signIn(email, password)
        true
    }

    fun signUp(email: String, password: String, onDone: () -> Unit) = submit(onDone) {
        when (auth.signUp(email, password)) {
            SignUpResult.SIGNED_IN -> true
            SignUpResult.CONFIRM_EMAIL -> {
                form.value = AuthFormState(confirmEmail = true)
                false
            }
        }
    }

    fun resetForm() {
        form.value = AuthFormState()
    }

    fun syncNow() {
        viewModelScope.launch { runCatching { sync.sync() } }
    }

    /** Syncs first so nothing is lost; with [force], signs out even when that sync fails. */
    fun signOut(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                try {
                    sync.sync()
                } catch (e: SupabaseException) {
                    signOutFailed.value = e.message ?: ""
                    return@launch
                }
            }
            signOutFailed.value = null
            auth.clear()
            sync.clearLocalData()
        }
    }

    fun dismissSignOutError() {
        signOutFailed.value = null
    }

    /** Runs [action]; when it returns true the user is signed in, so sync and call [onDone]. */
    private fun submit(onDone: () -> Unit, action: suspend () -> Boolean) {
        if (form.value.busy) return
        form.value = AuthFormState(busy = true)
        viewModelScope.launch {
            try {
                if (action()) {
                    form.value = AuthFormState()
                    onDone()
                    sync.requestSync()
                }
            } catch (e: SupabaseException) {
                form.value = AuthFormState(error = e.message)
            }
        }
    }
}
