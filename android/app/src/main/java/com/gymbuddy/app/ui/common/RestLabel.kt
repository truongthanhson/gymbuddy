package com.gymbuddy.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymbuddy.app.R
import com.gymbuddy.app.domain.formatElapsed

/** "2:00", or "Off" for 0. */
@Composable
fun restLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(R.string.rest_off) else formatElapsed(seconds.toLong())
