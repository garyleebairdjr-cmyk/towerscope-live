package io.towerscope.license

import io.towerscope.platform.KeyValueStore
import kotlinx.coroutines.flow.StateFlow

/** Where the one-time unlock stands. Only the Play edition has one; everywhere else the app is free. */
data class LicenseState(
    /** Bought (now, or earlier and cached on this phone). */
    val unlocked: Boolean = false,
    /** When the free trial ends. */
    val trialEndsAtMs: Long = 0,
    /** Price from Google Play in the person's currency, or null until Play answers. */
    val price: String? = null,
    /** Google Play is connected and can take a purchase. */
    val billingReady: Boolean = false,
    val busy: Boolean = false,
    /** Short status for the paywall, e.g. a pending payment or Play being unreachable. */
    val message: String? = null,
) {
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
