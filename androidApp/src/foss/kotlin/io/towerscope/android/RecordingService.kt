package io.towerscope.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps TowerScope's history recording going with the app closed (open-source edition only, opt-in).
 *
 * It runs as a location foreground service with a notification for as long as it runs. It is only started
 * from the app's own screen, so Android lets it keep the app's while-in-use location access; it never asks
 * for "Allow all the time". It is not restarted by the system if it's killed (START_NOT_STICKY), and it stops
 * when the person taps Stop in the notification or turns the setting off.
 */
class RecordingService : Service() {
    private val app get() = application as TowerScopeApplication

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.controller.setBackgroundRecording(false) // also stops this service
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }.isSuccess
        if (!started) {
            // E.g. location permission was revoked. Don't leave the setting claiming it's on.
            app.controller.setBackgroundRecording(false)
            stopSelf()
            return START_NOT_STICKY
        }
        app.controller.onBackgroundRecordingStarted()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        app.controller.onBackgroundRecordingStopped()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Background recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while TowerScope records signal history with the app closed"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("TowerScope is recording signal history")
            .setContentText("About one reading a minute, kept on this phone. Tap Stop to end.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 7
        const val ACTION_STOP = "io.towerscope.android.STOP_RECORDING"
    }
}
