package com.example.mentzertracker.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Rarelift Pro": a one-time, non-consumable in-app product sold through Google Play Billing.
 *
 * Set up in Play Console -> Monetize -> In-app products with the id [PRODUCT_ID]. Ownership comes
 * from Play on every start (queryPurchases), and is cached so Pro still works offline. There's no
 * server, so the check is on-device only, which is fine for an app of this size.
 */
object ProAccess {
    const val SOLD_HERE = true
    const val PRODUCT_ID = "rarelift_pro"

    private const val PREFS = "pro_access"
    private const val KEY_PRO = "is_pro"

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    /** Localised price from Play ("₹199"), once known. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short messages for a snackbar (purchase pending, failed, restored...). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var app: Context? = null
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    fun init(ctx: Context) {
        if (client != null) return
        val appCtx = ctx.applicationContext
        app = appCtx
        _isPro.value = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PRO, false)
        client = BillingClient.newBuilder(appCtx)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) { refresh(); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }
            override fun onBillingServiceDisconnected() { /* auto-reconnect is enabled */ }
        })
    }

    /** Re-reads ownership and price from Play. Also what "Restore purchase" does. */
    fun refresh() {
        val c = client ?: return
        if (!c.isReady) { connect(); return }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter { PRODUCT_ID in it.products }
                setPro(owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED })
                owned.forEach(::acknowledgeIfNeeded)
            }
        }
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
            )).build()
        ) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetailsResult.productDetailsList.firstOrNull()
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    fun purchase(activity: Activity) {
        val c = client
        val d = details
        if (c == null || !c.isReady || d == null) {
            _messages.tryEmit("Google Play isn't reachable right now. Try again in a moment.")
            refresh()
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        )).build()
        c.launchBillingFlow(activity, params)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty()
                .filter { PRODUCT_ID in it.products }
                .forEach { p ->
                    when (p.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            setPro(true)
                            acknowledgeIfNeeded(p)
                            _messages.tryEmit("Rarelift Pro unlocked. Thank you!")
                        }
                        Purchase.PurchaseState.PENDING ->
                            _messages.tryEmit("Payment pending. Pro unlocks as soon as it goes through.")
                        else -> Unit
                    }
                }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { refresh(); _messages.tryEmit("You already own Pro. Restored.") }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _messages.tryEmit("Purchase didn't go through (${result.debugMessage.ifBlank { "code ${result.responseCode}" }}).")
        }
    }

    /** Play refunds purchases that aren't acknowledged within 3 days. */
    private fun acknowledgeIfNeeded(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED || p.isAcknowledged) return
        client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) { }
    }

    private fun setPro(on: Boolean) {
        _isPro.value = on
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_PRO, on)?.apply()
    }
}
