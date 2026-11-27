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
    @Volatile private var declination: Float? = null
    private var declinationFix: GeoFix? = null

    override val declinationDeg: Float? get() = declination

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
        // Don't rename: app updates keep this file, so a new name would lose saved settings and the API key.
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
                // Android only reveals cell IDs with precise location; approximate counts only where an edition opts out of precise.
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    (!FeatureFlags.current.allowPreciseLocation && granted(Manifest.permission.ACCESS_COARSE_LOCATION)),
                phoneState = granted(Manifest.permission.READ_PHONE_STATE),
                phoneStateApplicable = true,
                requestedOnce = requestedOnce,
            ),
        )
    }

    override fun radioSnapshots(intervalMs: Long): Flow<RadioSnapshot> = radio.snapshots(intervalMs)

    @SuppressLint("MissingPermission")
    override fun locationFixes(): Flow<GeoFix> = locationFixes(2000L)

    @SuppressLint("MissingPermission")
    override fun locationFixes(intervalMs: Long): Flow<GeoFix> = callbackFlow {
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
            providers.forEach { lm.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) }
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
                    // The field model changes over kilometres, so recompute only when the fix changes.
                    if (f !== declinationFix) {
                        declinationFix = f
                        declination = GeomagneticField(f.lat.toFloat(), f.lon.toFloat(), (f.altitudeM ?: 0.0).toFloat(), System.currentTimeMillis()).declination
                    }
                    deg += declination ?: 0f
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

    override val appVersion: String? by lazy {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
    }

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

    /**
     * History lives in one of two private files. history.tsv is excluded from backups; history-backup.tsv is
     * included in Android's automatic Google backup, so it comes back on reinstall or on a new phone. The
     * person's choice is where the file is (not a setting), so it survives a restore with the data.
     */
    override val historyFile: io.towerscope.data.HistoryFile = object : io.towerscope.data.HistoryFile {
        private val local = java.io.File(app.filesDir, "history.tsv")
        private val backup = java.io.File(app.filesDir, "history-backup.tsv")
        private val f get() = if (backup.exists()) backup else local

        override fun readAll(): String? = runCatching { f.takeIf { it.exists() }?.readText() }.getOrNull()
        override fun append(text: String) { runCatching { f.appendText(text) } }
        override fun replace(text: String) { runCatching { atomicWrite(f, text) } }
        override fun clear() { runCatching { local.delete(); backup.delete() } }

        override val backupSupported: Boolean get() = true
        override val backedUp: Boolean get() = backup.exists()
        override fun setBackedUp(on: Boolean) {
            runCatching {
                val from = if (on) local else backup
                val to = if (on) backup else local
                if (from.exists()) { if (!from.renameTo(to)) { to.writeText(from.readText()); from.delete() } }
                else if (on) to.createNewFile()
            }
        }

        private fun atomicWrite(target: java.io.File, text: String) {
            val tmp = java.io.File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) { target.writeText(text); tmp.delete() }
        }
    }

    /** Set at startup by the open-source edition, which can record with the app closed. */
    override var backgroundRecorder: BackgroundRecorder? = null

    /** Set at startup by the Play edition. */
    override var license: io.towerscope.license.LicenseManager? = null

    /**
     * Writes the file to the app's cache and opens the share sheet. Needs the FileProvider that only the
     * open-source edition declares (authority "<applicationId>.export"); elsewhere this returns false.
     */
    override fun shareFile(fileName: String, mimeType: String, content: String): Boolean = runCatching {
        val dir = java.io.File(app.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the latest export
        val file = java.io.File(dir, fileName).apply { writeText(content) }
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.export", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "TowerScope Live history")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        app.startActivity(Intent.createChooser(send, "Export history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.isSuccess

    override fun localHour(epochMs: Long): Int =
        java.util.Calendar.getInstance().apply { timeInMillis = epochMs }.get(java.util.Calendar.HOUR_OF_DAY)

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }
}
