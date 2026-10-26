package com.gymbuddy.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymbuddy.app.data.csv.IncomingCsv
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.settings.ThemeMode
import com.gymbuddy.app.data.sync.SyncManager
import com.gymbuddy.app.ui.GymBuddyNavHost
import com.gymbuddy.app.ui.common.LocalAppSettings
import com.gymbuddy.app.ui.common.LocalIsPro
import com.gymbuddy.app.ui.settings.ImportFileDialog
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
    @Inject lateinit var incomingCsv: IncomingCsv

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity has already offered its intent.
        if (savedInstanceState == null) incomingCsv.offer(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val isPro by proRepository.isPro.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppSettings provides settings, LocalIsPro provides isPro) {
                val dark = when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                // Status and navigation bar icons follow the app's theme setting, not the system's.
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                GymBuddyTheme(darkTheme = dark) {
                    GymBuddyNavHost()
                    ImportFileDialog()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingCsv.offer(intent)
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
