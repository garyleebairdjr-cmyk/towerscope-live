package io.towerscope.license

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The StoreKit 2 side, written in Swift (iosApp/TowerScope/StoreKitBridge.swift) because StoreKit 2 is a
 * Swift-only API. Kotlin calls these when the person taps a paywall button; Swift reports back through
 * [AppStoreLicenseManager.publish]. All calls happen on the main thread.
 */
interface AppStoreBridge {
    /** Loads products and current entitlements, and listens for transaction updates. Called once at startup. */
    fun refresh()
    fun startTrial()
    fun purchaseUnlock()
    fun restore()
}

/** The App Store edition's unlock, shared with the paywall through [LicenseManager]. */
class AppStoreLicenseManager : LicenseManager {
    private val _state = MutableStateFlow(LicenseState(known = false, trialStartRequired = true))
    override val state: StateFlow<LicenseState> = _state.asStateFlow()

    var bridge: AppStoreBridge? = null

    override fun purchase() { bridge?.purchaseUnlock() }
    override fun restore() { bridge?.restore() }
    override fun startTrial() { bridge?.startTrial() }

    override val storeName: String get() = "the App Store"
    override val accountName: String get() = "Apple Account"
    override val trialNote: String
        get() = "The trial is linked to your Apple Account where possible, but a new device or reinstalling may start a new one."

    /**
     * Called from Swift with what StoreKit reports.
     * @param trialStartMs when the trial began (epoch ms), or 0 if the person hasn't started it.
     */
    fun publish(unlocked: Boolean, trialStartMs: Long, price: String?, storeReady: Boolean, busy: Boolean, message: String?) {
        _state.value = LicenseState(
            unlocked = unlocked,
            trialEndsAtMs = if (trialStartMs > 0) trialStartMs + Trial.LENGTH_MS else 0,
            price = price,
            billingReady = storeReady,
            busy = busy,
            message = message,
            known = true,
            trialStartRequired = true,
        )
    }
}

/** What Swift needs to reach: `IosEdition.shared.license`. */
object IosEdition {
    val license = AppStoreLicenseManager()

    /** Product IDs, created later in App Store Connect. Keep in sync with StoreKitBridge.swift and IOS.md. */
    const val UNLOCK_PRODUCT_ID = "towerscope_unlock"
    const val TRIAL_PRODUCT_ID = "towerscope_7_day_trial"
}
