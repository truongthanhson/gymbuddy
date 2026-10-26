package com.gymbuddy.app.watch

import android.content.Context
import android.util.Log
import com.gymbuddy.app.data.exercise.ExerciseDao
import com.gymbuddy.app.data.settings.SettingsRepository
import com.gymbuddy.app.data.workout.WorkoutDao
import com.gymbuddy.app.data.workout.WorkoutRepository
import com.gymbuddy.app.data.workout.filledFrom
import com.gymbuddy.app.data.workout.isEmpty
import com.gymbuddy.app.domain.resolveRestSeconds
import com.gymbuddy.app.timer.RestTimerController
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.watchlink.WatchAction
import com.gymbuddy.watchlink.WatchPaths
import com.gymbuddy.watchlink.WatchState
import com.gymbuddy.watchlink.decodeWatchAction
import com.gymbuddy.watchlink.encode
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Keeps the Wear OS app in step with the workout on the phone (shared/watch-protocol.md):
 * publishes the active workout as a Data Layer item and carries out the actions the watch sends.
 */
@Singleton
class WatchBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WorkoutRepository,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val restTimer: RestTimerController,
    private val settings: SettingsRepository,
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    private val vietnamese: Boolean
        get() = context.resources.configuration.locales[0].language == "vi"

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.observeActive(), restTimer.state, settings.settings) { workout, rest, appSettings ->
                Triple(workout, rest, appSettings)
            }
                .mapLatest { (workout, rest, appSettings) ->
                    val previous = workout?.exercises.orEmpty()
                        .map { it.workoutExercise.exerciseId }
                        .distinct()
                        .associateWith { workoutDao.observePreviousSets(it).first() }
                    buildWatchState(workout, previous, appSettings.weightUnit, rest?.endsAtMillis, vietnamese)
                }
                .distinctUntilChanged()
                .collect(::publish)
        }
    }

    /** Called with the JSON of a message the watch sent. */
    suspend fun handle(json: String) {
        val action = runCatching { decodeWatchAction(json) }.getOrNull() ?: return
        when (action.action) {
            WatchAction.COMPLETE_SET -> action.setId?.let { completeSet(it) }
            WatchAction.SKIP_REST -> withContext(Dispatchers.Main) { restTimer.skip() }
        }
    }

    /** Same as ticking the set on the phone (WorkoutViewModel.toggleSet), for a set not done yet. */
    private suspend fun completeSet(setId: String) {
        val set = workoutDao.getSet(setId)?.takeIf { !it.completed && it.deletedAt == null } ?: return
        val item = workoutDao.getWorkoutExercise(set.workoutExerciseId) ?: return
        val exercise = exerciseDao.get(item.exerciseId)
        val index = workoutDao.getSets(item.id).indexOfFirst { it.id == setId }
        val previous = workoutDao.observePreviousSets(item.exerciseId).first().getOrNull(index)
        if (set.isEmpty() && previous != null) repository.updateSet(setId) { it.filledFrom(previous) }
        repository.toggleSetCompleted(setId)
        val seconds = resolveRestSeconds(
            item.restSeconds,
            exercise?.defaultRestSeconds,
            settings.settings.value.defaultRestSeconds,
        )
        withContext(Dispatchers.Main) { restTimer.start(seconds, exercise.displayName(vietnamese)) }
    }

    private suspend fun publish(state: WatchState) {
        val request = PutDataMapRequest.create(WatchPaths.STATE)
            .apply { dataMap.putString(WatchPaths.JSON_KEY, state.encode()) }
            .asPutDataRequest()
            .setUrgent()
        try {
            dataClient.putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services or no Wear OS API on this phone: there is no watch to update.
            Log.d("WatchBridge", "Could not publish watch state", e)
        }
    }
}
