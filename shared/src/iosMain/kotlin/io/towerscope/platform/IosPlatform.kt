package io.towerscope.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.towerscope.model.DeviceInfo
import io.towerscope.model.GeoFix
import io.towerscope.model.NetworkStatus
import io.towerscope.model.PlatformKind
import io.towerscope.model.RadioCapabilities
import io.towerscope.model.RadioSnapshot
import io.towerscope.model.Transport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreTelephony.CTRadioAccessTechnologyCDMA1x
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORev0
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevA
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevB
import platform.CoreTelephony.CTRadioAccessTechnologyEdge
import platform.CoreTelephony.CTRadioAccessTechnologyGPRS
import platform.CoreTelephony.CTRadioAccessTechnologyHSDPA
import platform.CoreTelephony.CTRadioAccessTechnologyHSUPA
import platform.CoreTelephony.CTRadioAccessTechnologyLTE
import platform.CoreTelephony.CTRadioAccessTechnologyNR
import platform.CoreTelephony.CTRadioAccessTechnologyNRNSA
import platform.CoreTelephony.CTRadioAccessTechnologyWCDMA
import platform.CoreTelephony.CTRadioAccessTechnologyeHRPD
import platform.CoreTelephony.CTTelephonyNetworkInfo
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
import platform.Foundation.lowPowerModeEnabled
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.darwin.NSObject

/**
 * iOS implementation. Apple does not expose cell identity or signal strength to third-party
 * apps, so the radio snapshot carries only the access technology; tower data comes from the
 * OpenCelliD area search around the GPS position.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatform : PlatformServices {
    private val manager = CLLocationManager()
    private val delegate = LocationDelegate()
    private val telephony = CTTelephonyNetworkInfo()

    override val permissions = PermissionGate()

    override val device = DeviceInfo(
        platform = PlatformKind.IOS,
        manufacturer = "Apple",
        model = UIDevice.currentDevice.model,
        osName = UIDevice.currentDevice.systemName,
        osVersion = UIDevice.currentDevice.systemVersion,
    )

    override val store: KeyValueStore = object : KeyValueStore {
        private val defaults = NSUserDefaults.standardUserDefaults
        override fun getString(key: String): String? = defaults.stringForKey(key)
        override fun putString(key: String, value: String?) {
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        delegate.onAuthorization = { refreshPermissions(requested = true) }
        permissions.requester = { manager.requestWhenInUseAuthorization() }
        permissions.settingsOpener = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
            }
        }
        refreshPermissions(requested = false)
    }

    private fun refreshPermissions(requested: Boolean) {
        val status = manager.authorizationStatus
        val granted = status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
        permissions.update(
            PermissionState(
                location = granted,
                phoneState = true,
                phoneStateApplicable = false,
                requestedOnce = requested || status != kCLAuthorizationStatusNotDetermined,
            ),
        )
    }

    override fun radioSnapshots(intervalMs: Long): Flow<RadioSnapshot> = flow {
        while (true) {
            val tech = telephony.serviceCurrentRadioAccessTechnology?.values?.firstOrNull() as? String
            emit(
                RadioSnapshot(
                    cells = emptyList(),
                    network = NetworkStatus(
                        networkTypeLabel = techLabel(tech),
                        isNrNsa = tech == CTRadioAccessTechnologyNRNSA,
                        transport = Transport.UNKNOWN,
                    ),
                    timestampMs = nowMs(),
                    capabilities = RadioCapabilities(
                        canReadCells = false,
                        limitationNote = "iOS doesn't let apps read cell IDs or signal strength. TowerScope shows your network type, " +
                            "the nearest towers from OpenCelliD and a full speed test. Use the Android app for live radio measurements.",
                    ),
                ),
            )
            delay(intervalMs)
        }
    }

    private fun techLabel(t: String?): String? = when (t) {
        CTRadioAccessTechnologyNR -> "5G SA"
        CTRadioAccessTechnologyNRNSA -> "5G NSA"
        CTRadioAccessTechnologyLTE -> "LTE"
        CTRadioAccessTechnologyHSDPA, CTRadioAccessTechnologyHSUPA -> "HSPA"
        CTRadioAccessTechnologyWCDMA -> "UMTS"
        CTRadioAccessTechnologyEdge -> "EDGE"
        CTRadioAccessTechnologyGPRS -> "GPRS"
        CTRadioAccessTechnologyCDMA1x -> "CDMA"
        CTRadioAccessTechnologyCDMAEVDORev0, CTRadioAccessTechnologyCDMAEVDORevA, CTRadioAccessTechnologyCDMAEVDORevB -> "EV-DO"
        CTRadioAccessTechnologyeHRPD -> "eHRPD"
        null -> null
        else -> t.removePrefix("CTRadioAccessTechnology")
    }

    override fun locationFixes(): Flow<GeoFix> = callbackFlow {
        delegate.onLocation = { loc ->
            val (lat, lon) = loc.coordinate.useContents { latitude to longitude }
            trySend(
                GeoFix(
                    lat = lat,
                    lon = lon,
                    accuracyM = loc.horizontalAccuracy.takeIf { it >= 0 },
                    altitudeM = loc.altitude.takeIf { loc.verticalAccuracy >= 0 },
                    speedMps = loc.speed.takeIf { it >= 0 },
                    bearingDeg = loc.course.takeIf { it >= 0 },
                    timestampMs = (loc.timestamp.timeIntervalSince1970 * 1000).toLong(),
                    provider = "CoreLocation",
                ),
            )
        }
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            delegate.onLocation = null
        }
    }

    override fun headings(): Flow<Float> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) {
            awaitClose { }
            return@callbackFlow
        }
        delegate.onHeading = { h ->
            val deg = if (h.trueHeading >= 0) h.trueHeading else h.magneticHeading
            trySend(deg.toFloat())
        }
        manager.headingFilter = 1.0
        manager.startUpdatingHeading()
        awaitClose {
            manager.stopUpdatingHeading()
            delegate.onHeading = null
        }
    }

    override fun createHttpClient(): HttpClient = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        defaultRequest { header("User-Agent", "TowerScope/1.0 (iOS)") }
        expectSuccess = false
    }

    override fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    override val regionCode: String? get() = NSLocale.currentLocale.countryCode

    override fun deviceConditions(): io.towerscope.calc.DeviceConditions =
        io.towerscope.calc.DeviceConditions(powerSaveMode = NSProcessInfo.processInfo.lowPowerModeEnabled)

    // iOS only allows opening this app's own Settings page; system pages can't be deep-linked.
    override fun openSystemSettings(target: io.towerscope.calc.SettingsTarget) {
        openUrl(platform.UIKit.UIApplicationOpenSettingsURLString)
    }

    override fun openUrl(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }
}

private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    var onLocation: ((CLLocation) -> Unit)? = null
    var onHeading: ((CLHeading) -> Unit)? = null
    var onAuthorization: (() -> Unit)? = null

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.let { onLocation?.invoke(it) }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        onHeading?.invoke(didUpdateHeading)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthorization?.invoke()
    }
}
