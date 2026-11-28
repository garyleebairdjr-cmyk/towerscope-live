package io.towerscope.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.towerscope.platform.BackgroundRecorder

/** Open-source edition: everything free, plus opt-in recording with the app closed. */
object Edition : EditionHooks {
    override fun install(app: TowerScopeApplication) {
        app.platform.backgroundRecorder = ServiceRecorder(app)
    }

    override fun onCreate(activity: ComponentActivity) {
        val recorder = (activity.application as TowerScopeApplication).platform.backgroundRecorder as? ServiceRecorder ?: return
        // Android 13+ hides a foreground service's notification (and its Stop button) without this permission,
        // so ask right before the first start. Recording starts either way.
        val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { recorder.startService() }
        recorder.notificationAsker = launcher
    }
}

/** Starts and stops [RecordingService]. Only ever started from the app's own screen, by the person. */
class ServiceRecorder(private val context: Context) : BackgroundRecorder {
    var notificationAsker: ActivityResultLauncher<String>? = null
    /** Ask at most once per run, so returning to the app never re-opens the dialog. */
    private var asked = false

    override fun start() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val asker = notificationAsker
        if (needsAsk && asker != null && !asked) {
            asked = true
            asker.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startService()
    }

    fun startService() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)) }
    }

    override fun stop() {
        context.stopService(Intent(context, RecordingService::class.java))
    }
}
