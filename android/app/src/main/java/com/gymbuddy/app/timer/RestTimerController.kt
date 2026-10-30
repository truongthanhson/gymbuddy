package com.gymbuddy.app.timer

import com.gymbuddy.app.domain.RestTimerState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one rest timer of the app. The countdown lives in the notification and an alarm fires at the end,
 * so it keeps working while the app is in the background.
 */
@Singleton
class RestTimerController @Inject constructor(private val notifier: RestTimerNotifier) {

    private val _state = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    private var label: String = ""

    fun start(seconds: Int, exerciseName: String, setId: String? = null) {
        val next = RestTimerState.start(seconds, now(), setId) ?: return
        label = exerciseName
        apply(next)
    }

    fun adjust(deltaSeconds: Int) {
        val current = _state.value ?: return
        apply(current.adjusted(deltaSeconds, now()))
    }

    fun skip() = apply(null)

    /** Called by the alarm when the rest is over. */
    fun onFinished() {
        val current = _state.value
        if (current != null && current.remainingSeconds(now()) > 0) return // the timer was extended meanwhile
        _state.value = null
        notifier.cancelCountdown()
    }

    /** The UI saw the countdown reach zero before the alarm did: end it now. */
    fun finishNow() {
        if (_state.value == null) return
        _state.value = null
        notifier.cancel()
        notifier.showDone()
    }

    private fun apply(next: RestTimerState?) {
        _state.value = next
        if (next == null) {
            notifier.cancel()
        } else {
            notifier.show(next, label)
        }
    }

    private fun now() = System.currentTimeMillis()
}
