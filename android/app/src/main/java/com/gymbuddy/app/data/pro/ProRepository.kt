package com.gymbuddy.app.data.pro

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.gymbuddy.app.BuildConfig
import com.gymbuddy.app.domain.ProProducts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProPlan { MONTHLY, YEARLY, LIFETIME }

/** A plan as Google Play sells it, with the price formatted for the user's country. */
data class ProOffer(val plan: ProPlan, val price: String, internal val details: ProductDetails, internal val offerToken: String?)

/**
 * GymBuddy Pro through Google Play Billing. The last known state is kept on the device so Pro works offline;
 * Play's purchase list replaces it whenever the store answers.
 */
@Singleton
class ProRepository @Inject constructor(@ApplicationContext context: Context) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val purchased = MutableStateFlow(prefs.getBoolean(KEY_PURCHASED, false))
    private val debugPro = MutableStateFlow(BuildConfig.PRO_SWITCH && prefs.getBoolean(KEY_DEBUG, false))
    private val subscribed = MutableStateFlow(prefs.getBoolean(KEY_SUBSCRIBED, false))
    private val offersState = MutableStateFlow<List<ProOffer>>(emptyList())

    val isPro: StateFlow<Boolean> = combine(purchased, debugPro) { bought, debug -> bought || debug }
        .stateIn(scope, SharingStarted.Eagerly, purchased.value || debugPro.value)

    /** Empty until Play answers, or when the products don't exist in Play Console yet. */
    val offers: StateFlow<List<ProOffer>> = offersState.asStateFlow()

    /** Lifetime Pro without a subscription gets a smaller Buddy AI allowance (supabase/functions/coach). */
    val isLifetimeOnly: StateFlow<Boolean> = combine(purchased, subscribed, debugPro) { bought, sub, debug -> bought && !sub && !debug }
        .stateIn(scope, SharingStarted.Eagerly, purchased.value && !subscribed.value && !debugPro.value)

    /** Debug builds can turn Pro on without a purchase, to try the locked screens. */
    val debugOverride: StateFlow<Boolean> = debugPro.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() = connect()

    /** Asks Play again, e.g. for "Restore purchases". */
    fun refresh() {
        if (client.isReady) scope.launch { load() } else connect()
    }

    fun purchase(activity: Activity, offer: ProOffer) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details)
            .apply { offer.offerToken?.let(::setOfferToken) }
            .build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
    }

    fun setDebugOverride(enabled: Boolean) {
        if (!BuildConfig.PRO_SWITCH) return
        prefs.edit().putBoolean(KEY_DEBUG, enabled).apply()
        debugPro.value = enabled
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // An update lists only the new purchase, so it can turn Pro on but never off.
            scope.launch { if (handle(purchases)) setPurchased(true) }
        }
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { load() }
            }

            // The next refresh reconnects.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun load() {
        offersState.value = queryOffers()
        val subs = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
        val inApp = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
        if (subs.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
            inApp.billingResult.responseCode != BillingClient.BillingResponseCode.OK
        ) {
            return
        }
        setPurchased(handle(subs.purchasesList + inApp.purchasesList))
    }

    private suspend fun queryOffers(): List<ProOffer> {
        fun params(ids: List<String>, type: String) = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() })
            .build()
        val subs = client.queryProductDetails(params(ProProducts.subscriptions, ProductType.SUBS)).productDetailsList.orEmpty()
        val inApp = client.queryProductDetails(params(listOf(ProProducts.LIFETIME), ProductType.INAPP)).productDetailsList.orEmpty()
        return (subs + inApp).mapNotNull { details ->
            when (details.productId) {
                ProProducts.MONTHLY, ProProducts.YEARLY -> {
                    // The base plan's offer; its last pricing phase is the regular price after any trial.
                    val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
                    val plan = if (details.productId == ProProducts.MONTHLY) ProPlan.MONTHLY else ProPlan.YEARLY
                    ProOffer(plan, price, details, offer.offerToken)
                }
                ProProducts.LIFETIME -> details.oneTimePurchaseOfferDetails?.let {
                    ProOffer(ProPlan.LIFETIME, it.formattedPrice, details, offerToken = null)
                }
                else -> null
            }
        }.sortedBy { it.plan }
    }

    /** Acknowledges new Pro purchases (Play refunds unacknowledged ones) and says whether any is active. */
    private suspend fun handle(purchases: List<Purchase>): Boolean {
        val owned = purchases.filter { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in ProProducts.all }
        }
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            val result = client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
            if (result.responseCode != BillingClient.BillingResponseCode.OK) Log.w("ProRepository", "Acknowledge failed: ${result.debugMessage}")
        }
        val sub = owned.any { p -> p.products.any { it in ProProducts.subscriptions } }
        prefs.edit().putBoolean(KEY_SUBSCRIBED, sub).apply()
        subscribed.value = sub
        return owned.isNotEmpty()
    }

    private fun setPurchased(value: Boolean) {
        prefs.edit().putBoolean(KEY_PURCHASED, value).apply()
        purchased.value = value
    }

    private companion object {
        const val KEY_PURCHASED = "purchased"
        const val KEY_DEBUG = "debug_override"
        const val KEY_SUBSCRIBED = "subscribed"
    }
}
