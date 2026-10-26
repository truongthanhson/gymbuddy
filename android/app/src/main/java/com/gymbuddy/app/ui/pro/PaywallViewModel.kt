package com.gymbuddy.app.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.gymbuddy.app.data.pro.ProOffer
import com.gymbuddy.app.data.pro.ProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PaywallViewModel @Inject constructor(private val pro: ProRepository) : ViewModel() {
    val isPro: StateFlow<Boolean> = pro.isPro
    val offers: StateFlow<List<ProOffer>> = pro.offers
    val debugOverride: StateFlow<Boolean> = pro.debugOverride

    init {
        pro.refresh()
    }

    fun purchase(activity: Activity, offer: ProOffer) = pro.purchase(activity, offer)
    fun restore() = pro.refresh()
    fun setDebugOverride(enabled: Boolean) = pro.setDebugOverride(enabled)
}
