package com.gymbuddy.app.ui.buddy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymbuddy.app.R
import com.gymbuddy.app.data.coach.BuddyAi
import com.gymbuddy.app.data.coach.BuddyPrefs
import com.gymbuddy.app.data.coach.CoachError
import com.gymbuddy.app.data.coach.CoachPlan
import com.gymbuddy.app.data.coach.RoutineProposal
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.sync.AuthRepository
import com.gymbuddy.app.ui.common.useVietnameseNames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineUiState(
    val locked: Boolean = false,
    val needsBuddyAi: Boolean = false,
    val designing: Boolean = false,
    val proposal: RoutineProposal? = null,
    val saved: Boolean = false,
    val error: CoachError? = null,
    /** Exercise names (English, Vietnamese) per id, for the preview. */
    val names: Map<String, Pair<String, String?>> = emptyMap(),
)

@HiltViewModel
class BuddyRoutineViewModel @Inject constructor(
    private val ai: BuddyAi,
    private val exercises: ExerciseDao,
    prefs: BuddyPrefs,
    auth: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow(
        RoutineUiState(
            locked = ai.plan == CoachPlan.FREE,
            needsBuddyAi = !ai.isAvailable || auth.session.value == null || !prefs.settings.value.aiConsent,
        ),
    )
    val ui: StateFlow<RoutineUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val names = exercises.getAllLive().associate { it.id to (it.name to it.nameVi) }
            state.update { it.copy(names = names) }
        }
    }

    fun design(request: String) {
        val text = request.trim()
        if (text.isEmpty() || state.value.designing) return
        state.update { it.copy(designing = true, error = null, proposal = null, saved = false) }
        viewModelScope.launch {
            try {
                val proposal = ai.designRoutine(text)
                state.update { it.copy(designing = false, proposal = proposal) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(designing = false, error = e.coachError()) }
            }
        }
    }

    fun save() {
        val routine = state.value.proposal?.routine ?: return
        viewModelScope.launch {
            ai.saveRoutine(routine)
            state.update { it.copy(saved = true) }
        }
    }
}

/** Buddy AI designs a routine from a request in the person's words; it becomes templates once they agree (Pro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyRoutineScreen(
    onBack: () -> Unit,
    onOpenBuddy: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: BuddyRoutineViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var request by rememberSaveable { mutableStateOf("") }
    val vietnamese = useVietnameseNames()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.buddy_routine)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.locked -> {
                    Text(stringResource(R.string.buddy_routine_locked))
                    Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_upgrade)) }
                }
                state.needsBuddyAi -> {
                    Text(stringResource(R.string.buddy_weekly_needs_ai))
                    Button(onClick = onOpenBuddy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_ask)) }
                }
                else -> {
                    Text(stringResource(R.string.buddy_routine_intro))
                    OutlinedTextField(
                        value = request,
                        onValueChange = { request = it.take(500) },
                        placeholder = { Text(stringResource(R.string.buddy_routine_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                    )
                    Button(
                        onClick = { viewModel.design(request) },
                        enabled = request.isNotBlank() && !state.designing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.buddy_routine_design)) }
                    if (state.designing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.buddy_routine_designing), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    state.error?.let { Text(coachErrorText(it), color = MaterialTheme.colorScheme.error) }
                    state.proposal?.let { proposal ->
                        Text(proposal.routine.name, style = MaterialTheme.typography.titleLarge)
                        if (proposal.text.isNotBlank()) Text(proposal.text, style = MaterialTheme.typography.bodyMedium)
                        proposal.routine.days.forEach { day ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(day.name, style = MaterialTheme.typography.titleMedium)
                                    day.exercises.forEach { e ->
                                        val (en, vi) = state.names[e.exerciseId] ?: (e.exerciseId to null)
                                        val reps = if (e.repsLow == e.repsHigh) "${e.repsLow}" else "${e.repsLow}–${e.repsHigh}"
                                        Text(
                                            stringResource(R.string.buddy_routine_line, e.sets, reps, if (vietnamese && vi != null) vi else en),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.saved) {
                            Text(stringResource(R.string.buddy_routine_saved), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_routine_save)) }
                            OutlinedButton(onClick = { viewModel.design(request) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.buddy_routine_again))
                            }
                        }
                    }
                }
            }
        }
    }
}
