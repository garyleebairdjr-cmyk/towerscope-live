package io.towerscope.data

import io.towerscope.platform.KeyValueStore

enum class Environment(val label: String, val description: String) {
    URBAN("Urban", "Dense city, tall buildings"),
    SUBURBAN("Suburban", "Houses, trees, light commercial"),
    RURAL("Rural", "Open fields, few obstructions"),
}

enum class UnitSystem(val label: String) {
    METRIC("Metric (km, m)"), IMPERIAL("Imperial (mi, ft)");

    companion object {
        /** Countries that use miles for road/everyday distances. */
        private val MILES_COUNTRIES = setOf("US", "GB", "LR", "MM", "PR", "GU", "VI", "AS", "MP")
        fun defaultFor(region: String?): UnitSystem =
            if (region?.uppercase() in MILES_COUNTRIES) IMPERIAL else METRIC
    }
}

data class AppSettings(
    val openCellIdKey: String = "",
    val environment: Environment = Environment.SUBURBAN,
    val towerHeightM: Double = 30.0,
    val handsetHeightM: Double = 1.5,
    /** Effective radiated power of a single LTE/NR reference-signal resource element (dBm). */
    val referenceEirpDbm: Double = 30.0,
    val handsetTxPowerDbm: Double = 23.0,
    val units: UnitSystem = UnitSystem.METRIC,
    val refreshSeconds: Int = 3,
    /** Keep signal readings and speed tests (with GPS position) on the phone to learn patterns over time. */
    val recordHistory: Boolean = true,
    val historyLocation: HistoryLocation = HistoryLocation.EXACT,
) {
    val hasApiKey: Boolean get() = openCellIdKey.isNotBlank()
}

class SettingsRepository(private val store: KeyValueStore, private val defaultUnits: UnitSystem = UnitSystem.METRIC) {
    fun load(): AppSettings {
        val d = AppSettings(units = defaultUnits)
        return AppSettings(
            openCellIdKey = store.getString(K_KEY) ?: d.openCellIdKey,
            environment = store.getString(K_ENV)?.let { v -> Environment.entries.firstOrNull { it.name == v } } ?: d.environment,
            towerHeightM = store.getString(K_HEIGHT)?.toDoubleOrNull() ?: d.towerHeightM,
            referenceEirpDbm = store.getString(K_EIRP)?.toDoubleOrNull() ?: d.referenceEirpDbm,
            handsetTxPowerDbm = store.getString(K_UETX)?.toDoubleOrNull() ?: d.handsetTxPowerDbm,
            units = store.getString(K_UNITS)?.let { v -> UnitSystem.entries.firstOrNull { it.name == v } } ?: d.units,
            refreshSeconds = store.getString(K_REFRESH)?.toIntOrNull() ?: d.refreshSeconds,
            recordHistory = store.getString(K_HISTORY)?.toBooleanStrictOrNull() ?: d.recordHistory,
            historyLocation = store.getString(K_HISTORY_LOC)?.let { v -> HistoryLocation.entries.firstOrNull { it.name == v } } ?: d.historyLocation,
        )
    }

    fun save(s: AppSettings) {
        store.putString(K_KEY, s.openCellIdKey.trim())
        store.putString(K_ENV, s.environment.name)
        store.putString(K_HEIGHT, s.towerHeightM.toString())
        store.putString(K_EIRP, s.referenceEirpDbm.toString())
        store.putString(K_UETX, s.handsetTxPowerDbm.toString())
        store.putString(K_UNITS, s.units.name)
        store.putString(K_REFRESH, s.refreshSeconds.toString())
        store.putString(K_HISTORY, s.recordHistory.toString())
        store.putString(K_HISTORY_LOC, s.historyLocation.name)
    }

    private companion object {
        const val K_KEY = "ocid_key"
        const val K_ENV = "environment"
        const val K_HEIGHT = "tower_height"
        const val K_EIRP = "ref_eirp"
        const val K_UETX = "ue_tx"
        const val K_UNITS = "units"
        const val K_REFRESH = "refresh"
        const val K_HISTORY = "record_history"
        const val K_HISTORY_LOC = "history_location"
    }
}
