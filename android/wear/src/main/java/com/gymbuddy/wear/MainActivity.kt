package com.gymbuddy.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var link: PhoneLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = PhoneLink(applicationContext)
        setContent {
            WorkoutRemote(
                stateFlow = link.state,
                onCompleteSet = { id -> lifecycleScope.launch { link.completeSet(id) } },
                onSkipRest = { lifecycleScope.launch { link.skipRest() } },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        link.start()
        lifecycleScope.launch { link.load() }
    }

    override fun onStop() {
        link.stop()
        super.onStop()
    }
}
