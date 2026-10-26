package com.gymbuddy.app

import android.app.Application
import com.gymbuddy.app.data.exercise.ExerciseSeeder
import com.gymbuddy.app.timer.RestTimerNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GymBuddyApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var restTimerNotifier: RestTimerNotifier

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        restTimerNotifier.createChannels()
        appScope.launch { exerciseSeeder.seedIfNeeded() }
    }
}
