package com.gymbuddy.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.gymbuddy.app.data.settings.AppSettings

/** The current settings, provided at the top of the UI so any screen can format weights and rest times. */
val LocalAppSettings = compositionLocalOf { AppSettings() }
