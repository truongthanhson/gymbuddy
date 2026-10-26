package com.gymbuddy.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fires when a rest timer ends, also when the app is in the background or was killed. */
@AndroidEntryPoint
class RestTimerReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: RestTimerController
    @Inject lateinit var notifier: RestTimerNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val current = controller.state.value
        if (current != null && current.remainingSeconds(System.currentTimeMillis()) > 0) return
        controller.onFinished()
        notifier.showDone()
    }
}
