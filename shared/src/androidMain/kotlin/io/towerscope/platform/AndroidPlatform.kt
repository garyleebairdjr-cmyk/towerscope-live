package io.towerscope.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.towerscope.model.DeviceInfo
import io.towerscope.model.GeoFix
import io.towerscope.model.PlatformKind
import io.towerscope.model.RadioSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class AndroidPlatform(context: Context) : PlatformServices {
    private val app = context.applicationContext
    private val radio = AndroidRadio(app)

    @Volatile private var lastFix: GeoFix? = null

    override val permissions = PermissionGate()

    override val device = DeviceInfo(
        platform = PlatformKind.ANDROID,
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        model = Build.MODEL,
        osName = "Android",
        osVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
    )

    override val store: KeyValueStore = object : KeyValueStore {
        private val prefs = app.getSharedPreferences("towerscope", Context.MODE_PRIVATE)
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun putString(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }

    /** Re-reads the granted permissions; call from onResume and after the permission dialog. */
    fun refreshPermissions(requestedOnce: Boolean = permissions.state.value.requestedOnce) {
        fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED
        permissions.update(
            PermissionState(
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                phoneState = granted(Manifest.permission.READ_PHONE_STATE),
                phoneStateApplicable = true,
                requestedOnce = requestedOnce,
            ),
        )
    }

    override fun radioSnapshots(intervalMs: Long): Flow<RadioSnapshot> = radio.snapshots(intervalMs)

    @SuppressLint("MissingPermission")
    override fun locationFixes(): Flow<GeoFix> = callbackFlow {
        val lm = app.getSystemService(LocationManager::class.java)
        var best: Location? = null

        fun offer(loc: Location) {
            val b = best
            // Prefer fresh, accurate fixes; accept a worse one if the current is >10 s old.
            val accept = b == null || loc.accuracy <= b.accuracy || loc.time - b.time > 10_000
            if (!accept) return
            best = loc
            val fix = GeoFix(
                lat = loc.latitude,
                lon = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                bearingDeg = if (loc.hasBearing()) loc.bearing.toDouble() else null,
                timestampMs = loc.time,
                provider = loc.provider,
            )
            lastFix = fix
            trySend(fix)
        }

        val listener = LocationListener { offer(it) }
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        try {
            providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { offer(it) }
            providers.forEach { lm.requestLocationUpdates(it, 2000L, 0f, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    override fun headings(): Flow<Float> = callbackFlow {
        val sm = app.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        var sinAvg = 0.0
        var cosAvg = 1.0
        var lastSent = Float.NaN
        @Suppress("DEPRECATION")
        val display = app.getSystemService(WindowManager::class.java)?.defaultDisplay

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (ax, ay) = when (display?.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orient)
                var deg = Math.toDegrees(orient[0].toDouble())
                lastFix?.let { f ->
                    deg += GeomagneticField(f.lat.toFloat(), f.lon.toFloat(), (f.altitudeM ?: 0.0).toFloat(), System.currentTimeMillis()).declination
                }
                // Circular low-pass filter to steady the needle.
                val r = Math.toRadians(deg)
                sinAvg = sinAvg * 0.85 + sin(r) * 0.15
                cosAvg = cosAvg * 0.85 + cos(r) * 0.15
                val h = ((Math.toDegrees(atan2(sinAvg, cosAvg)) + 360) % 360).toFloat()
                if (lastSent.isNaN() || kotlin.math.abs(((h - lastSent + 540) % 360) - 180) >= 1f) {
                    lastSent = h
                    trySend(h)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }

    override fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        defaultRequest { header("User-Agent", "TowerScope/1.0 (Android)") }
        expectSuccess = false
    }

    override fun nowMs(): Long = System.currentTimeMillis()

    override val regionCode: String? get() = java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }

    override fun deviceConditions(): io.towerscope.calc.DeviceConditions {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val vpn = runCatching {
            cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        }.getOrNull()
        return io.towerscope.calc.DeviceConditions(
            powerSaveMode = pm?.isPowerSaveMode,
            vpnActive = vpn,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
        )
    }

    override fun openSystemSettings(target: io.towerscope.calc.SettingsTarget) {
        val action = when (target) {
            io.towerscope.calc.SettingsTarget.AIRPLANE_MODE -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
            // Opens "Mobile networks", which holds the network mode (5G/LTE) selector on Pixel and Samsung.
            io.towerscope.calc.SettingsTarget.NETWORK_MODE -> android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS
            io.towerscope.calc.SettingsTarget.BATTERY_SAVER -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            io.towerscope.calc.SettingsTarget.VPN -> android.provider.Settings.ACTION_VPN_SETTINGS
            // There is no public intent for Wi-Fi calling; the wireless/connections page leads to it.
            io.towerscope.calc.SettingsTarget.WIFI_CALLING -> android.provider.Settings.ACTION_WIRELESS_SETTINGS
            io.towerscope.calc.SettingsTarget.SYSTEM_UPDATE -> "android.settings.SYSTEM_UPDATE_SETTINGS"
        }
        val fallbacks = listOf(action, android.provider.Settings.ACTION_WIRELESS_SETTINGS, android.provider.Settings.ACTION_SETTINGS)
        for (a in fallbacks) {
            val ok = runCatching { app.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }
}
