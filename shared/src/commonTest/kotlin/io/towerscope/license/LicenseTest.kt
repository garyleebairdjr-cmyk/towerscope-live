package io.towerscope.license

import io.towerscope.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseTest {
    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private val day = LicenseState.DAY_MS
    private val t0 = 1_800_000_000_000L

    @Test
    fun trialStartsAtFirstOpenAndDoesNotMove() {
        val store = MapStore()
        assertEquals(t0 + 7 * day, Trial.endsAt(store, t0))
        assertEquals(t0 + 7 * day, Trial.endsAt(store, t0 + 3 * day)) // later opens keep the first time
    }

    @Test
    fun aFirstOpenTimeInTheFutureIsReset() {
        // A clock that was wrong when the app first opened must not grant a longer trial.
        val store = MapStore(mutableMapOf("first_open_at" to (t0 + 365 * day).toString()))
        assertEquals(t0 + 7 * day, Trial.endsAt(store, t0))
    }

    @Test
    fun lockedOnlyAfterTheTrialWithoutAnUnlock() {
        val s = LicenseState(trialEndsAtMs = t0 + 7 * day)
        assertFalse(s.locked(t0))
        assertEquals(7, s.trialDaysLeft(t0))
        assertEquals(1, s.trialDaysLeft(t0 + 6 * day + 1))
        assertTrue(s.locked(t0 + 7 * day))
        assertFalse(s.copy(unlocked = true).locked(t0 + 30 * day))
    }

    @Test
    fun priceFallsBackWhenPlayHasNotAnswered() {
        assertEquals("$1.99", LicenseState().displayPrice)
        assertEquals("1,99 €", LicenseState(price = "1,99 €").displayPrice)
    }

    @Test
    fun unlockCacheRoundTrips() {
        val store = MapStore()
        assertFalse(UnlockCache.get(store))
        UnlockCache.set(store, true)
        assertTrue(UnlockCache.get(store))
        UnlockCache.set(store, false)
        assertFalse(UnlockCache.get(store))
    }
}
