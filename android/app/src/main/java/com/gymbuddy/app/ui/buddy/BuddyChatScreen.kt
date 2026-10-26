package com.gymbuddy.app.ui.buddy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.R
import com.gymbuddy.app.data.coach.BuddySettings
import com.gymbuddy.app.data.coach.BuddyTone
import com.gymbuddy.app.data.coach.CoachError
import com.gymbuddy.app.data.coach.CoachPlan
import com.gymbuddy.app.data.coach.Experience
import com.gymbuddy.app.data.coach.TrainingGoal

@Composable
fun coachErrorText(error: CoachError): String = stringResource(
    when (error) {
        CoachError.QUOTA -> R.string.buddy_error_quota
        CoachError.OFFLINE -> R.string.buddy_error_offline
        CoachError.REFUSED -> R.string.buddy_error_refused
        CoachError.SIGNED_OUT -> R.string.buddy_error_signed_out
        CoachError.NOT_CONFIGURED -> R.string.buddy_ai_unavailable
        CoachError.SERVER -> R.string.buddy_error_server
    },
)

/** Chat with Buddy AI, with its consent screen and settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: BuddyChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val ready = state.available && state.signedIn && state.settings.aiConsent
    LaunchedEffect(ready) { if (ready) viewModel.loadHistory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.buddy_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.buddy_settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding()) {
            when {
                !state.available -> Message(stringResource(R.string.buddy_ai_unavailable))
                !state.signedIn -> Message(stringResource(R.string.buddy_ai_sign_in)) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.buddy_open_settings)) }
                }
                !state.settings.aiConsent -> Consent(onAccept = viewModel::acceptConsent)
                else -> Chat(state, onSend = viewModel::send, onOpenPaywall = onOpenPaywall, onDismissError = viewModel::dismissError)
            }
        }
    }
    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            BuddySettingsSheet(
                settings = state.settings,
                onChange = viewModel::updateSettings,
                onClearHistory = viewModel::clearHistory,
                onTurnOffAi = {
                    viewModel.turnOffAi()
                    settingsOpen = false
                },
            )
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

@Composable
private fun Consent(onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.buddy_consent_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.buddy_consent_body), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.buddy_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_consent_accept)) }
    }
}

@Composable
private fun Chat(state: BuddyChatUiState, onSend: (String) -> Unit, onOpenPaywall: () -> Unit, onDismissError: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lines = state.chat.lines
    LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        if (state.chat.loadingHistory) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.plan == CoachPlan.FREE) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.buddy_free_allowance), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenPaywall) { Text(stringResource(R.string.buddy_upgrade)) }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lines.isEmpty() && !state.chat.loadingHistory) {
                item { Text(stringResource(R.string.buddy_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(listOf(R.string.buddy_suggest_1, R.string.buddy_suggest_2, R.string.buddy_suggest_3)) { id ->
                    val text = stringResource(id)
                    AssistChip(onClick = { onSend(text) }, label = { Text(text) })
                }
            }
            items(lines.size) { i -> Bubble(lines[i], typing = state.chat.streaming && i == lines.lastIndex) }
            state.chat.error?.let { error ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(coachErrorText(error))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (error == CoachError.QUOTA && state.plan != CoachPlan.PRO) {
                                    Button(onClick = onOpenPaywall) { Text(stringResource(R.string.buddy_upgrade)) }
                                }
                                OutlinedButton(onClick = onDismissError) { Text(stringResource(android.R.string.ok)) }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(2_000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.buddy_input_hint)) },
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !state.chat.streaming,
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.buddy_send)) }
        }
    }
}

@Composable
private fun Bubble(line: ChatLine, typing: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromBuddy) Arrangement.Start else Arrangement.End) {
        val color = if (line.fromBuddy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
        Box(Modifier.widthIn(max = 320.dp).background(color, RoundedCornerShape(16.dp)).padding(12.dp)) {
            if (typing && line.text.isEmpty()) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
            } else {
                Text(line.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
        }
    }
}

/** Tone and the goal profile Buddy plans around. */
@Composable
fun BuddySettingsSheet(
    settings: BuddySettings,
    onChange: ((BuddySettings) -> BuddySettings) -> Unit,
    onClearHistory: (() -> Unit)?,
    onTurnOffAi: (() -> Unit)?,
) {
    var limitations by remember(settings.limitations) { mutableStateOf(settings.limitations) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.buddy_settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.buddy_tone), style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            listOf(BuddyTone.GENTLE to R.string.buddy_tone_gentle, BuddyTone.BALANCED to R.string.buddy_tone_balanced, BuddyTone.STRICT to R.string.buddy_tone_strict),
            settings.tone,
        ) { tone -> onChange { it.copy(tone = tone) } }
        Text(stringResource(R.string.buddy_goal), style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            listOf(TrainingGoal.GENERAL to R.string.buddy_goal_general, TrainingGoal.STRENGTH to R.string.buddy_goal_strength),
            settings.goal,
        ) { goal -> onChange { it.copy(goal = goal) } }
        ChoiceRow(
            listOf(TrainingGoal.MUSCLE to R.string.buddy_goal_muscle, TrainingGoal.FAT_LOSS to R.string.buddy_goal_fat_loss),
            settings.goal,
        ) { goal -> onChange { it.copy(goal = goal) } }
        Text(stringResource(R.string.buddy_days, settings.daysPerWeek), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.daysPerWeek.toFloat(),
            onValueChange = { v -> onChange { it.copy(daysPerWeek = v.toInt()) } },
            valueRange = 1f..7f,
            steps = 5,
        )
        Text(stringResource(R.string.buddy_experience), style = MaterialTheme.typography.titleSmall)
        ChoiceRow(
            listOf(
                Experience.BEGINNER to R.string.buddy_experience_beginner,
                Experience.INTERMEDIATE to R.string.buddy_experience_intermediate,
                Experience.ADVANCED to R.string.buddy_experience_advanced,
            ),
            settings.experience,
        ) { experience -> onChange { it.copy(experience = experience) } }
        OutlinedTextField(
            value = limitations,
            onValueChange = { text ->
                limitations = text.take(300)
                onChange { it.copy(limitations = limitations) }
            },
            label = { Text(stringResource(R.string.buddy_limitations)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        if (onClearHistory != null) {
            OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_clear_history)) }
        }
        if (onTurnOffAi != null && settings.aiConsent) {
            TextButton(onClick = onTurnOffAi, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.buddy_turn_off_ai), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
