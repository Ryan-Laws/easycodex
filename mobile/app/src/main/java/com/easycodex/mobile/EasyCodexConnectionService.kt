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
const val ACTION_PRESET_REPLY = "com.easycodex.mobile.action.PRESET_REPLY"
const val ACTION_APPROVE_REQUEST = "com.easycodex.mobile.action.APPROVE_REQUEST"
const val ACTION_DENY_REQUEST = "com.easycodex.mobile.action.DENY_REQUEST"
const val ACTION_DISMISS_AGENT_NOTIFICATION = "com.easycodex.mobile.action.DISMISS_AGENT_NOTIFICATION"
const val EXTRA_QUICK_REPLY_AGENT_ID = "agentId"
const val EXTRA_QUICK_REPLY_NOTIFICATION_ID = "notificationId"
const val EXTRA_PRESET_REPLY_TEXT = "presetReplyText"
const val EXTRA_APPROVAL_REQUEST_ID = "approvalRequestId"
const val KEY_QUICK_REPLY_TEXT = "easycodex_quick_reply"

enum class AgentNotificationActionKind {
    QuickReply,
    PresetReply,
    Approval,
    Dismiss,
}

data class AgentNotificationActionSpec(
    val kind: AgentNotificationActionKind,
    val title: String,
    val presetText: String = "",
    val approved: Boolean = false,
)

fun agentNotificationActionSpecs(
    kind: AgentAlertKind,
    canApprove: Boolean = false,
    strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE),
): List<AgentNotificationActionSpec> {
    return when (kind) {
        AgentAlertKind.Confirmation -> if (canApprove) {
            listOf(
                AgentNotificationActionSpec(AgentNotificationActionKind.Approval, strings.notificationApprove, approved = true),
                AgentNotificationActionSpec(AgentNotificationActionKind.Approval, strings.notificationDeny, approved = false),
                AgentNotificationActionSpec(AgentNotificationActionKind.Dismiss, strings.later),
            )
        } else {
            listOf(
                AgentNotificationActionSpec(AgentNotificationActionKind.QuickReply, strings.notificationReply),
                AgentNotificationActionSpec(AgentNotificationActionKind.Dismiss, strings.later),
            )
        }
        AgentAlertKind.Completed -> listOf(
            AgentNotificationActionSpec(AgentNotificationActionKind.PresetReply, strings.notificationContinue, strings.notificationContinuePrompt),
            AgentNotificationActionSpec(AgentNotificationActionKind.QuickReply, strings.notificationFollowUp),
            AgentNotificationActionSpec(AgentNotificationActionKind.Dismiss, strings.later),
        )
        AgentAlertKind.Question -> listOf(
            AgentNotificationActionSpec(AgentNotificationActionKind.QuickReply, strings.notificationAnswer),
            AgentNotificationActionSpec(AgentNotificationActionKind.Dismiss, strings.later),
        )
        AgentAlertKind.Error -> listOf(
            AgentNotificationActionSpec(AgentNotificationActionKind.PresetReply, strings.notificationAnalyzeError, strings.notificationAnalyzeErrorPrompt),
            AgentNotificationActionSpec(AgentNotificationActionKind.Dismiss, strings.later),
        )
    }
}

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
        when (intent?.action) {
            ACTION_QUICK_REPLY -> {
                handleQuickReply(intent)
                updateForegroundNotificationIfChanged(force = true)
                return START_STICKY
            }
            ACTION_PRESET_REPLY -> {
                handlePresetReply(intent)
                updateForegroundNotificationIfChanged(force = true)
                return START_STICKY
            }
            ACTION_APPROVE_REQUEST, ACTION_DENY_REQUEST -> {
                handleApprovalAction(intent, approved = intent.action == ACTION_APPROVE_REQUEST)
                updateForegroundNotificationIfChanged(force = true)
                return START_STICKY
            }
            ACTION_DISMISS_AGENT_NOTIFICATION -> {
                dismissNotification(intent)
                return START_STICKY
            }
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
        val strings = appStringsFor(controller.appLanguage)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BACKGROUND_CONNECTION_CHANNEL_ID,
                    strings.backgroundConnectionChannel,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = strings.backgroundConnectionChannelDescription
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
            builder.addAction(buildQuickReplyAction(agentId, BACKGROUND_CONNECTION_NOTIFICATION_ID, strings.notificationQuickInput))
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
            .setLabel(appStringsFor(controller.appLanguage).notificationQuickInputLabel)
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

    private fun handlePresetReply(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_PRESET_REPLY_TEXT).orEmpty().trim()
        val agentId = intent.getStringExtra(EXTRA_QUICK_REPLY_AGENT_ID).orEmpty()
        if (text.isBlank() || agentId.isBlank()) return
        controller.sendQuickReply(agentId, text)
        dismissNotification(intent)
    }

    private fun handleApprovalAction(intent: Intent, approved: Boolean) {
        val agentId = intent.getStringExtra(EXTRA_QUICK_REPLY_AGENT_ID).orEmpty()
        if (agentId.isBlank()) return
        val requestId = intent.getStringExtra(EXTRA_APPROVAL_REQUEST_ID).orEmpty()
        controller.respondApprovalForAgentRequest(agentId, requestId, approved)
        dismissNotification(intent)
    }

    private fun dismissNotification(intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, 0)
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
    title: String = appStringsFor(DEFAULT_APP_LANGUAGE).notificationReply,
    inputLabel: String = appStringsFor(DEFAULT_APP_LANGUAGE).notificationQuickInputLabel,
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
        .setLabel(inputLabel)
        .build()
    val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
    return Notification.Action.Builder(Icon.createWithResource(context, smallIcon), title, replyPendingIntent)
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(false)
        .build()
}

fun presetReplyAction(
    context: Context,
    agentId: String,
    notificationId: Int,
    title: String,
    text: String,
): Notification.Action {
    val intent = Intent(context, EasyCodexConnectionService::class.java).apply {
        action = ACTION_PRESET_REPLY
        putExtra(EXTRA_QUICK_REPLY_AGENT_ID, agentId)
        putExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, notificationId)
        putExtra(EXTRA_PRESET_REPLY_TEXT, text)
    }
    val pendingIntent = PendingIntent.getService(
        context,
        EasyCodexConnectionService.quickReplyRequestCode("$agentId:$title", notificationId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
    return Notification.Action.Builder(Icon.createWithResource(context, smallIcon), title, pendingIntent).build()
}

fun approvalAction(
    context: Context,
    agentId: String,
    requestId: String,
    notificationId: Int,
    title: String,
    approved: Boolean,
): Notification.Action {
    val intent = Intent(context, EasyCodexConnectionService::class.java).apply {
        action = if (approved) ACTION_APPROVE_REQUEST else ACTION_DENY_REQUEST
        putExtra(EXTRA_QUICK_REPLY_AGENT_ID, agentId)
        putExtra(EXTRA_APPROVAL_REQUEST_ID, requestId)
        putExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, notificationId)
    }
    val pendingIntent = PendingIntent.getService(
        context,
        EasyCodexConnectionService.quickReplyRequestCode("$agentId:$title", notificationId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
    return Notification.Action.Builder(Icon.createWithResource(context, smallIcon), title, pendingIntent).build()
}

fun dismissAgentNotificationAction(
    context: Context,
    agentId: String,
    notificationId: Int,
    title: String = appStringsFor(DEFAULT_APP_LANGUAGE).later,
): Notification.Action {
    val intent = Intent(context, EasyCodexConnectionService::class.java).apply {
        action = ACTION_DISMISS_AGENT_NOTIFICATION
        putExtra(EXTRA_QUICK_REPLY_AGENT_ID, agentId)
        putExtra(EXTRA_QUICK_REPLY_NOTIFICATION_ID, notificationId)
    }
    val pendingIntent = PendingIntent.getService(
        context,
        EasyCodexConnectionService.quickReplyRequestCode("$agentId:dismiss", notificationId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
    return Notification.Action.Builder(Icon.createWithResource(context, smallIcon), title, pendingIntent).build()
}
