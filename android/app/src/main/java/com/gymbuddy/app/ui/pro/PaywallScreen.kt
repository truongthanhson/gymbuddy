package com.gymbuddy.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.BuildConfig
import com.gymbuddy.app.R
import com.gymbuddy.app.data.pro.ProPlan
import com.gymbuddy.app.domain.FREE_TEMPLATE_LIMIT
import com.gymbuddy.app.domain.ProFeature

@get:StringRes
private val ProPlan.label: Int
    get() = when (this) {
        ProPlan.MONTHLY -> R.string.pro_monthly
        ProPlan.YEARLY -> R.string.pro_yearly
        ProPlan.LIFETIME -> R.string.pro_lifetime
    }

@get:StringRes
val ProFeature.label: Int
    get() = when (this) {
        ProFeature.UNLIMITED_TEMPLATES -> R.string.pro_feature_templates
        ProFeature.EXERCISE_CHARTS -> R.string.pro_feature_charts
        ProFeature.BODY_MEASUREMENTS -> R.string.pro_feature_measurements
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What Pro unlocks and the plans Google Play sells, like Strong's upgrade screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val debugOverride by viewModel.debugOverride.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pro_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                stringResource(if (isPro) R.string.pro_active else R.string.pro_pitch),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProFeature.entries.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (feature == ProFeature.UNLIMITED_TEMPLATES) {
                                stringResource(feature.label, FREE_TEMPLATE_LIMIT)
                            } else {
                                stringResource(feature.label)
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            if (!isPro) {
                if (offers.isEmpty()) {
                    Text(
                        stringResource(R.string.pro_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                offers.forEach { offer ->
                    val text = stringResource(R.string.pro_plan_price, stringResource(offer.plan.label), offer.price)
                    if (offer.plan == ProPlan.YEARLY) {
                        Button(onClick = { activity?.let { viewModel.purchase(it, offer) } }, Modifier.fillMaxWidth()) { Text(text) }
                    } else {
                        OutlinedButton(onClick = { activity?.let { viewModel.purchase(it, offer) } }, Modifier.fillMaxWidth()) { Text(text) }
                    }
                }
                TextButton(onClick = viewModel::restore) { Text(stringResource(R.string.pro_restore)) }
            }
            if (BuildConfig.DEBUG) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pro_debug_override), modifier = Modifier.weight(1f))
                    Switch(checked = debugOverride, onCheckedChange = viewModel::setDebugOverride)
                }
            }
        }
    }
}

/** Shown in place of a Pro feature for free users. */
@Composable
fun ProLocked(feature: ProFeature, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringResource(feature.label, FREE_TEMPLATE_LIMIT), textAlign = TextAlign.Center)
        Button(onClick = onUnlock) { Text(stringResource(R.string.pro_unlock)) }
    }
}
