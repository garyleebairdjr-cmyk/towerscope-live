package io.towerscope.license

import io.towerscope.platform.KeyValueStore
import kotlinx.coroutines.flow.StateFlow

/** Where the one-time unlock stands. Only the store editions (Google Play, App Store) have one. */
data class LicenseState(
    /** Bought (now, or earlier and cached on this phone). */
    val unlocked: Boolean = false,
    /** When the free trial ends. */
    val trialEndsAtMs: Long = 0,
    /** Price from the store in the person's currency, or null until the store answers. */
    val price: String? = null,
    /** The store is connected and can take a purchase. */
    val billingReady: Boolean = false,
    val busy: Boolean = false,
    /** Short status for the paywall, e.g. a pending payment or the store being unreachable. */
    val message: String? = null,
    /** False until the store has answered once, so the app doesn't flash a paywall at launch. */
    val known: Boolean = true,
    /**
     * The person starts the trial themselves after reading what it includes (App Store guideline 3.1.1).
     * Until then [trialEndsAtMs] is 0 and the paywall offers "Start free trial". Google Play's trial starts
     * on first open, so it leaves this false.
     */
    val trialStartRequired: Boolean = false,
) {
    val trialNotStarted: Boolean get() = trialStartRequired && trialEndsAtMs == 0L
    fun trialActive(nowMs: Long): Boolean = nowMs < trialEndsAtMs
    fun locked(nowMs: Long): Boolean = !unlocked && !trialActive(nowMs)
    fun trialDaysLeft(nowMs: Long): Int = if (!trialActive(nowMs)) 0 else ((trialEndsAtMs - nowMs + DAY_MS - 1) / DAY_MS).toInt()
    val displayPrice: String get() = price ?: FALLBACK_PRICE

    companion object {
        const val DAY_MS = 24L * 3600 * 1000
        const val FALLBACK_PRICE = "$1.99"
    }
}

interface LicenseManager {
    val state: StateFlow<LicenseState>
    fun purchase()
    fun restore()

    /** Starts the free trial, for stores where the person starts it ([LicenseState.trialStartRequired]). */
    fun startTrial() {}

    /** Wording for the paywall. */
    val storeName: String get() = "Google Play"
    val accountName: String get() = "Google account"
    /** How the trial is tracked, said plainly (it can be reset; there's no server). */
    val trialNote: String get() = "The free week is tracked on this phone only, so it isn't carried between phones."
}

/**
 * The free trial is enforced by the app, not by Google Play. The first-open time is kept in the app's
 * private settings, so uninstalling and reinstalling starts a new trial. There is no server.
 */
object Trial {
    const val LENGTH_MS = 7 * LicenseState.DAY_MS
    private const val K_FIRST_OPEN = "first_open_at"

    /** The first time the app was opened, recording [nowMs] if this is the first time. */
    fun firstOpenAt(store: KeyValueStore, nowMs: Long): Long =
        store.getString(K_FIRST_OPEN)?.toLongOrNull()?.takeIf { it <= nowMs }
            ?: nowMs.also { store.putString(K_FIRST_OPEN, it.toString()) }

    fun endsAt(store: KeyValueStore, nowMs: Long): Long = firstOpenAt(store, nowMs) + LENGTH_MS
}

/** Remembers an acknowledged purchase so the app stays unlocked offline. */
object UnlockCache {
    private const val K_UNLOCKED = "unlock_acknowledged"
    fun get(store: KeyValueStore): Boolean = store.getString(K_UNLOCKED) == "1"
    fun set(store: KeyValueStore, on: Boolean) = store.putString(K_UNLOCKED, if (on) "1" else null)
}
