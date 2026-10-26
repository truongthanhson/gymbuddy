package com.gymbuddy.app.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gymbuddy.app.R

@Composable
fun ComingSoonScreen(@StringRes title: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyMedium)
    }
}
