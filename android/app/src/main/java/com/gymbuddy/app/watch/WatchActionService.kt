package com.gymbuddy.app.watch

import com.gymbuddy.watchlink.WatchPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/** Receives the actions the Wear OS app sends, also when the phone app is not open. */
@AndroidEntryPoint
class WatchActionService : WearableListenerService() {

    @Inject lateinit var bridge: WatchBridge

    // Runs on a background thread of the service, so waiting for the write here is fine.
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchPaths.ACTION) return
        runBlocking { bridge.handle(event.data.decodeToString()) }
    }
}
