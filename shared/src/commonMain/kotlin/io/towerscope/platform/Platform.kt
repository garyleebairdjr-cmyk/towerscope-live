package io.towerscope.platform

import io.ktor.client.HttpClient
import io.towerscope.model.DeviceInfo
import io.towerscope.model.GeoFix
import io.towerscope.model.RadioSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the shared code needs from the host OS. */
interface PlatformServices {
    val device: DeviceInfo
    val permissions: PermissionGate
    val store: KeyValueStore

    /** Periodic radio snapshots. Must only be collected once permissions are granted. */
    fun radioSnapshots(intervalMs: Long): Flow<RadioSnapshot>

    fun locationFixes(): Flow<GeoFix>

    /** Location fixes no more often than [intervalMs]; used to save battery while recording with the app closed. */
    fun locationFixes(intervalMs: Long): Flow<GeoFix> = locationFixes()

    /**
     * Device heading in degrees clockwise from north. May never emit if no compass.
     * It is referenced to true north once [declinationDeg] is known, and to magnetic north before that.
     */
    fun headings(): Flow<Float>

    /**
     * Magnetic declination at the current position in degrees (east positive: magnetic north lies east
     * of true north), or null while unknown. Read alongside each heading.
     */
    val declinationDeg: Float? get() = null

    fun createHttpClient(): HttpClient

    fun nowMs(): Long

    fun openUrl(url: String)

    /** Current power-saving / VPN / update state. Cheap; called on every radio refresh. */
    fun deviceConditions(): io.towerscope.calc.DeviceConditions = io.towerscope.calc.DeviceConditions()

    /** Opens a system settings page. Apps can't change these settings; the user does. */
    fun openSystemSettings(target: io.towerscope.calc.SettingsTarget) {}

    /** Installed app version (e.g. "1.0.12"), if the platform can report it. */
    val appVersion: String? get() = null

    /** ISO 3166 country of the device locale (e.g. "US"), used to pick default units. */
    val regionCode: String? get() = null

    /** Where long-term history is kept. Platforms with a file system should override this. */
    val historyFile: io.towerscope.data.HistoryFile get() = io.towerscope.data.KeyValueHistoryFile(store)

    /** Keeps recording while the app is closed, or null where that isn't offered. */
    val backgroundRecorder: BackgroundRecorder? get() = null

    /** One-time unlock (Play edition), or null where the app is free. */
    val license: io.towerscope.license.LicenseManager? get() = null

    /** Offers [content] as a file to share or save. Returns false when this platform can't. */
    fun shareFile(fileName: String, mimeType: String, content: String): Boolean = false

    /** Hour of day (0–23) in the phone's time zone. The default is UTC. */
    fun localHour(epochMs: Long): Int = ((epochMs / 3_600_000L) % 24).toInt()
}

data class PermissionState(
    val location: Boolean = false,
    val phoneState: Boolean = false,
    /** Phone-state permission does not exist on this platform. */
    val phoneStateApplicable: Boolean = true,
    val requestedOnce: Boolean = false,
) {
    val coreGranted: Boolean get() = location
    val allGranted: Boolean get() = location && (phoneState || !phoneStateApplicable)
}

/** Bridges the OS permission dialog (owned by the host UI) to shared code. */
class PermissionGate {
    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state

    var requester: (() -> Unit)? = null
    var settingsOpener: (() -> Unit)? = null

    fun update(state: PermissionState) {
        _state.value = state
    }

    fun request() {
        requester?.invoke()
    }

    fun openSettings() {
        settingsOpener?.invoke()
    }
}

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}
