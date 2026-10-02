package com.shilapi.xcertplay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.shilapi.xcertplay.host.R

/** Keeps an explicitly started connection alive when another car app is in the foreground. */
class AndroidPlaySessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CarPlayBackgroundSession.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!CarPlayBackgroundSession.hasSession()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MEDIA) {
            val code = intent.getIntExtra("media", 0)
            if (code in listOf(1, 2, 4, 5)) CarPlayBackgroundSession.snapshot()?.controller?.sendMedia(code)
        }
        running = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AndroidPlayLanguage.text(this, "CarPlay连接与播放"), NotificationManager.IMPORTANCE_LOW))
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (Build.VERSION.SDK_INT >= 30 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(1, notification, types)
        } else startForeground(1, notification)
        return START_NOT_STICKY
    }
    override fun onDestroy() { running = false; super.onDestroy() }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // BYD's recents force-stops the package ~10 ms after removing the task: end guidance first.
        CarPlayBackgroundSession.stop()
        stopSelf()
    }
    companion object {
        @Volatile private var running = false
        const val ACTION_MEDIA = "com.androidplay.app.MEDIA"
        fun refreshNotification(context: android.content.Context) {
            if (running) context.getSystemService(NotificationManager::class.java).notify(1, buildNotification(context))
        }
        private fun buildNotification(context: android.content.Context): Notification {
            val open = PendingIntent.getActivity(context, 0, Intent(context, CarPlayHostActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(context, 1, Intent(context, AndroidPlaySessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_androidplay_notification)
                .setContentTitle("AndroidPlay").setContentText(AndroidPlayLanguage.text(context, "CarPlay连接服务正在运行"))
                .setContentIntent(open).setOngoing(true)
            CarPlayBackgroundSession.decorateNotification(builder)
            return builder.addAction(Notification.Action.Builder(null, AndroidPlayLanguage.text(context, "断开连接"), stop).build()).build()
        }
        const val ACTION_STOP = "com.androidplay.app.DISCONNECT"
        private const val CHANNEL = "androidplay_connection"
    }
}
