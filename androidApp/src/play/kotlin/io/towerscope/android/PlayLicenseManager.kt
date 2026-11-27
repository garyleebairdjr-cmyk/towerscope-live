package io.towerscope.android

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.towerscope.license.LicenseManager
import io.towerscope.license.LicenseState
import io.towerscope.license.Trial
import io.towerscope.license.UnlockCache
import io.towerscope.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/**
 * The Play edition's one-time unlock.
 *
 * - Product: [PRODUCT_ID], a one-time, non-consumable in-app product. It is never consumed.
 * - Trial: 7 days from the first open, enforced by the app (see [Trial]). There is no server.
 * - Unlock is granted only for a purchase in the PURCHASED state (not PENDING), and acknowledged right away.
 *   Google refunds purchases that aren't acknowledged within three days; for license testers that window is
 *   only a few minutes, so a test purchase that "disappears" usually means acknowledgement failed.
 * - Purchases are restored from Google Play on every start and resume, and an acknowledged unlock is cached
 *   on the phone so the app keeps working offline.
 * - If Google Play can't be reached after the trial with no cached unlock, the paywall stays up with a
 *   Retry button. It never unlocks on an error.
 */
class PlayLicenseManager(
    private val context: Context,
    private val store: KeyValueStore,
    private val now: () -> Long,
) : LicenseManager {
    private val _state = MutableStateFlow(
        LicenseState(unlocked = UnlockCache.get(store), trialEndsAtMs = Trial.endsAt(store, now())),
    )
    override val state: StateFlow<LicenseState> = _state.asStateFlow()

    private var client: BillingClient? = null
    private var activity: WeakReference<Activity>? = null
    private var product: ProductDetails? = null

    fun connect(activity: Activity) {
        this.activity = WeakReference(activity)
        _state.update { it.copy(trialEndsAtMs = Trial.endsAt(store, now())) }
        if (client != null) { restore(); return }
        // A client can't reconnect after endConnection(), so build a fresh one per visit.
        val c = BillingClient.newBuilder(context)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        _state.update { it.copy(busy = true) }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    _state.update { it.copy(billingReady = true, busy = false, message = null) }
                    loadProduct()
                    restore()
                } else {
                    unreachable()
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(billingReady = false) }
            }
        })
    }

    fun disconnect() {
        client?.endConnection()
        client = null
        activity = null
        _state.update { it.copy(billingReady = false, busy = false) }
    }

    override fun purchase() {
        val c = client
        val act = activity?.get()
        val details = product
        if (c == null || act == null || !_state.value.billingReady) { retryConnection(); return }
        if (details == null) {
            loadProduct()
            _state.update { it.copy(message = "Getting the price from Google Play. Try again in a moment.") }
            return
        }
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .apply { if (offer != null) setOfferToken(offer) }
                        .build(),
                ),
            )
            .build()
        val result = c.launchBillingFlow(act, params)
        if (result.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) restore()
        else if (result.responseCode != BillingResponseCode.OK) {
            _state.update { it.copy(message = "Google Play couldn't start the purchase (${result.debugMessage.ifBlank { "code ${result.responseCode}" }}).") }
        }
    }

    /** Asks Google Play which purchases this account owns. Also the Retry action when Play was unreachable. */
    override fun restore() {
        val c = client
        if (c == null || !_state.value.billingReady) { retryConnection(); return }
        _state.update { it.copy(busy = true) }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingResponseCode.OK) { unreachable(); return@queryPurchasesAsync }
            val ours = purchases.filter { PRODUCT_ID in it.products }
            if (ours.none { it.purchaseState == Purchase.PurchaseState.PURCHASED || it.purchaseState == Purchase.PurchaseState.PENDING }) {
                // Play answered and this account doesn't own it (never bought, or refunded): drop any cached unlock.
                UnlockCache.set(store, false)
                _state.update { it.copy(unlocked = false, busy = false) }
            } else {
                ours.forEach(::handle)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun retryConnection() {
        val act = activity?.get() ?: return
        disconnect()
        connect(act)
    }

    private fun loadProduct() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(ProductType.INAPP).build()),
            )
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val pd = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID } ?: return@queryProductDetailsAsync
            product = pd
            val price = pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice ?: pd.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update { it.copy(price = price) }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().filter { PRODUCT_ID in it.products }.forEach(::handle)
            BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            BillingResponseCode.USER_CANCELED -> _state.update { it.copy(message = null) }
            else -> _state.update { it.copy(message = "The purchase didn't go through. Nothing was charged. You can try again.") }
        }
    }

    private fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _state.update { it.copy(unlocked = true, message = null) }
                if (p.isAcknowledged) { UnlockCache.set(store, true); return }
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                client?.acknowledgePurchase(ack) { r ->
                    if (r.responseCode == BillingResponseCode.OK) UnlockCache.set(store, true)
                    // If this fails, the next restore() tries again; Play allows three days.
                }
            }
            Purchase.PurchaseState.PENDING -> _state.update {
                it.copy(message = "Your payment is pending. TowerScope unlocks as soon as Google Play confirms it.")
            }
            else -> {}
        }
    }

    private fun unreachable() {
        _state.update {
            it.copy(
                billingReady = false, busy = false,
                message = if (it.unlocked) null else "Couldn't reach Google Play. Check your connection, then tap Retry.",
            )
        }
    }

    companion object {
        /** Must match the one-time product created in Play Console. */
        const val PRODUCT_ID = "towerscope_unlock"
    }
}
