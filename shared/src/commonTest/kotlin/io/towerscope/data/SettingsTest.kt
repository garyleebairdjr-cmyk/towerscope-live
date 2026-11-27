package io.towerscope.data

import io.towerscope.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Saved settings must survive app updates. Updates keep the stored values, but only under the
 * names they were saved with, so renaming a key here would make every user's API key disappear.
 */
class SettingsTest {
    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    @Test
    fun keySavedByAnEarlierVersionIsStillRead() {
        // What versions up to 0.9.7 wrote to the "towerscope" preferences.
        val store = MapStore(mutableMapOf("ocid_key" to "pk.0123456789abcdef0123456789abcdef", "units" to "IMPERIAL"))
        val s = SettingsRepository(store).load()
        assertEquals("pk.0123456789abcdef0123456789abcdef", s.openCellIdKey)
        assertEquals(UnitSystem.IMPERIAL, s.units)
    }

    @Test
    fun saveAndLoadRoundTripKeepsTheKey() {
        val store = MapStore()
        val repo = SettingsRepository(store)
        repo.save(AppSettings(openCellIdKey = "  pk.abc  "))
        assertEquals("pk.abc", repo.load().openCellIdKey)
        assertEquals("pk.abc", store.map["ocid_key"])
    }

    @Test
    fun historyIsOptInOnAFreshInstall() {
        val s = SettingsRepository(MapStore()).load()
        assertEquals(false, s.recordHistory)
        assertEquals(HistoryLocation.APPROXIMATE, s.historyLocation)
        assertEquals(false, s.backgroundRecording)
    }

    @Test
    fun someoneWhoAlreadyRecordsKeepsTheirChoice() {
        // Versions up to 0.9.11 saved every setting, history included, whenever any setting changed.
        val store = MapStore(mutableMapOf("record_history" to "true", "history_location" to "EXACT"))
        val s = SettingsRepository(store).load()
        assertEquals(true, s.recordHistory)
        assertEquals(HistoryLocation.EXACT, s.historyLocation)
    }
}
