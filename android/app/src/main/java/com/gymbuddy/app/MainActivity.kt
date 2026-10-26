package com.gymbuddy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.ui.GymBuddyNavHost
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.ui.theme.GymBuddyTheme
import com.gymbuddy.app.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var proRepository: ProRepository
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val isPro by proRepository.isPro.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppSettings provides settings, LocalIsPro provides isPro) {
                GymBuddyTheme {
                    GymBuddyNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncManager.requestSync()
    }

    override fun onStop() {
        super.onStop()
        // Pulled or imported workouts change the widget too.
        widgetRefresher.refresh()
    }
}
