package com.gymbuddy.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.gymbuddy.app.data.exercise.ExerciseEntity

/** True when the app is shown in Vietnamese, so built-in exercises use their Vietnamese names. */
@Composable
fun useVietnameseNames(): Boolean = LocalConfiguration.current.locales[0].language == "vi"

fun ExerciseEntity?.displayName(vietnamese: Boolean): String = when {
    this == null -> "?"
    vietnamese && nameVi != null -> nameVi
    else -> name
}
