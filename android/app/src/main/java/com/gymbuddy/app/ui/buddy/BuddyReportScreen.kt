package com.gymbuddy.app.ui.buddy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.gymbuddy.app.R
import com.gymbuddy.app.data.coach.BuddyAi
import com.gymbuddy.app.data.coach.BuddyPrefs
import com.gymbuddy.app.data.coach.CoachError
import com.gymbuddy.app.data.coach.CoachPlan
import com.gymbuddy.app.data.coach.WeeklyReport
import com.gymbuddy.app.data.sync.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReportState {
    /** Pro only; the free plan sees what it would get. */
    data object Locked : ReportState
    /** Buddy AI is not set up, the person is signed out, or has not agreed to send data. */
    data object NeedsBuddyAi : ReportState
    data object Loading : ReportState
    data class Ready(val report: WeeklyReport) : ReportState
    data class Failed(val error: CoachError) : ReportState
}

@HiltViewModel
class BuddyReportViewModel @Inject constructor(
    private val ai: BuddyAi,
    private val prefs: BuddyPrefs,
    private val auth: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow<ReportState>(ReportState.Loading)
    val report: StateFlow<ReportState> = state.asStateFlow()

    fun load() {
        state.value = when {
            ai.plan == CoachPlan.FREE -> ReportState.Locked
            !ai.isAvailable || auth.session.value == null || !prefs.settings.value.aiConsent -> ReportState.NeedsBuddyAi
            else -> ReportState.Loading
        }
        if (state.value != ReportState.Loading) return
        viewModelScope.launch {
            state.value = try {
                ReportState.Ready(ai.weeklyReport())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReportState.Failed(e.coachError())
            }
        }
    }
}

/** Buddy AI's look back at last week (Pro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyReportScreen(
    onBack: () -> Unit,
    onOpenBuddy: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: BuddyReportViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.report.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.buddy_weekly_report)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state) {
                ReportState.Locked -> {
                    Text(stringResource(R.string.buddy_weekly_locked))
                    Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_upgrade)) }
                }
                ReportState.NeedsBuddyAi -> {
                    Text(stringResource(R.string.buddy_weekly_needs_ai))
                    Button(onClick = onOpenBuddy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_ask)) }
                }
                ReportState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.buddy_weekly_loading), modifier = Modifier.padding(start = 12.dp))
                }
                is ReportState.Failed -> {
                    Text(coachErrorText(current.error))
                    OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.buddy_retry)) }
                }
                is ReportState.Ready -> {
                    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    val week = current.report.week
                    Text(
                        stringResource(R.string.buddy_weekly_range, week.monday.format(format), week.monday.plusDays(6).format(format)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.buddy_name),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Text(current.report.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OutlinedButton(onClick = onOpenBuddy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buddy_weekly_discuss)) }
                }
            }
        }
    }
}
