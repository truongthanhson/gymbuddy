package com.gymbuddy.app.data.csv

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A CSV file shared or opened from another app (like Strong's export), waiting for the user to confirm the import. */
@Singleton
class IncomingCsv @Inject constructor() {
    private val file = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = file.asStateFlow()

    /** Keeps the file of a share or open intent; other intents are ignored. */
    fun offer(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) file.value = uri
    }

    /** The pending file, which is then no longer pending. */
    fun take(): Uri? = file.value.also { file.value = null }
}
