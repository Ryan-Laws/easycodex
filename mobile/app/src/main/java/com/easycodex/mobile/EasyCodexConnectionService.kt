package com.easycodex.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.app.RemoteInput

private const val BACKGROUND_CONNECTION_CHANNEL_ID = "easycodex-background-connection"
private const val BACKGROUND_CONNECTION_NOTIFICATION_ID = 72002
private const val BACKGROUND_CONNECTION_UPDATE_MS = 60_000L
const val ACTION_QUICK_REPLY = "com.easycodex.mobile.action.QUICK_REPLY"
const val EXTRA_QUICK_REPLY_AGENT_ID = "agentId"
const val EXTRA_QUICK_REPLY_NOTIFICATION_ID = "notificationId"
const val KEY_QUICK_REPLY_TEXT = "easycodex_quick_reply"

class EasyCodexConnectionService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var controller: EasyCodexController
    private var lastNotificationText = ""
    private val connectionStateListener = object : EasyCodexController.ConnectionStateListener {
        override fun onConnectionStateChanged(status: String, text: String) {
            main.post { updateForegroundNotificationIfChanged(force = true) }
        }
    }
    private val notificationUpdater = object : Runnable {
        override fun run() {
            updateForegroundNotificationIfChanged(force = false)
            main.postDelayed(this, BACKGROUND_CONNECTION_UPDATE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = EasyCodexControllerProvider.get(applicationContext)
        lastNotificationText = foregroundNotificationText()
        startForegroundCompat(buildNotification(lastNotificationText))
        controller.addConnectionStateListener(connectionStateListener)
        main.postDelayed(notificationUpdater, BACKGROUND_CONNECTION_UPDATE_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.reloadSettings()
        if (intent?.action == ACTION_QUICK_REPLY) {
            handleQuickReply(intent)
            updateForegroundNotificationIfChanged(force = true)
            return START_STICKY
        }
        if (controller.connectionStatus != "connected" && controller.connectionStatus != "connecting") {
            controller.connect()
        }
        updateForegroundNotificationIfChanged(force = true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controller.removeConnectionStateListener(connectionStateListener)
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

    private fun updateForegroundNotificationIfChanged(force: Boolean) {
        val nextText = foregroundNotificationText()
        if (!force && nextText == lastNotificationText) return
        lastNotificationText = nextText
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(BACKGROUND_CONNECTION_NOTIFICATION_ID, buildNotification(nextText))
    }

    private fun foregroundNotificationText(): String {
        val strings = appStringsFor(controller.appLanguage)
        return controller.statusText.ifBlank { strings.connectingRelay }
    }

    private fun buildNotification(contentText: String): Notification {
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
        val smallIcon = if (applicationInfo.icon != 0) applicationInfo.icon else R.mipmap.ic_launcher
        val builder = Notification.Builder(this, BACKGROUND_CONNECTION_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("EasyCodex")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
        controller.activeAgentId?.takeIf { it.isNotBlank() }?.let { agentId ->
            builder.addAction(buildQuickReplyAction(agentId, BACKGROUND_CONNECTION_NOTIFICATION_ID, "快速输入"))
        }
        return builder.build()
    }

    private fun buildQuickReplyAction(agentId: String, notificationId: Int, title: String): Notification.Action {
        val replyIntent = Intent(this, EasyCodexConnectionService::class.java).apply {
            action = ACTION_QUICK_REPLY
            putExtra(EXTRA_QUICK_REPLY_AGENT_ID, agentId)
            putExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getService(
            this,
            quickReplyRequestCode(agentId, notificationId),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag(),
        )
        val remoteInput = RemoteInput.Builder(KEY_QUICK_REPLY_TEXT)
            .setLabel("输入要发送给 Codex 的内容")
            .build()
        val smallIcon = if (applicationInfo.icon != 0) applicationInfo.icon else R.mipmap.ic_launcher
        return Notification.Action.Builder(Icon.createWithResource(this, smallIcon), title, replyPendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun handleQuickReply(intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_QUICK_REPLY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        val agentId = intent.getStringExtra(EXTRA_QUICK_REPLY_AGENT_ID).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, 0)
        if (text.isBlank() || agentId.isBlank()) return
        controller.sendQuickReply(agentId, text)
        if (notificationId > 0 && notificationId != BACKGROUND_CONNECTION_NOTIFICATION_ID) {
            getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }
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

        fun quickReplyRequestCode(agentId: String, notificationId: Int): Int {
            return notificationId xor agentId.hashCode()
        }
    }
}

fun mutablePendingIntentFlag(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}

fun quickReplyAction(
    context: Context,
    agentId: String,
    notificationId: Int,
    title: String = "回复",
): Notification.Action {
    val replyIntent = Intent(context, EasyCodexConnectionService::class.java).apply {
        action = ACTION_QUICK_REPLY
        putExtra(EXTRA_QUICK_REPLY_AGENT_ID, agentId)
        putExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, notificationId)
    }
    val replyPendingIntent = PendingIntent.getService(
        context,
        EasyCodexConnectionService.quickReplyRequestCode(agentId, notificationId),
        replyIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag(),
    )
    val remoteInput = RemoteInput.Builder(KEY_QUICK_REPLY_TEXT)
        .setLabel("输入要发送给 Codex 的内容")
        .build()
    val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
    return Notification.Action.Builder(Icon.createWithResource(context, smallIcon), title, replyPendingIntent)
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(false)
        .build()
}
