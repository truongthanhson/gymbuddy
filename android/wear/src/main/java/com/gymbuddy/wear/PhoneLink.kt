package com.gymbuddy.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gymbuddy.watchlink.WatchAction
import com.gymbuddy.watchlink.WatchPaths
import com.gymbuddy.watchlink.WatchState
import com.gymbuddy.watchlink.decodeWatchState
import com.gymbuddy.watchlink.encode
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/** The watch's view of the phone: the state the phone publishes, and a way to send it actions. */
class PhoneLink(context: Context) : DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val _state = MutableStateFlow<WatchState?>(null)

    /** Null until the phone has shared anything. */
    val state: StateFlow<WatchState?> = _state.asStateFlow()

    fun start() = dataClient.addListener(this)

    fun stop() = dataClient.removeListener(this)

    /** Reads what the phone last published, for when the watch app opens. */
    suspend fun load() = guarded {
        // No host in the URI matches the item from any node.
        val uri = Uri.Builder().scheme("wear").path(WatchPaths.STATE).build()
        val items = dataClient.getDataItems(uri).await()
        try {
            items.forEach(::read)
        } finally {
            items.release()
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach {
            if (it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchPaths.STATE) read(it.dataItem)
        }
    }

    suspend fun completeSet(setId: String) {
        // Show the tick right away; the phone's next state confirms it.
        _state.update { current ->
            val workout = current?.workout ?: return@update current
            WatchState(
                workout.copy(
                    exercises = workout.exercises.map { e ->
                        e.copy(sets = e.sets.map { if (it.id == setId) it.copy(done = true) else it })
                    },
                ),
            )
        }
        send(WatchAction.completeSet(setId))
    }

    suspend fun skipRest() {
        _state.update { current -> current?.workout?.let { WatchState(it.copy(restEndsAt = null)) } ?: current }
        send(WatchAction.skipRest())
    }

    private suspend fun send(action: WatchAction) = guarded {
        val bytes = action.encode().encodeToByteArray()
        for (node in nodeClient.connectedNodes.await()) {
            messageClient.sendMessage(node.id, WatchPaths.ACTION, bytes).await()
        }
    }

    private fun read(item: DataItem) {
        val json = DataMapItem.fromDataItem(item).dataMap.getString(WatchPaths.JSON_KEY) ?: return
        runCatching { decodeWatchState(json) }.onSuccess { _state.value = it }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PhoneLink", "Wear OS Data Layer call failed", e)
        }
    }
}
