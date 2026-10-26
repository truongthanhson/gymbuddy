package com.gymbuddy.app.ui.buddy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.data.coach.BuddyAi
import com.gymbuddy.app.data.coach.BuddyPrefs
import com.gymbuddy.app.data.coach.CoachPlan
import com.gymbuddy.app.data.sync.AuthRepository
import com.gymbuddy.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NoteState {
    /** Buddy AI is off for this person (free plan, signed out or no consent): show nothing. */
    data object Hidden : NoteState
    data object Loading : NoteState
    data class Ready(val text: String) : NoteState
}

@HiltViewModel
class BuddyNoteViewModel @Inject constructor(
    private val ai: BuddyAi,
    private val prefs: BuddyPrefs,
    private val auth: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow<NoteState>(NoteState.Hidden)
    val note: StateFlow<NoteState> = state.asStateFlow()
    private var requested: String? = null

    fun load(workoutId: String) {
        if (requested == workoutId) return
        requested = workoutId
        val usable = ai.isAvailable && auth.session.value != null && prefs.settings.value.aiConsent && ai.plan != CoachPlan.FREE
        if (!usable) {
            state.value = NoteState.Hidden
            return
        }
        state.value = NoteState.Loading
        viewModelScope.launch {
            // A note is a bonus: on any failure the summary simply goes without it.
            state.value = try {
                ai.workoutNote(workoutId).takeIf { it.isNotBlank() }?.let { NoteState.Ready(it) } ?: NoteState.Hidden
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NoteState.Hidden
            }
        }
    }
}

/** Buddy AI's few lines on a workout just finished (Pro). */
@Composable
fun BuddyAiNote(workoutId: String, viewModel: BuddyNoteViewModel = hiltViewModel()) {
    LaunchedEffect(workoutId) { viewModel.load(workoutId) }
    val note by viewModel.note.collectAsStateWithLifecycle()
    when (val current = note) {
        NoteState.Hidden -> Unit
        NoteState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.buddy_note_loading), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }
        is NoteState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.buddy_note_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            }
            Text(current.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
