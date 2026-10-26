package com.gymbuddy.app.ui.buddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.coach.BuddyAi
import com.gymbuddy.app.data.coach.BuddyPrefs
import com.gymbuddy.app.data.coach.BuddySettings
import com.gymbuddy.app.data.coach.ChatEvent
import com.gymbuddy.app.data.coach.CoachError
import com.gymbuddy.app.data.coach.CoachException
import com.gymbuddy.app.data.coach.CoachPlan
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.sync.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatLine(val fromBuddy: Boolean, val text: String)

data class ChatState(
    val lines: List<ChatLine> = emptyList(),
    val loadingHistory: Boolean = false,
    val streaming: Boolean = false,
    val error: CoachError? = null,
)

data class BuddyChatUiState(
    val available: Boolean,
    val signedIn: Boolean,
    val settings: BuddySettings,
    val plan: CoachPlan,
    val chat: ChatState,
)

@HiltViewModel
class BuddyChatViewModel @Inject constructor(
    private val ai: BuddyAi,
    private val prefs: BuddyPrefs,
    auth: AuthRepository,
    pro: ProRepository,
) : ViewModel() {

    private val chat = MutableStateFlow(ChatState())
    private var historyLoaded = false

    val state: StateFlow<BuddyChatUiState> = combine(auth.session, prefs.settings, pro.isPro, chat) { session, settings, _, chat ->
        BuddyChatUiState(ai.isAvailable, session != null, settings, ai.plan, chat)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BuddyChatUiState(ai.isAvailable, auth.session.value != null, prefs.settings.value, ai.plan, ChatState()),
    )

    /** Loads the conversation once Buddy AI can be used. */
    fun loadHistory() {
        if (historyLoaded || !ai.isAvailable || !prefs.settings.value.aiConsent) return
        historyLoaded = true
        viewModelScope.launch {
            chat.update { it.copy(loadingHistory = true, error = null) }
            try {
                val lines = ai.history().map { ChatLine(it.role == "assistant", it.content) }
                chat.update { it.copy(lines = lines, loadingHistory = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                historyLoaded = false
                chat.update { it.copy(loadingHistory = false, error = e.coachError()) }
            }
        }
    }

    fun acceptConsent() {
        prefs.update { it.copy(aiConsent = true) }
        loadHistory()
    }

    fun updateSettings(change: (BuddySettings) -> BuddySettings) = prefs.update(change)

    fun turnOffAi() {
        prefs.update { it.copy(aiConsent = false) }
        historyLoaded = false
        chat.value = ChatState()
    }

    fun clearHistory() = viewModelScope.launch {
        try {
            ai.clearHistory()
            chat.update { it.copy(lines = emptyList(), error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            chat.update { it.copy(error = e.coachError()) }
        }
    }

    fun dismissError() = chat.update { it.copy(error = null) }

    fun send(message: String) {
        val text = message.trim()
        if (text.isEmpty() || chat.value.streaming) return
        chat.update { it.copy(lines = it.lines + ChatLine(false, text) + ChatLine(true, ""), streaming = true, error = null) }
        viewModelScope.launch {
            try {
                val context = ai.context()
                ai.chat(text, context).collect { event ->
                    if (event is ChatEvent.Text) {
                        chat.update { s -> s.copy(lines = s.lines.dropLast(1) + ChatLine(true, s.lines.last().text + event.delta)) }
                    }
                }
                chat.update { it.copy(streaming = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Drop the unanswered question's empty reply; keep a partial one.
                chat.update { s ->
                    val lines = if (s.lines.lastOrNull()?.text.isNullOrEmpty()) s.lines.dropLast(1) else s.lines
                    s.copy(lines = lines, streaming = false, error = e.coachError())
                }
            }
        }
    }
}

/** Anything unexpected (a malformed answer, say) shows as a server error instead of crashing. */
fun Exception.coachError(): CoachError = (this as? CoachException)?.error ?: CoachError.SERVER
