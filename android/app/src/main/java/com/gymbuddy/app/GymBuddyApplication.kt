package com.gymbuddy.app

import android.app.Application
import com.gymbuddy.app.data.exercise.ExerciseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GymBuddyApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { exerciseSeeder.seedIfNeeded() }
    }
}
