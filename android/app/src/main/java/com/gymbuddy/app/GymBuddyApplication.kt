package com.gymbuddy.app

import android.app.Application
import com.gymbuddy.app.data.coach.BuddyPrefs
import com.gymbuddy.app.data.coach.BuddyReminders
import com.gymbuddy.app.data.exercise.ExerciseSeeder
import com.gymbuddy.app.data.pro.ProRepository
import com.gymbuddy.app.timer.RestTimerNotifier
import com.gymbuddy.app.watch.WatchBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class GymBuddyApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var restTimerNotifier: RestTimerNotifier
    @Inject lateinit var watchBridge: WatchBridge
    @Inject lateinit var proRepository: ProRepository
    @Inject lateinit var buddyPrefs: BuddyPrefs
    @Inject lateinit var buddyReminders: BuddyReminders

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        restTimerNotifier.createChannels()
        appScope.launch { exerciseSeeder.seedIfNeeded() }
        watchBridge.start(appScope)
        proRepository.start()
        buddyReminders.createChannel()
        appScope.launch {
            buddyPrefs.settings.map { it.reminders }.distinctUntilChanged().collect { buddyReminders.apply(it) }
        }
    }
}
