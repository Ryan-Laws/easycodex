package com.easycodex.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

private const val BACKGROUND_CONNECTION_CHANNEL_ID = "easycodex-background-connection"
private const val BACKGROUND_CONNECTION_NOTIFICATION_ID = 72002
private const val BACKGROUND_CONNECTION_UPDATE_MS = 5_000L

class EasyCodexConnectionService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var controller: EasyCodexController
    private val notificationUpdater = object : Runnable {
        override fun run() {
            updateForegroundNotification()
            main.postDelayed(this, BACKGROUND_CONNECTION_UPDATE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = EasyCodexControllerProvider.get(applicationContext)
        startForegroundCompat(buildNotification())
        main.post(notificationUpdater)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.reloadSettings()
        if (controller.connectionStatus != "connected" && controller.connectionStatus != "connecting") {
            controller.connect()
        }
        updateForegroundNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        main.removeCallbacks(notificationUpdater)
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BACKGROUND_CONNECTION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(BACKGROUND_CONNECTION_NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(BACKGROUND_CONNECTION_NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BACKGROUND_CONNECTION_CHANNEL_ID,
                    "EasyCodex 后台连接",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "保持 EasyCodex 与本地中继的后台连接"
                },
            )
        }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            BACKGROUND_CONNECTION_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val strings = appStringsFor(controller.appLanguage)
        val smallIcon = if (applicationInfo.icon != 0) applicationInfo.icon else R.mipmap.ic_launcher
        return Notification.Builder(this, BACKGROUND_CONNECTION_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("EasyCodex")
            .setContentText(controller.statusText.ifBlank { strings.connectingRelay })
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, EasyCodexConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }
}
