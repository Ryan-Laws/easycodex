package com.easycodex.mobile

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AgentMessage(
    val role: String,
    val type: String,
    val text: String,
    val timestamp: Long,
    val itemId: String? = null,
    val streaming: Boolean = false,
)

data class Agent(
    val id: String,
    val name: String,
    val model: String,
    val cwd: String,
    val projectRoot: String? = null,
    val status: String,
    val serviceTier: String = DEFAULT_SERVICE_TIER,
    val reasoningEffort: String = DEFAULT_REASONING_EFFORT,
    val activity: String? = null,
    val messages: List<AgentMessage> = emptyList(),
    val codexThreadId: String? = null,
    val preview: String? = null,
    val resumable: Boolean = false,
    val updatedAt: Long = 0,
)

enum class AgentAlertKind {
    Completed,
    Question,
    Confirmation,
    Error,
}

data class AgentAlert(
    val id: String,
    val agentId: String,
    val agentName: String,
    val kind: AgentAlertKind,
    val title: String,
    val detail: String,
    val timestamp: Long,
)

data class AgentApprovalRequest(
    val id: String,
    val agentId: String,
    val method: String,
    val title: String,
    val detail: String,
    val timestamp: Long,
)

data class PlanReview(
    val agentId: String,
    val message: AgentMessage,
)

data class RuntimeCapabilities(
    val providerMode: String = "official",
    val supportsServiceTier: Boolean = true,
    val supportsReasoningEffort: Boolean = true,
    val reason: String = "official Codex runtime",
)

data class CodexModelOption(
    val model: String,
    val displayName: String,
    val defaultReasoningEffort: String = DEFAULT_REASONING_EFFORT,
    val supportedReasoningEfforts: List<String> = emptyList(),
    val additionalSpeedTiers: List<String> = emptyList(),
    val isDefault: Boolean = false,
)

data class DirectoryOption(
    val name: String,
    val path: String,
)

data class WorktreeOption(
    val name: String,
    val path: String,
    val branch: String? = null,
    val current: Boolean = false,
    val locked: Boolean = false,
)

data class DirectoryListing(
    val path: String,
    val parent: String?,
    val roots: List<DirectoryOption>,
    val worktrees: List<WorktreeOption> = emptyList(),
    val entries: List<DirectoryOption>,
)

private const val EMOJI_COLUMNS = 8
private const val EMOJI_ROWS = 5
private const val EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS
private const val AGENTS_REFRESH_DEBOUNCE_MS = 500L
private const val AGENT_ACTIVITY_UPDATE_THROTTLE_MS = 500L
private const val STREAM_DELTA_FLUSH_MS = 48L
private const val CODEX_THREAD_DETAIL_PREFETCH_LIMIT = 10
private const val CODEX_THREAD_DETAIL_MAX_RETRIES = 3
private const val CODEX_THREAD_DETAIL_RETRY_BASE_MS = 1_200L
private const val CODEX_DETAIL_LOADING_LABEL = "正在加载具体细节"
private const val CODEX_DETAIL_RETRY_LABEL = "正在重新同步任务详情"
private const val AGENT_ALERT_CHANNEL_ID = "easycodex-agent-alerts"
private const val AGENT_ALERT_NOTIFICATION_BASE_ID = 73000
private const val RELAY_REQUEST_TIMEOUT_MS = 30_000L
private const val PLAN_MODE_PROMPT = """
请先进入计划模式处理下面的需求。

要求：
1. 先优化并整理一个可执行计划。
2. 计划可以很详细，包含关键步骤、风险点、验证方式和需要确认的问题。
3. 不要开始执行、不要修改文件、不要运行命令。
4. 计划最后请明确询问我是否要开始这个计划。

需求：
"""
private const val PLAN_OPTIMIZE_PROMPT = "请优化上一条计划，让步骤更清晰、风险更完整、执行顺序更可靠。仍然不要开始执行，最后继续询问我是否要开始这个计划。"
private const val PLAN_START_PROMPT = "开始执行上一条计划。"

private data class PendingStreamDelta(
    val agentId: String,
    val itemId: String,
    var type: String,
    val text: StringBuilder = StringBuilder(),
)

private val COMMON_EMOJI = listOf(
    "😀", "😄", "😂", "🤣", "😊", "😍", "😘", "😎",
    "🤔", "😮", "😢", "😭", "😤", "😴", "👍", "👎",
    "🙏", "👏", "💪", "👌", "🙌", "✅", "❌", "🔥",
    "❤️", "🎉", "🚀", "👀", "💡", "⚠️", "⭐", "✨",
    "😁", "😆", "😅", "🙂", "🙃", "😉", "😋", "🤩",
    "🥰", "😗", "😙", "😚", "🤗", "🤭", "🫢", "🫡",
    "😐", "😑", "😶", "🙄", "😏", "😒", "😬", "😌",
    "😔", "😪", "🤤", "😷", "🤒", "🤕", "🤧", "🥵",
    "🥶", "🥳", "😵", "🤯", "😱", "😨", "😰", "😡",
    "🤬", "😈", "👿", "💀", "☠️", "👻", "🤖", "💩",
    "👋", "🤚", "🖐️", "✋", "🖖", "🤞", "🫰", "🤟",
    "🤘", "👈", "👉", "👆", "👇", "☝️", "✊", "👊",
    "🤛", "🤜", "🫶", "💅", "✍️", "🤝", "🫂", "💬",
    "💭", "💯", "💢", "💥", "💫", "💦", "💨", "🕳️",
    "💤", "💗", "💓", "💕", "💖", "💘", "💝", "💔",
    "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍",
    "🍎", "🍌", "🍉", "🍓", "🍕", "🍔", "🍟", "🍜",
    "☕", "🍺", "🍻", "🍷", "⚽", "🏀", "🏈", "🎮",
    "🎧", "🎬", "🎯", "🏆", "🚗", "✈️", "🏠", "🌍",
    "☀️", "🌙", "☁️", "⛈️", "🌈", "🌊", "🌸", "🌲",
    "🐶", "🐱", "🐭", "🐼", "🦊", "🦁", "🐵", "🐧",
    "⌛", "⏰", "📌", "📎", "📷", "🔒", "🔑", "🔧",
    "🧰", "🛠️", "📦", "📁", "📄", "📝", "🔍", "🔔",
    "🔕", "📣", "📍", "📈", "📉", "🔗", "🧪", "🧠",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val importedConnection = applyEasyCodexConnectionUri(this, intent?.data)
        setContent {
            EasyCodexApp(importedConnection = importedConnection)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyEasyCodexConnectionUri(this, intent.data)) {
            recreate()
        }
    }
}

private fun buildSystemVoiceInputIntent(context: Context): Intent {
    val baseIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出要发送给 EasyCodex 的内容")
    }
    val preferredActivity = context.packageManager
        .queryIntentActivities(baseIntent, 0)
        .filter { it.activityInfo?.packageName?.isNotBlank() == true }
        .minByOrNull { voiceRecognizerPriority(it.activityInfo.packageName) }

    return if (preferredActivity != null) {
        Intent(baseIntent).apply {
            component = ComponentName(preferredActivity.activityInfo.packageName, preferredActivity.activityInfo.name)
        }
    } else {
        baseIntent
    }
}

private fun voiceRecognizerPriority(packageName: String): Int {
    val normalized = packageName.lowercase(Locale.ROOT)
    return when {
        normalized.startsWith("com.google.") -> 20
        normalized.contains("googlequicksearchbox") -> 20
        else -> 0
    }
}

class EasyCodexController(private val context: android.content.Context) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, (JSONObject?, String?) -> Unit>()
    private var webSocket: WebSocket? = null
    private var requestCounter = 0
    private var connectionGeneration = 0
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var manuallyDisconnected = false
    private var agentsRefreshInFlight = false
    private var agentsRefreshQueued = false
    private var agentsRefreshRunnable: Runnable? = null
    private val prefs = context.getSharedPreferences("easycodex", android.content.Context.MODE_PRIVATE)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_APP_LANGUAGE) {
            val nextLanguage = prefs.getString(PREF_APP_LANGUAGE, DEFAULT_APP_LANGUAGE)?.ifBlank { DEFAULT_APP_LANGUAGE }
                ?: DEFAULT_APP_LANGUAGE
            if (nextLanguage != appLanguage) {
                appLanguage = nextLanguage
                if (connectionStatus == "connected") syncClientLanguage()
            }
        }
    }
    private var lastStreamSessionId = prefs.getString("last_stream_session_id", "") ?: ""
    private var lastStreamSeq = prefs.getLong("last_stream_seq", 0L)
    private val streamingAgentIds = mutableSetOf<String>()
    private val lastActivityUpdateAt = ConcurrentHashMap<String, Long>()
    private val pendingStreamDeltas = linkedMapOf<String, PendingStreamDelta>()
    private var streamDeltaFlushRunnable: Runnable? = null
    private var threadDetailRequestCounter = 0
    private val latestThreadDetailRequests = ConcurrentHashMap<String, Int>()
    private val detailedCodexThreads = ConcurrentHashMap<String, Agent>()
    private val threadDetailsInFlight = mutableSetOf<String>()
    private val threadDetailRetryCounts = ConcurrentHashMap<String, Int>()
    private val threadDetailRetryRunnables = ConcurrentHashMap<String, Runnable>()

    var relayUrl by mutableStateOf(prefs.getString(PREF_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL)
    var apiKey by mutableStateOf(prefs.getString(PREF_API_KEY, "") ?: "")
    var defaultModel by mutableStateOf(prefs.getString(PREF_DEFAULT_MODEL, DEFAULT_AGENT_MODEL) ?: DEFAULT_AGENT_MODEL)
    var defaultCwd by mutableStateOf(prefs.getString(PREF_DEFAULT_CWD, DEFAULT_AGENT_CWD) ?: DEFAULT_AGENT_CWD)
    var defaultReasoningEffort by mutableStateOf(
        prefs.getString(PREF_DEFAULT_REASONING_EFFORT, DEFAULT_REASONING_EFFORT) ?: DEFAULT_REASONING_EFFORT,
    )
    var defaultServiceTier by mutableStateOf(
        normalizeServiceTier(prefs.getString(PREF_DEFAULT_SERVICE_TIER, DEFAULT_SERVICE_TIER) ?: DEFAULT_SERVICE_TIER),
    )
    var themeMode by mutableStateOf(prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE)
    var themeColor by mutableStateOf(prefs.getString(PREF_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR)
    var appLayout by mutableStateOf(prefs.getString(PREF_APP_LAYOUT, DEFAULT_APP_LAYOUT) ?: DEFAULT_APP_LAYOUT)
    var appLanguage by mutableStateOf(prefs.getString(PREF_APP_LANGUAGE, DEFAULT_APP_LANGUAGE) ?: DEFAULT_APP_LANGUAGE)
    var oledMode by mutableStateOf(prefs.getBoolean(PREF_OLED_MODE, false))
    var connectionStatus by mutableStateOf("disconnected")
    var statusText by mutableStateOf(appStringsFor(appLanguage).disconnected)
    var activeAgentId by mutableStateOf<String?>(null)
    var draftProjectCwd by mutableStateOf<String?>(defaultCwd.ifBlank { DEFAULT_AGENT_CWD })
    var draftProjectLocked by mutableStateOf(false)
    var draftModel by mutableStateOf(defaultModel.ifBlank { DEFAULT_AGENT_MODEL })
    var draftReasoningEffort by mutableStateOf(defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT })
    var draftServiceTier by mutableStateOf(defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER })
    var inputText by mutableStateOf("")
    var isBusy by mutableStateOf(false)
    var runtimeCapabilities by mutableStateOf(RuntimeCapabilities())
    val agents = mutableStateListOf<Agent>()
    val codexModels = mutableStateListOf<CodexModelOption>()
    val alerts = mutableStateListOf<AgentAlert>()
    val approvalRequests = mutableStateListOf<AgentApprovalRequest>()
    var agentsRevision by mutableStateOf(0)
        private set
    var planReview by mutableStateOf<PlanReview?>(null)
        private set
    private var lastPromptedPlanKey: String? = null
    private var projectOptionsCacheRevision = -1
    private var projectOptionsCacheDraftCwd: String? = null
    private var projectOptionsCacheDefaultCwd = ""
    private var projectOptionsCache = emptyList<String>()

    private val strings: AppStrings
        get() = appStringsFor(appLanguage)

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val activeAgent: Agent?
        get() = activeAgentId?.let { selectedId -> agents.firstOrNull { it.id == selectedId } }

    val draftAgent: Agent?
        get() {
            val cwd = draftProjectCwd ?: return null
            return Agent(
                id = "draft_project_task",
                name = if (draftProjectLocked) projectNameFromCwd(cwd) else strings.homeSubtitle,
                model = draftModel.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
                cwd = cwd,
                projectRoot = cwd,
                status = strings.homeSubtitle,
                serviceTier = normalizeServiceTier(draftServiceTier.ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } }),
                reasoningEffort = draftReasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } },
            )
        }

    fun projectOptions(): List<String> {
        if (
            projectOptionsCacheRevision == agentsRevision &&
            projectOptionsCacheDraftCwd == draftProjectCwd &&
            projectOptionsCacheDefaultCwd == defaultCwd
        ) {
            return projectOptionsCache
        }
        val paths = mutableListOf<String>()
        cleanNullablePath(draftProjectCwd)?.let { paths.add(it) }
        cleanNullablePath(defaultCwd)?.let { paths.add(it) }
        agents.sortedByDescending { it.updatedAt }.forEach { agent ->
            cleanNullablePath(agent.projectRoot ?: agent.cwd)?.let { paths.add(it) }
        }
        projectOptionsCache = paths.distinctBy { normalizePathKey(it) }
        projectOptionsCacheRevision = agentsRevision
        projectOptionsCacheDraftCwd = draftProjectCwd
        projectOptionsCacheDefaultCwd = defaultCwd
        return projectOptionsCache
    }

    fun browseDirectories(path: String?, callback: (DirectoryListing?, String?) -> Unit) {
        send(
            "browse_directories",
            mapOf("path" to path.orEmpty()),
        ) { data, error ->
            if (error != null) {
                statusText = "${strings.directoryUnreadable}: $error"
                callback(null, error)
                return@send
            }
            val payload = data?.optJSONObject("data") ?: JSONObject()
            callback(parseDirectoryListing(payload), null)
        }
    }

    fun reloadSettings() {
        val nextRelayUrl = prefs.getString(PREF_RELAY_URL, DEFAULT_RELAY_URL)?.trim().orEmpty()
            .ifBlank { DEFAULT_RELAY_URL }
        val nextApiKey = prefs.getString(PREF_API_KEY, "")?.trim().orEmpty()
        val shouldReconnect = nextRelayUrl != relayUrl || nextApiKey != apiKey
        val previousLanguage = appLanguage
        relayUrl = nextRelayUrl
        apiKey = nextApiKey
        defaultModel = prefs.getString(PREF_DEFAULT_MODEL, DEFAULT_AGENT_MODEL)?.ifBlank { DEFAULT_AGENT_MODEL }
            ?: DEFAULT_AGENT_MODEL
        defaultCwd = prefs.getString(PREF_DEFAULT_CWD, DEFAULT_AGENT_CWD)?.trim().orEmpty()
            .ifBlank { DEFAULT_AGENT_CWD }
        if (activeAgentId == null && draftProjectCwd.isNullOrBlank()) {
            draftProjectCwd = defaultCwd
        }
        defaultReasoningEffort = prefs.getString(PREF_DEFAULT_REASONING_EFFORT, DEFAULT_REASONING_EFFORT)
            ?.ifBlank { DEFAULT_REASONING_EFFORT }
            ?: DEFAULT_REASONING_EFFORT
        defaultServiceTier = normalizeServiceTier(
            prefs.getString(PREF_DEFAULT_SERVICE_TIER, DEFAULT_SERVICE_TIER)
                ?.ifBlank { DEFAULT_SERVICE_TIER }
                ?: DEFAULT_SERVICE_TIER,
        )
        themeMode = prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE)?.ifBlank { DEFAULT_THEME_MODE }
            ?: DEFAULT_THEME_MODE
        themeColor = prefs.getString(PREF_THEME_COLOR, DEFAULT_THEME_COLOR)?.ifBlank { DEFAULT_THEME_COLOR }
            ?: DEFAULT_THEME_COLOR
        appLayout = prefs.getString(PREF_APP_LAYOUT, DEFAULT_APP_LAYOUT)?.ifBlank { DEFAULT_APP_LAYOUT }
            ?: DEFAULT_APP_LAYOUT
        appLanguage = prefs.getString(PREF_APP_LANGUAGE, DEFAULT_APP_LANGUAGE)?.ifBlank { DEFAULT_APP_LANGUAGE }
            ?: DEFAULT_APP_LANGUAGE
        oledMode = prefs.getBoolean(PREF_OLED_MODE, false)
        if (shouldReconnect) connect()
        else if (previousLanguage != appLanguage && connectionStatus == "connected") syncClientLanguage()
    }

    fun connect() {
        manuallyDisconnected = false
        cancelReconnect()
        cancelAgentsRefresh()
        streamingAgentIds.clear()
        webSocket?.close(1000, "Reconnect")
        val generation = ++connectionGeneration
        if (relayUrl.isBlank() || apiKey.isBlank()) {
            connectionStatus = "disconnected"
            statusText = strings.missingConnection
            return
        }

        connectionStatus = "connecting"
        statusText = strings.connectingRelay
        val request = try {
            Request.Builder().url(relayUrl).build()
        } catch (error: IllegalArgumentException) {
            connectionStatus = "disconnected"
            statusText = strings.invalidRelayUrl
            return
        }
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendRaw("auth", mapOf("key" to apiKey, "clientId" to clientId())) { _, error ->
                    if (generation != connectionGeneration) return@sendRaw
                    if (error == null) {
                        reconnectAttempts = 0
                        connectionStatus = "connected"
                        statusText = strings.connected
                        syncClientLanguage()
                        refreshRuntimeOptions()
                        refreshAgents()
                        replayMissedStream()
                    } else {
                        connectionStatus = "disconnected"
                        statusText = localizedConnectionError(error)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post {
                    if (generation == connectionGeneration) handleIncoming(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post {
                    if (generation != connectionGeneration) return@post
                    this@EasyCodexController.webSocket = null
                    val error = localizedConnectionError(t.message)
                    failPending(error)
                    scheduleReconnect(error)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post {
                    if (generation != connectionGeneration) return@post
                    this@EasyCodexController.webSocket = null
                    val error = if (reason.isBlank()) strings.connectionClosed else localizedConnectionError(reason)
                    failPending(error)
                    if (manuallyDisconnected) {
                        connectionStatus = "disconnected"
                        statusText = error
                    } else {
                        scheduleReconnect(error)
                    }
                }
            }
        })
    }

    fun disconnect() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        manuallyDisconnected = true
        cancelReconnect()
        cancelAgentsRefresh()
        cancelStreamDeltaFlush()
        streamingAgentIds.clear()
        connectionGeneration++
        webSocket?.close(1000, "Closed")
        webSocket = null
        failPending(strings.connectionClosed)
        connectionStatus = "disconnected"
        statusText = strings.connectionClosed
    }

    fun refreshAgents() {
        cancelAgentsRefresh()
        if (streamingAgentIds.isNotEmpty()) {
            agentsRefreshQueued = true
            return
        }
        if (agentsRefreshInFlight) {
            agentsRefreshQueued = true
            return
        }
        agentsRefreshQueued = false
        agentsRefreshInFlight = true
        send("list_agents") { data, error ->
            if (error != null) {
                statusText = error
                finishAgentsRefresh()
                return@send
            }
            val array = data?.optJSONArray("data") ?: JSONArray()
            val nextAgents = mutableListOf<Agent>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { nextAgents.add(parseAgent(it)) }
            }
            if (streamingAgentIds.isNotEmpty()) {
                agentsRefreshQueued = true
                finishAgentsRefresh()
                return@send
            }
            val selectedAgent = activeAgent
            mergeAgents(nextAgents)
            if (activeAgentId != null && selectedAgent?.resumable != true && nextAgents.none { it.id == activeAgentId }) openHome()
            refreshCodexThreads(nextAgents) {
                finishAgentsRefresh()
            }
        }
    }

    fun openHome() {
        activeAgentId = null
        draftProjectLocked = false
        if (draftProjectCwd.isNullOrBlank()) {
            draftProjectCwd = defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        }
    }

    fun selectAgent(agentId: String) {
        draftProjectCwd = null
        draftProjectLocked = false
        activeAgentId = agentId
        val selected = activeAgent
        val threadId = selected?.codexThreadId
        if (selected?.resumable == true && !threadId.isNullOrBlank() && detailedCodexThreads[threadId] == null) {
            updateAgent(agentId) { it.copy(activity = CODEX_DETAIL_LOADING_LABEL) }
        }
        refreshActiveCodexThreadDetail()
    }

    fun startProjectDraft(cwd: String) {
        val cleaned = cleanNullablePath(cwd) ?: defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        val current = activeAgent
        activeAgentId = null
        draftProjectCwd = cleaned
        draftProjectLocked = true
        draftModel = current?.model?.takeIf { it.isNotBlank() } ?: defaultModel.ifBlank { DEFAULT_AGENT_MODEL }
        draftReasoningEffort = current?.reasoningEffort?.takeIf { it.isNotBlank() }
            ?: defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT }
        draftServiceTier = normalizeServiceTier(
            current?.serviceTier?.takeIf { it.isNotBlank() }
                ?: defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER },
        )
        inputText = ""
    }

    fun updateDraftProject(cwd: String) {
        activeAgentId = null
        draftProjectCwd = cleanNullablePath(cwd) ?: defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        draftProjectLocked = false
    }

    fun updateDraftModel(model: String) {
        activeAgentId = null
        draftModel = model
        val speedTiers = serviceTierOptionsFor(draftAgent)
        draftServiceTier = if (draftServiceTier in speedTiers) draftServiceTier else DEFAULT_SERVICE_TIER
    }

    fun updateDraftReasoningEffort(reasoningEffort: String) {
        activeAgentId = null
        draftReasoningEffort = reasoningEffort
    }

    fun updateDraftServiceTier(serviceTier: String) {
        activeAgentId = null
        draftServiceTier = normalizeServiceTier(serviceTier)
    }

    fun refreshRuntimeOptions() {
        send("runtime_capabilities") { data, error ->
            if (error == null) {
                data?.optJSONObject("data")?.let { runtimeCapabilities = parseRuntimeCapabilities(it) }
            }
        }
        send("list_codex_models", mapOf("includeHidden" to false)) { data, error ->
            if (error != null) {
                statusText = "${strings.connected}, $error"
                return@send
            }
            val array = data?.optJSONArray("data") ?: JSONArray()
            val nextModels = mutableListOf<CodexModelOption>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { model ->
                    parseCodexModel(model)?.let { nextModels.add(it) }
                }
            }
            if (nextModels.isNotEmpty()) {
                codexModels.clear()
                codexModels.addAll(nextModels.distinctBy { it.model })
                if (defaultModel !in codexModels.map { it.model }) {
                    defaultModel = codexModels.firstOrNull { it.isDefault }?.model ?: codexModels.first().model
                }
            }
        }
    }

    fun availableModelOptions(active: Agent?): List<CodexModelOption> {
        if (codexModels.isNotEmpty()) return codexModels
        val fallback = listOfNotNull(
            active?.model?.takeIf { it.isNotBlank() },
            defaultModel.takeIf { it.isNotBlank() },
        ).distinct()
        return fallback.map { CodexModelOption(model = it, displayName = it, isDefault = it == defaultModel) }
    }

    fun reasoningOptionsFor(agent: Agent?): List<String> {
        val selectedModel = agent?.model ?: defaultModel
        val fromModel = codexModels.firstOrNull { it.model == selectedModel }?.supportedReasoningEfforts
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return if (fromModel.isNotEmpty()) fromModel else listOf("low", "medium", "high", "xhigh")
    }

    fun serviceTierOptionsFor(agent: Agent?): List<String> {
        if (!runtimeCapabilities.supportsServiceTier) return emptyList()
        val selectedModel = agent?.model ?: defaultModel
        val additional = codexModels.firstOrNull { it.model == selectedModel }?.additionalSpeedTiers
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return (listOf("default") + additional).distinct()
    }

    fun updateActiveModel(model: String) {
        if (draftProjectCwd != null) {
            draftModel = model
            val speedTiers = serviceTierOptionsFor(draftAgent)
            draftServiceTier = if (draftServiceTier in speedTiers) draftServiceTier else DEFAULT_SERVICE_TIER
            return
        }
        val currentTier = normalizeServiceTier(activeAgent?.serviceTier ?: defaultServiceTier)
        val speedTiers = serviceTierOptionsFor(activeAgent?.copy(model = model))
        val nextTier = if (currentTier in speedTiers) currentTier else DEFAULT_SERVICE_TIER
        updateActiveConfig(model = model, serviceTier = nextTier)
    }

    fun updateActiveReasoningEffort(reasoningEffort: String) {
        if (draftProjectCwd != null) {
            draftReasoningEffort = reasoningEffort
            return
        }
        updateActiveConfig(reasoningEffort = reasoningEffort)
    }

    fun updateActiveServiceTier(serviceTier: String) {
        if (draftProjectCwd != null) {
            draftServiceTier = normalizeServiceTier(serviceTier)
            return
        }
        updateActiveConfig(serviceTier = serviceTier)
    }

    private fun updateActiveConfig(
        model: String? = null,
        reasoningEffort: String? = null,
        serviceTier: String? = null,
    ) {
        val agent = activeAgent ?: return
        val next = agent.copy(
            model = model ?: agent.model,
            reasoningEffort = reasoningEffort ?: agent.reasoningEffort,
            serviceTier = normalizeServiceTier(serviceTier ?: agent.serviceTier),
        )
        updateAgent(agent.id) { next }
        if (agent.resumable) return
        val params = mutableMapOf<String, Any?>("agentId" to agent.id)
        model?.let { params["model"] = it }
        reasoningEffort?.takeIf { runtimeCapabilities.supportsReasoningEffort }?.let { params["reasoningEffort"] = it }
        serviceTier?.takeIf { runtimeCapabilities.supportsServiceTier }?.let { params["serviceTier"] = normalizeServiceTier(it) }
        send("update_agent_config", params) { _, error ->
            if (error != null) {
                statusText = "参数更新失败：$error"
                updateAgent(agent.id) { agent }
            }
        }
    }

    fun createAgent(
        name: String,
        model: String,
        cwd: String,
        reasoningEffort: String = defaultReasoningEffort,
        serviceTier: String = defaultServiceTier,
        firstMessage: String? = null,
        firstDisplayMessage: String? = null,
    ) {
        isBusy = true
        val params = mutableMapOf<String, Any?>(
            "name" to name.ifBlank { "EasyCodex" },
            "model" to model.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
            "cwd" to cwd.ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } },
            "approvalPolicy" to "never",
        )
        if (runtimeCapabilities.supportsServiceTier) {
            params["serviceTier"] = normalizeServiceTier(serviceTier.ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } })
        }
        if (runtimeCapabilities.supportsReasoningEffort) {
            params["reasoningEffort"] = reasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } }
        }
        send(
            "create_agent",
            params,
        ) { data, error ->
            isBusy = false
            if (error != null) {
                statusText = error
                if (!firstMessage.isNullOrBlank()) inputText = firstMessage
                return@send
            }
            data?.optJSONObject("data")?.let {
                val agent = parseAgent(it)
                upsertAgent(agent)
                activeAgentId = agent.id
                draftProjectCwd = null
                if (!firstMessage.isNullOrBlank()) sendMessageToAgent(agent.id, firstMessage, firstDisplayMessage ?: firstMessage)
            }
        }
    }

    fun sendActiveMessage(planMode: Boolean = false) {
        val text = inputText.trim()
        if (text.isBlank()) return
        val transportText = if (planMode) "$PLAN_MODE_PROMPT$text" else text
        draftAgent?.let { draft ->
            inputText = ""
            createAgent(
                name = taskNameFromPrompt(text),
                model = draft.model,
                cwd = draft.cwd,
                reasoningEffort = draft.reasoningEffort,
                serviceTier = draft.serviceTier,
                firstMessage = transportText,
                firstDisplayMessage = text,
            )
            return
        }
        val agent = activeAgent ?: return
        if (agent.resumable) {
            inputText = ""
            resumeCodexThread(agent, transportText, text)
            return
        }
        inputText = ""
        sendMessageToAgent(agent.id, transportText, text)
    }

    fun showPlanReview(agentId: String, message: AgentMessage) {
        planReview = PlanReview(agentId, message)
        lastPromptedPlanKey = "${agentId}:${message.stableKey()}"
    }

    fun dismissPlanReview() {
        planReview = null
    }

    fun optimizePlan(review: PlanReview) {
        planReview = null
        sendMessageToAgent(review.agentId, PLAN_OPTIMIZE_PROMPT, "优化这个计划")
    }

    fun startPlan(review: PlanReview) {
        planReview = null
        sendMessageToAgent(review.agentId, PLAN_START_PROMPT, "开始这个计划")
    }

    fun appendToInput(value: String) {
        if (value.isBlank()) return
        inputText = when {
            inputText.isBlank() -> value
            inputText.endsWith(" ") || inputText.endsWith("\n") -> inputText + value
            else -> "$inputText $value"
        }
    }

    fun uploadActiveAttachments(uris: List<Uri>) {
        val cwd = draftProjectCwd ?: activeAgent?.cwd ?: return
        if (uris.isEmpty()) return
        val files = uris.take(12).mapNotNull { uri -> attachmentPayload(uri) }
        if (files.isEmpty()) {
            statusText = "没有读取到可上传的附件"
            return
        }
        isBusy = true
        statusText = "正在上传 ${files.size} 个附件"
        send(
            "upload_attachments",
            mapOf(
                "cwd" to cwd.ifBlank { "." },
                "files" to files,
            ),
        ) { data, error ->
            isBusy = false
            if (error != null) {
                statusText = "附件上传失败：$error"
                return@send
            }
            val uploaded = data?.optJSONObject("data")?.optJSONArray("files")
                ?: data?.optJSONArray("files")
                ?: JSONArray()
            val lines = mutableListOf<String>()
            for (index in 0 until uploaded.length()) {
                val file = uploaded.optJSONObject(index) ?: continue
                val name = file.optString("name", "附件")
                val path = file.optString("path")
                if (path.isNotBlank()) lines.add("- $name: $path")
            }
            if (lines.isEmpty()) {
                statusText = "附件已上传，但没有返回文件路径"
                return@send
            }
            val attachmentText = "已上传附件：\n${lines.joinToString("\n")}\n请结合这些附件继续处理。"
            inputText = if (inputText.isBlank()) attachmentText else "${inputText.trimEnd()}\n\n$attachmentText"
            statusText = "已上传 ${lines.size} 个附件"
        }
    }

    private fun attachmentPayload(uri: Uri): Map<String, Any?>? {
        val resolver = context.contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                input.copyTo(output)
                output.toByteArray()
            }
        }.getOrNull() ?: return null
        return mapOf(
            "name" to displayName(uri),
            "mimeType" to resolver.getType(uri),
            "size" to bytes.size,
            "base64" to Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    private fun displayName(uri: Uri): String {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(index)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
    }

    private fun sendMessageToAgent(agentId: String, text: String, displayText: String = text) {
        val wasBusy = agents.firstOrNull { it.id == agentId }?.isBusy() == true
        streamingAgentIds.add(agentId)
        appendMessage(agentId, AgentMessage("user", "user", displayText, System.currentTimeMillis()))
        updateAgent(agentId) {
            it.copy(
                status = "working",
                activity = if (wasBusy) "已加入队列，等待当前任务完成" else "已提交，等待 AI 接收",
            )
        }
        send("send_message", mapOf("agentId" to agentId, "text" to text)) { _, error ->
            if (error != null) {
                streamingAgentIds.remove(agentId)
                appendMessage(agentId, AgentMessage("agent", "status", "发送失败：$error", System.currentTimeMillis()))
                updateAgent(agentId) { it.copy(status = "error", activity = null) }
                refreshAgents()
            } else {
                updateAgent(agentId) { current ->
                    current.copy(activity = if (wasBusy) "已排队，当前任务完成后自动继续" else current.activity)
                }
            }
        }
    }

    private fun refreshCodexThreads(runningAgents: List<Agent>, onComplete: () -> Unit = {}) {
        refreshCodexThreadsPage(runningAgents, emptyList(), emptySet(), null, onComplete)
    }

    private fun refreshCodexThreadsPage(
        runningAgents: List<Agent>,
        importedSoFar: List<Agent>,
        visibleThreadIdsSoFar: Set<String>,
        cursor: String?,
        onComplete: () -> Unit,
    ) {
        val params = mutableMapOf<String, Any?>(
            "limit" to 100,
            "includeGlobal" to true,
            "all" to true,
        )
        if (!cursor.isNullOrBlank()) params["cursor"] = cursor
        send("list_codex_threads", params) { data, error ->
            if (error != null) {
                statusText = "已连接，EasyCodex 任务同步失败：$error"
                agentsRefreshQueued = true
                onComplete()
                return@send
            }
            val existingThreadIds = runningAgents.mapNotNull { it.codexThreadId }.toSet()
            val existingIds = (runningAgents + importedSoFar).map { it.id }.toSet()
            val array = data?.optJSONObject("data")?.optJSONArray("data")
                ?: data?.optJSONArray("data")
                ?: JSONArray()
            val imported = mutableListOf<Agent>()
            val pageVisibleThreadIds = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val thread = array.optJSONObject(index) ?: continue
                val threadId = thread.optString("id")
                if (!isRestorableCodexThread(thread)) continue
                if (threadId.isBlank()) continue
                pageVisibleThreadIds.add(threadId)
                if (threadId in existingThreadIds) continue
                val agent = mergeCodexThreadSummary(parseCodexThread(thread))
                if (agent.id !in existingIds && imported.none { it.id == agent.id }) imported.add(agent)
            }
            val nextImported = importedSoFar + imported
            val nextVisibleThreadIds = visibleThreadIdsSoFar + pageVisibleThreadIds
            val nextCursor = listOf(
                data?.optJSONObject("data")?.optString("nextCursor").orEmpty(),
                data?.optString("nextCursor").orEmpty(),
            ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            if (nextCursor.isNotBlank()) {
                refreshCodexThreadsPage(runningAgents, nextImported, nextVisibleThreadIds, nextCursor, onComplete)
                return@send
            }
            val selected = activeAgentId
            val visibleRunningAgents = runningAgents.filter {
                it.codexThreadId.isNullOrBlank() || it.codexThreadId in nextVisibleThreadIds
            }
            if (streamingAgentIds.isNotEmpty()) {
                agentsRefreshQueued = true
                onComplete()
                return@send
            }
            replaceAgents(visibleRunningAgents + nextImported)
            activeAgentId = when {
                selected != null && agents.any { it.id == selected } -> selected
                else -> null
            }
            if (activeAgentId == null && draftProjectCwd.isNullOrBlank()) {
                draftProjectCwd = defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
            }
            prefetchCodexThreadDetails(nextImported)
            refreshActiveCodexThreadDetail()
            onComplete()
        }
    }

    private fun finishAgentsRefresh() {
        agentsRefreshInFlight = false
        if (agentsRefreshQueued) {
            agentsRefreshQueued = false
            scheduleAgentsRefresh()
        }
    }

    private fun replaceAgents(nextAgents: List<Agent>) {
        reconcileAgents(mergedAgents(nextAgents), removeMissing = true)
    }

    private fun mergeAgents(nextAgents: List<Agent>) {
        reconcileAgents(mergedAgents(nextAgents), removeMissing = false)
    }

    private fun mergedAgents(nextAgents: List<Agent>): List<Agent> {
        val currentById = agents.associateBy { it.id }
        return nextAgents.map { incoming ->
            val current = currentById[incoming.id]
            if (current == null) {
                incoming
            } else {
                incoming.copy(
                    messages = mergeMessageLists(current.messages, incoming.messages),
                    activity = incoming.activity ?: current.activity,
                    updatedAt = mergedUpdatedAt(current, incoming),
                )
            }
        }
    }

    private fun mergedUpdatedAt(current: Agent, incoming: Agent): Long {
        return when {
            incoming.updatedAt <= 0L -> current.updatedAt
            current.updatedAt <= 0L -> incoming.updatedAt
            incoming.resumable && !current.isBusy() && !incoming.isBusy() -> incoming.updatedAt
            incoming.messages.isEmpty() -> current.updatedAt
            else -> maxOf(current.updatedAt, incoming.updatedAt)
        }
    }

    private fun reconcileAgents(nextAgents: List<Agent>, removeMissing: Boolean) {
        val previousProjectKeys = agents.projectOptionKeys()
        if (removeMissing) {
            val nextIds = nextAgents.map { it.id }.toSet()
            for (index in agents.lastIndex downTo 0) {
                if (agents[index].id !in nextIds) {
                    agents.removeAt(index)
                }
            }
        }
        nextAgents.forEachIndexed { targetIndex, agent ->
            val currentIndex = agents.indexOfFirst { it.id == agent.id }
            if (currentIndex < 0) {
                agents.add(targetIndex.coerceAtMost(agents.size), agent)
                return@forEachIndexed
            }
            if (currentIndex != targetIndex && targetIndex < agents.size) {
                val current = agents.removeAt(currentIndex)
                agents.add(targetIndex, current)
            }
            if (agents[targetIndex] != agent) {
                agents[targetIndex] = agent
            }
        }
        if (previousProjectKeys != agents.projectOptionKeys()) agentsRevision += 1
    }

    private fun mergeCodexThreadSummary(summary: Agent): Agent {
        val threadId = summary.codexThreadId ?: return summary
        val detail = detailedCodexThreads[threadId] ?: return summary
        return summary.copy(
            messages = detail.messages.ifEmpty { summary.messages },
            model = detail.model.ifBlank { summary.model },
            serviceTier = detail.serviceTier.ifBlank { summary.serviceTier },
            reasoningEffort = detail.reasoningEffort.ifBlank { summary.reasoningEffort },
            status = if (detail.isBusy()) detail.status else summary.status,
            activity = detail.activity ?: summary.activity,
            updatedAt = mergedUpdatedAt(summary, detail),
        )
    }

    private fun rememberLiveCodexThread(agent: Agent) {
        val threadId = agent.codexThreadId ?: return
        if (agent.messages.isEmpty() && !agent.isBusy()) return
        val cached = detailedCodexThreads[threadId]
        val merged = cached?.let {
            agent.copy(
                messages = mergeMessageLists(it.messages, agent.messages),
                activity = agent.activity ?: it.activity,
                updatedAt = mergedUpdatedAt(it, agent),
            )
        } ?: agent
        detailedCodexThreads[threadId] = merged
    }

    private fun refreshActiveCodexThreadDetail() {
        val agent = activeAgent ?: return
        refreshCodexThreadDetail(agent, force = true)
    }

    private fun prefetchCodexThreadDetails(importedAgents: List<Agent>) {
        importedAgents
            .asSequence()
            .filter { it.resumable && !it.codexThreadId.isNullOrBlank() }
            .filter { agent ->
                val threadId = agent.codexThreadId ?: return@filter false
                val cached = detailedCodexThreads[threadId]
                cached == null || cached.updatedAt < agent.updatedAt || cached.messages.isEmpty()
            }
            .sortedByDescending { it.updatedAt }
            .take(CODEX_THREAD_DETAIL_PREFETCH_LIMIT)
            .forEach { refreshCodexThreadDetail(it, force = false) }
    }

    private fun refreshCodexThreadDetail(agent: Agent, force: Boolean = false) {
        val threadId = agent.codexThreadId ?: return
        if (!agent.resumable) return
        threadDetailRetryRunnables.remove(threadId)?.let { main.removeCallbacks(it) }
        val cached = detailedCodexThreads[threadId]
        if (!force && cached != null && cached.updatedAt >= agent.updatedAt && cached.messages.isNotEmpty()) return
        if (!threadDetailsInFlight.add(threadId)) return
        val requestVersion = ++threadDetailRequestCounter
        latestThreadDetailRequests[threadId] = requestVersion
        send("read_codex_thread", mapOf("threadId" to threadId)) { data, error ->
            threadDetailsInFlight.remove(threadId)
            if (error != null) {
                if (latestThreadDetailRequests[threadId] == requestVersion) {
                    scheduleCodexThreadDetailRetry(agent, force)
                }
                return@send
            }
            if (latestThreadDetailRequests[threadId] != requestVersion) return@send
            val detailJson = data?.optJSONObject("data") ?: return@send
            threadDetailRetryCounts.remove(threadId)
            threadDetailRetryRunnables.remove(threadId)?.let { main.removeCallbacks(it) }
            val detailed = parseCodexThreadDetail(detailJson, agent).copy(
                activity = if (agent.isBusy()) agent.activity else null,
            )
            val cached = detailedCodexThreads[threadId]
            val merged = cached?.let {
                detailed.copy(
                    messages = mergeMessageLists(it.messages, detailed.messages),
                    status = if (it.isBusy()) it.status else detailed.status,
                    activity = it.activity ?: detailed.activity,
                    updatedAt = mergedUpdatedAt(it, detailed),
                )
            } ?: detailed
            detailedCodexThreads[threadId] = merged
            upsertAgentMerged(merged)
        }
    }

    private fun scheduleCodexThreadDetailRetry(agent: Agent, force: Boolean) {
        val threadId = agent.codexThreadId ?: return
        val retryCount = (threadDetailRetryCounts[threadId] ?: 0) + 1
        if (retryCount > CODEX_THREAD_DETAIL_MAX_RETRIES) {
            threadDetailRetryCounts.remove(threadId)
            threadDetailRetryRunnables.remove(threadId)
            updateAgent(agent.id) { current ->
                if (current.activity == CODEX_DETAIL_LOADING_LABEL || current.activity == CODEX_DETAIL_RETRY_LABEL) {
                    current.copy(activity = null)
                } else {
                    current
                }
            }
            return
        }
        threadDetailRetryCounts[threadId] = retryCount
        val delayMs = CODEX_THREAD_DETAIL_RETRY_BASE_MS * retryCount
        updateAgent(agent.id) { current ->
            if (current.id == activeAgentId || current.activity == CODEX_DETAIL_LOADING_LABEL || current.activity == CODEX_DETAIL_RETRY_LABEL) {
                current.copy(activity = CODEX_DETAIL_RETRY_LABEL)
            } else {
                current
            }
        }
        val retry = Runnable {
            threadDetailRetryRunnables.remove(threadId)
            val latestAgent = agents.firstOrNull { it.codexThreadId == threadId } ?: agent
            refreshCodexThreadDetail(latestAgent, force)
        }
        threadDetailRetryRunnables[threadId] = retry
        main.postDelayed(retry, delayMs)
    }

    private fun resumeCodexThread(agent: Agent, firstMessage: String? = null, firstDisplayMessage: String? = null) {
        val threadId = agent.codexThreadId ?: return
        isBusy = true
        updateAgent(agent.id) { it.copy(status = "resuming", activity = "正在恢复 EasyCodex 任务") }
        val params = mutableMapOf<String, Any?>(
            "agentId" to agent.id,
            "name" to agent.name,
            "model" to agent.model.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
            "cwd" to agent.cwd.ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } },
            "approvalPolicy" to "never",
            "codexThreadId" to threadId,
        )
        if (runtimeCapabilities.supportsServiceTier) {
            params["serviceTier"] = normalizeServiceTier(agent.serviceTier.ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } })
        }
        if (runtimeCapabilities.supportsReasoningEffort) {
            params["reasoningEffort"] = agent.reasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } }
        }
        send(
            "create_agent",
            params,
        ) { data, error ->
            isBusy = false
            if (error != null) {
                statusText = "恢复 EasyCodex 任务失败：$error"
                updateAgent(agent.id) { it.copy(status = "error", activity = null) }
                return@send
            }
            data?.optJSONObject("data")?.let {
                val resumed = parseAgent(it)
                upsertAgent(resumed)
                activeAgentId = resumed.id
                if (!firstMessage.isNullOrBlank()) sendMessageToAgent(resumed.id, firstMessage, firstDisplayMessage ?: firstMessage)
            }
        }
    }

    fun stopActiveAgent() {
        val agent = activeAgent ?: return
        send("stop_agent", mapOf("agentId" to agent.id)) { _, error ->
            if (error == null) updateAgent(agent.id) { it.copy(status = "stopped", activity = null) }
            else statusText = error
        }
    }

    fun respondApprovalRequest(request: AgentApprovalRequest, approved: Boolean) {
        approvalRequests.removeAll { it.id == request.id && it.agentId == request.agentId }
        send(
            "respond_agent_request",
            mapOf(
                "agentId" to request.agentId,
                "requestId" to request.id,
                "approved" to approved,
                "reason" to if (approved) "Approved from EasyCodex mobile" else "Denied from EasyCodex mobile",
            ),
        ) { _, error ->
            if (error != null) statusText = "审批响应失败：$error"
        }
    }

    private fun send(
        action: String,
        params: Map<String, Any?> = emptyMap(),
        callback: (JSONObject?, String?) -> Unit,
    ) {
        if (connectionStatus != "connected" && action != "auth") {
            callback(null, strings.disconnected)
            return
        }
        sendRaw(action, params, callback)
    }

    private fun sendRaw(
        action: String,
        params: Map<String, Any?> = emptyMap(),
        callback: (JSONObject?, String?) -> Unit,
    ) {
        val socket = webSocket
        if (socket == null) {
            callback(null, "WebSocket 不可用")
            return
        }
        val requestId = "android_${++requestCounter}"
        pending[requestId] = callback
        val body = JSONObject()
            .put("action", action)
            .put("requestId", requestId)
            .put("params", JSONObject(params))
        if (!socket.send(body.toString())) {
            pending.remove(requestId)
            callback(null, strings.connectionClosed)
            return
        }
        main.postDelayed({
            val timeoutCallback = pending.remove(requestId)
            timeoutCallback?.invoke(null, strings.connectionTimeout(relayUrl))
        }, RELAY_REQUEST_TIMEOUT_MS)
    }

    private fun syncClientLanguage() {
        sendRaw("update_client_language", mapOf("language" to resolvedAppLanguage(appLanguage))) { _, _ -> }
    }

    private fun scheduleReconnect(error: String) {
        if (relayUrl.isBlank() || apiKey.isBlank() || manuallyDisconnected) {
            connectionStatus = "disconnected"
            statusText = error
            return
        }
        val delayMillis = reconnectDelayMillis()
        reconnectAttempts += 1
        val seconds = delayMillis / 1000
        connectionStatus = "connecting"
        statusText = if (strings.settings == "Settings") "$error, reconnecting in ${seconds}s" else "$error，${seconds} 秒后自动重连"
        cancelReconnect()
        reconnectRunnable = Runnable { connect() }
        main.postDelayed(reconnectRunnable!!, delayMillis)
    }

    private fun reconnectDelayMillis(): Long {
        val seconds = when (reconnectAttempts) {
            0 -> 1
            1 -> 2
            2 -> 4
            3 -> 8
            else -> 15
        }
        return seconds * 1000L
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { main.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun scheduleAgentsRefresh() {
        agentsRefreshRunnable?.let { main.removeCallbacks(it) }
        agentsRefreshRunnable = Runnable {
            agentsRefreshRunnable = null
            refreshAgents()
        }
        main.postDelayed(agentsRefreshRunnable!!, AGENTS_REFRESH_DEBOUNCE_MS)
    }

    private fun cancelAgentsRefresh() {
        agentsRefreshRunnable?.let { main.removeCallbacks(it) }
        agentsRefreshRunnable = null
    }

    private fun failPending(error: String) {
        val callbacks = pending.values.toList()
        pending.clear()
        callbacks.forEach { it(null, error) }
    }

    private fun replayMissedStream() {
        if (lastStreamSessionId.isBlank()) return
        send(
            "replay_stream",
            mapOf(
                "sessionId" to lastStreamSessionId,
                "afterSeq" to lastStreamSeq,
                "limit" to 1000,
            ),
        ) { data, error ->
            if (error != null) return@send
            val payload = data?.optJSONObject("data") ?: return@send
            val events = payload.optJSONArray("events") ?: JSONArray()
            for (index in 0 until events.length()) {
                events.optJSONObject(index)?.let { handleStreamEnvelope(it) }
            }
            rememberStreamPosition(
                payload.optString("sessionId", lastStreamSessionId),
                payload.optLong("latestSeq", lastStreamSeq),
            )
        }
    }

    private fun handleIncoming(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = msg.optString("requestId")
        if (requestId.isNotBlank() && (msg.optString("type") == "response" || msg.optString("type") == "error")) {
            val callback = pending.remove(requestId) ?: return
            if (msg.optString("type") == "error") callback(null, msg.optString("error", "请求失败"))
            else callback(msg, null)
            return
        }

        if (msg.optString("type") == "stream") {
            handleStreamEnvelope(msg)
        }
    }

    private fun handleStreamEnvelope(msg: JSONObject) {
        rememberStreamPosition(msg.optString("sessionId", lastStreamSessionId), msg.optLong("seq", lastStreamSeq))
        val agentId = msg.optString("agentId")
        val event = msg.optString("event")
        val data = msg.optJSONObject("data") ?: JSONObject()
        handleStream(agentId, event, data)
    }

    private fun rememberStreamPosition(sessionId: String, seq: Long) {
        if (sessionId.isBlank() || seq <= 0L) return
        if (sessionId == lastStreamSessionId && seq <= lastStreamSeq) return
        lastStreamSessionId = sessionId
        lastStreamSeq = seq
        prefs.edit()
            .putString("last_stream_session_id", sessionId)
            .putLong("last_stream_seq", seq)
            .apply()
    }

    private fun handleStream(agentId: String, event: String, data: JSONObject) {
        if (agentId.isBlank()) return
        when (event) {
            "codex/threads_changed",
            "agents/changed" -> {
                refreshActiveCodexThreadDetail()
                scheduleAgentsRefresh()
            }
            "turn/started" -> {
                streamingAgentIds.add(agentId)
                updateAgent(agentId) { it.copy(status = "working", activity = "正在运行中，AI 正在接手任务", updatedAt = System.currentTimeMillis()) }
            }
            "turn/queued" -> {
                val position = data.optInt("position", 1).coerceAtLeast(1)
                val queuedAt = data.optLong("timestamp", System.currentTimeMillis())
                updateAgent(agentId) {
                    it.copy(
                        status = "working",
                        activity = "已加入队列，前面还有 ${position - 1} 个任务",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                finalizeMessage(agentId, "queued_$queuedAt", "已加入队列，当前任务完成后自动继续。", "status")
            }
            "turn/dequeued" -> {
                updateAgent(agentId) { it.copy(status = "working", activity = "队列任务已开始，正在运行中", updatedAt = System.currentTimeMillis()) }
            }
            "turn/completed" -> {
                streamingAgentIds.remove(agentId)
                val completedAt = System.currentTimeMillis()
                updateAgent(agentId) { it.copy(status = "ready", activity = null, updatedAt = completedAt) }
                recordAgentAlert(agentId, AgentAlertKind.Completed, data.optString("preview"))
                scheduleAgentsRefresh()
            }
            "turn/failed" -> {
                streamingAgentIds.remove(agentId)
                val text = data.optJSONObject("error")?.optString("message") ?: "运行失败"
                appendMessage(agentId, AgentMessage("agent", "status", text, System.currentTimeMillis()))
                updateAgent(agentId) { it.copy(status = "error", activity = null, updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Error, text)
                scheduleAgentsRefresh()
            }
            "agent/stopped" -> {
                streamingAgentIds.remove(agentId)
                updateAgent(agentId) { it.copy(status = "stopped", activity = null, updatedAt = System.currentTimeMillis()) }
                val code = data.optString("code")
                if (code.isNotBlank() && code != "0" && code != "null") {
                    recordAgentAlert(agentId, AgentAlertKind.Error, "任务进程已退出，退出码 $code")
                }
                scheduleAgentsRefresh()
            }
            "agent/stderr" -> {
                val text = data.optString("text")
                if (text.isNotBlank()) {
                    appendMessage(agentId, AgentMessage("agent", "status", text, System.currentTimeMillis()))
                }
            }
            "agent/requested" -> {
                val requestId = data.optString("requestId")
                if (requestId.isNotBlank()) {
                    val agent = agents.firstOrNull { it.id == agentId }
                    val method = data.optString("method").ifBlank { "approval" }
                    val text = data.optString("text")
                        .ifBlank { data.optJSONObject("params")?.let { jsonSummary(it) }.orEmpty() }
                        .ifBlank { method }
                    approvalRequests.removeAll { it.id == requestId && it.agentId == agentId }
                    approvalRequests.add(
                        AgentApprovalRequest(
                            id = requestId,
                            agentId = agentId,
                            method = method,
                            title = "${agent?.name ?: "EasyCodex"} 需要确认",
                            detail = text,
                            timestamp = data.optLong("timestamp", System.currentTimeMillis()),
                        ),
                    )
                    finalizeMessage(agentId, "request_$requestId", text, "status", streaming = true)
                    updateAgent(agentId) { it.copy(status = "working", activity = "正在等待你的确认", updatedAt = System.currentTimeMillis()) }
                }
            }
            "agent/request_resolved" -> {
                val requestId = data.optString("requestId")
                approvalRequests.removeAll { it.id == requestId && it.agentId == agentId }
                updateAgent(agentId) { it.copy(activity = "确认已发送，等待 Codex 继续执行", updatedAt = System.currentTimeMillis()) }
            }
            "item/started" -> {
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item)
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = true)
                if (itemId.isNotBlank()) finalizeMessage(agentId, itemId, text, type, streaming = true)
                updateAgent(agentId) {
                    it.copy(
                        status = "working",
                        activity = activityForMessageType(type, started = true),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
            "item/agentMessage/delta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "agent")
                updateAgentActivityThrottled(agentId, "正在生成回复")
            }
            "item/reasoning/delta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，推理内容持续返回")
            }
            "item/reasoning/textDelta",
            "item/reasoning/summaryTextDelta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，整理执行步骤")
            }
            "item/reasoning/summaryPartAdded" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = listOf("text", "summary", "part", "content")
                    .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
                    .orEmpty()
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，整理执行步骤")
            }
            "item/commandOutput/delta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "正在运行命令，输出持续返回")
            }
            "item/commandExecution/outputDelta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "正在运行命令，检查执行结果")
            }
            "item/fileChange/delta",
            "item/fileChange/outputDelta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "file_change")
                updateAgentActivityThrottled(agentId, "正在修改文件，改动内容持续更新")
            }
            "item/fileChange/patchUpdated" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val text = fileChangesText(data.optJSONArray("changes"))
                    .ifBlank { data.optJSONObject("item")?.let { streamItemText(it, "file_change", started = false) }.orEmpty() }
                if (itemId.isNotBlank() && text.isNotBlank()) finalizeMessage(agentId, itemId, text, "file_change")
            }
            "item/plan/delta" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("delta")
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "plan")
                updateAgentActivityThrottled(agentId, "正在规划步骤，准备继续执行")
            }
            "item/mcpToolCall/progress" -> {
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("message").let { if (it.isNotBlank()) "$it\n" else "" }
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "AI 正在使用工具，等待工具进度返回")
            }
            "item/completed" -> {
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item)
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (itemId.isNotBlank() && text.isNotBlank()) finalizeMessage(agentId, itemId, text, type)
            }
            "rawResponseItem/completed" -> {
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item).ifBlank { "raw_${System.currentTimeMillis()}" }
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (text.isNotBlank()) finalizeMessage(agentId, itemId, text, type)
            }
            "response_item" -> {
                val item = data.optJSONObject("payload") ?: data
                val itemId = itemId(item).ifBlank { "response_${System.currentTimeMillis()}" }
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (text.isNotBlank()) finalizeMessage(agentId, itemId, text, type)
            }
            "turn/diff/updated" -> {
                val turnId = data.optString("turnId").ifBlank { data.optString("turn_id") }.ifBlank { "turn_${System.currentTimeMillis()}" }
                val text = data.optString("diff")
                if (text.isNotBlank()) finalizeMessage(agentId, "turn_diff_$turnId", text, "file_change")
            }
            "turn/plan/updated" -> {
                val turnId = data.optString("turnId").ifBlank { data.optString("turn_id") }.ifBlank { "turn_${System.currentTimeMillis()}" }
                val text = listOf(data.optString("explanation"), planText(data.optJSONArray("plan")))
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                if (text.isNotBlank()) finalizeMessage(agentId, "plan_$turnId", text, "plan")
            }
            "thread/tokenUsage/updated" -> {
                val turnId = data.optString("turnId").ifBlank { data.optString("turn_id") }.ifBlank { "turn_${System.currentTimeMillis()}" }
                val tokenUsage = data.optJSONObject("tokenUsage")
                val text = tokenUsage?.let { "Token usage\n${jsonSummary(it)}" }.orEmpty()
                if (text.isNotBlank()) finalizeMessage(agentId, "tokens_$turnId", text, "status")
            }
            "event_msg" -> {
                handleEventMessage(agentId, data.optJSONObject("payload") ?: data)
            }
        }
    }

    private fun handleEventMessage(agentId: String, payload: JSONObject) {
        when (payload.optString("type")) {
            "task_started" -> {
                streamingAgentIds.add(agentId)
                updateAgent(agentId) { it.copy(status = "working", activity = "正在运行中，AI 正在使用中", updatedAt = System.currentTimeMillis()) }
                val itemId = payload.optString("turn_id").ifBlank { payload.optString("turnId") }.ifBlank { "thinking_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, "正在思考中，准备拆解任务。", "thinking", streaming = true)
            }
            "agent_message" -> {
                val text = payload.optString("message").ifBlank { payload.optString("text") }
                if (text.isNotBlank()) finalizeMessage(agentId, "agent_${System.currentTimeMillis()}", text, "agent")
            }
            "task_complete" -> {
                val text = payload.optString("last_agent_message")
                if (text.isNotBlank()) finalizeMessage(agentId, "agent_${System.currentTimeMillis()}", text, "agent")
                streamingAgentIds.remove(agentId)
                updateAgent(agentId) { it.copy(status = "ready", activity = null, updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, classifyCompletionAlert(text), text)
                scheduleAgentsRefresh()
            }
            "error" -> {
                val text = payload.optString("message").ifBlank { payload.optString("error") }.ifBlank { "运行失败" }
                finalizeMessage(agentId, "error_${System.currentTimeMillis()}", text, "status")
                updateAgent(agentId) { it.copy(status = "error", activity = null, updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Error, text)
            }
            "turn_aborted" -> {
                val reason = payload.optString("reason")
                val text = if (reason.isNotBlank()) "任务已中止：$reason" else "任务已中止"
                finalizeMessage(agentId, "aborted_${System.currentTimeMillis()}", text, "status")
                streamingAgentIds.remove(agentId)
                updateAgent(agentId) { it.copy(status = "ready", activity = null, updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Confirmation, text)
            }
            "exec_command_begin" -> {
                val itemId = itemId(payload).ifBlank { "exec_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, eventCommandText(payload), "command", streaming = true)
            }
            "exec_command_end",
            "mcp_tool_call_end" -> {
                val itemId = itemId(payload).ifBlank { "output_${System.currentTimeMillis()}" }
                val text = listOf("aggregated_output", "output", "stdout", "stderr")
                    .firstNotNullOfOrNull { key -> payload.optString(key).takeIf { it.isNotBlank() } }
                    .orEmpty()
                if (text.isNotBlank()) finalizeMessage(agentId, itemId, text, "command_output")
            }
            "mcp_tool_call_begin" -> {
                val itemId = itemId(payload).ifBlank { "mcp_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, eventCommandText(payload), "command", streaming = true)
            }
            "patch_apply_begin" -> {
                val itemId = itemId(payload).ifBlank { "patch_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, "apply_patch", "command", streaming = true)
            }
            "patch_apply_end" -> {
                val itemId = itemId(payload).ifBlank { "patch_${System.currentTimeMillis()}" }
                val text = fileChangesText(payload.optJSONArray("changes"))
                    .ifBlank { fileChangesText(payload.optJSONObject("changes")) }
                    .ifBlank { payload.optString("stdout") }
                    .ifBlank { payload.optString("message") }
                    .ifBlank { "文件已修改" }
                finalizeMessage(agentId, itemId, text, "file_change")
            }
            "token_count" -> {
                val text = payload.optJSONObject("info")?.let { "Token usage\n${jsonSummary(it)}" }
                    ?: payload.optJSONObject("rate_limits")?.let { "Rate limits\n${jsonSummary(it)}" }
                    ?: ""
                if (text.isNotBlank()) finalizeMessage(agentId, "tokens_${System.currentTimeMillis()}", text, "status")
            }
            "web_search_end" -> {
                val text = jsonSummary(payload)
                if (text.isNotBlank()) finalizeMessage(agentId, "web_${System.currentTimeMillis()}", text, "command_output")
            }
        }
    }

    private fun recordAgentAlert(agentId: String, kind: AgentAlertKind, detail: String = "") {
        val agent = agents.firstOrNull { it.id == agentId }
        val agentName = agent?.name?.takeIf { it.isNotBlank() } ?: "EasyCodex"
        val fallbackDetail = agent?.messages?.lastOrNull { it.role == "agent" && it.type == "agent" }?.text.orEmpty()
        val alertDetail = detail.ifBlank { fallbackDetail }.trim().replace(Regex("\\s+"), " ").take(180)
        val title = when (kind) {
            AgentAlertKind.Completed -> "$agentName 已完成任务"
            AgentAlertKind.Question -> "$agentName 有问题等你回答"
            AgentAlertKind.Confirmation -> "$agentName 有待确认事项"
            AgentAlertKind.Error -> "$agentName 需要处理"
        }
        val body = alertDetail.ifBlank {
            when (kind) {
                AgentAlertKind.Completed -> "任务已经做完了。"
                AgentAlertKind.Question -> "智能体发来了需要你回答的问题。"
                AgentAlertKind.Confirmation -> "智能体正在等待你的确认。"
                AgentAlertKind.Error -> "任务运行失败或进程已退出。"
            }
        }
        val alert = AgentAlert(
            id = "alert_${System.currentTimeMillis()}_${agentId.takeLast(6)}",
            agentId = agentId,
            agentName = agentName,
            kind = kind,
            title = title,
            detail = body,
            timestamp = System.currentTimeMillis(),
        )
        alerts.add(0, alert)
        while (alerts.size > 60) alerts.removeAt(alerts.lastIndex)
        playAgentAlertSound(kind)
        showAgentSystemNotification(alert)
    }

    private fun classifyCompletionAlert(text: String): AgentAlertKind {
        val normalized = text.lowercase(Locale.ROOT)
        val needsConfirmation = listOf("确认", "批准", "同意", "是否", "要不要", "选择", "待确认", "需要你", "请回复")
            .any { normalized.contains(it) }
        val asksQuestion = "?" in text || "？" in text || normalized.contains("question") || normalized.contains("answer")
        return when {
            needsConfirmation -> AgentAlertKind.Confirmation
            asksQuestion -> AgentAlertKind.Question
            else -> AgentAlertKind.Completed
        }
    }

    private fun playAgentAlertSound(kind: AgentAlertKind) {
        val toneType = when (kind) {
            AgentAlertKind.Completed -> ToneGenerator.TONE_PROP_ACK
            AgentAlertKind.Question,
            AgentAlertKind.Confirmation -> ToneGenerator.TONE_PROP_BEEP
            AgentAlertKind.Error -> ToneGenerator.TONE_PROP_NACK
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).apply {
                startTone(toneType, 180)
                main.postDelayed({ release() }, 260)
            }
        }
    }

    private fun showAgentSystemNotification(alert: AgentAlert) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureAgentAlertChannel(manager)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("agentId", alert.agentId)
                putExtra("alertId", alert.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                alert.id.hashCode(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
            val notification = Notification.Builder(context, AGENT_ALERT_CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(alert.title)
                .setContentText(alert.detail)
                .setStyle(Notification.BigTextStyle().bigText(alert.detail))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(alert.timestamp)
                .setCategory(Notification.CATEGORY_STATUS)
                .build()
            manager.notify(AGENT_ALERT_NOTIFICATION_BASE_ID + (alert.agentId.hashCode() and 0x0FFF), notification)
        }
    }

    private fun ensureAgentAlertChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            AGENT_ALERT_CHANNEL_ID,
            "EasyCodex 任务提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "任务完成、提问、待确认和错误提醒"
            setSound(soundUri, attrs)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun itemId(item: JSONObject): String {
        return listOf("id", "itemId", "call_id", "callId", "turn_id", "turnId")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            .orEmpty()
    }

    private fun activityForMessageType(type: String, started: Boolean): String {
        return when (type) {
            "command" -> if (started) "正在运行命令，等待执行结果" else "命令执行完成"
            "command_output" -> "正在读取命令输出"
            "file_change" -> if (started) "正在修改文件，准备生成改动" else "文件改动已更新"
            "sub_agent" -> if (started) "子代理正在工作，等待返回结果" else "子代理结果已返回"
            "thinking" -> "正在思考中，AI 正在使用中"
            "plan" -> "正在规划步骤，准备继续执行"
            "status" -> "状态已更新，等待下一步反馈"
            else -> if (started) "正在运行中，等待 AI 反馈" else "运行阶段已更新"
        }
    }

    private fun streamItemText(item: JSONObject, type: String, started: Boolean): String {
        val direct = listOf("text", "command", "output", "diff", "patch", "path", "file", "message", "aggregatedOutput", "aggregated_output", "stdout", "stderr")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
        if (!direct.isNullOrBlank()) {
            return itemDetailPrefix(item, type).let { prefix ->
                listOf(prefix, direct).filter { it.isNotBlank() }.joinToString("\n\n")
            }
        }
        val changes = fileChangesText(item.optJSONArray("changes")).ifBlank { fileChangesText(item.optJSONObject("changes")) }
        if (changes.isNotBlank()) return changes
        val content = contentText(item.optJSONArray("content"))
        if (content.isNotBlank()) return content
        val summary = contentText(item.optJSONArray("summary"))
        if (summary.isNotBlank()) return summary
        val result = item.opt("result")?.let { jsonSummary(it) }.orEmpty()
        if (result.isNotBlank() && result != "null") return result
        val args = item.opt("arguments")?.let { jsonSummary(it) }.orEmpty()
        if (args.isNotBlank() && args != "null") return itemDetailPrefix(item, type).let { prefix ->
            listOf(prefix, "args: $args").filter { it.isNotBlank() }.joinToString("\n\n")
        }
        return when (type) {
            "thinking" -> "正在思考中..."
            "command" -> if (started) "命令已开始执行。" else "命令执行完成。"
            "command_output" -> "命令输出已返回。"
            "file_change" -> if (started) "正在修改文件。" else "文件改动已更新。"
            "plan" -> "执行计划已更新。"
            "status" -> "状态已更新。"
            else -> if (started) "正在运行中..." else ""
        }
    }

    private fun itemDetailPrefix(item: JSONObject, type: String): String {
        val lines = mutableListOf<String>()
        if (type == "command" || type == "command_output") {
            val server = item.optString("server")
            val tool = item.optString("tool").ifBlank { item.optString("name") }
            if (server.isNotBlank() || tool.isNotBlank()) lines.add(listOf(server, tool).filter { it.isNotBlank() }.joinToString("."))
            val cwd = item.optString("cwd")
            if (cwd.isNotBlank()) lines.add("cwd: $cwd")
            val status = item.optString("status")
            if (status.isNotBlank()) lines.add("status: $status")
            if (item.has("exitCode") && !item.isNull("exitCode")) lines.add("exit: ${item.optInt("exitCode")}")
            if (item.has("durationMs") && !item.isNull("durationMs")) lines.add("duration: ${item.optLong("durationMs")}ms")
        }
        if (type == "file_change") {
            val status = item.optString("status")
            if (status.isNotBlank()) lines.add("status: $status")
        }
        return lines.joinToString("\n")
    }

    private fun contentText(array: JSONArray?): String {
        if (array == null) return ""
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            when (item) {
                is String -> if (item.isNotBlank()) values.add(item)
                is JSONObject -> {
                    val text = listOf("text", "message", "content")
                        .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
                    if (!text.isNullOrBlank()) values.add(text)
                }
            }
        }
        return values.joinToString("\n")
    }

    private fun fileChangesText(array: JSONArray?): String {
        if (array == null) return ""
        val blocks = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val change = array.optJSONObject(index) ?: continue
            val path = change.optString("path")
            val kind = change.optString("kind").ifBlank { change.optString("type") }
            val diff = change.optString("diff").ifBlank { change.optString("unified_diff") }.ifBlank { change.optString("content") }
            blocks.add(listOf(path, if (kind.isNotBlank()) "status: $kind" else "", diff).filter { it.isNotBlank() }.joinToString("\n"))
        }
        return blocks.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun fileChangesText(obj: JSONObject?): String {
        if (obj == null) return ""
        val blocks = mutableListOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            val change = obj.optJSONObject(path)
            if (change == null) {
                blocks.add(path)
                continue
            }
            val kind = change.optString("kind").ifBlank { change.optString("type") }
            val diff = change.optString("diff").ifBlank { change.optString("unified_diff") }.ifBlank { change.optString("content") }
            blocks.add(listOf(path, if (kind.isNotBlank()) "status: $kind" else "", diff).filter { it.isNotBlank() }.joinToString("\n"))
        }
        return blocks.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun planText(plan: JSONArray?): String {
        if (plan == null) return ""
        val rows = mutableListOf<String>()
        for (index in 0 until plan.length()) {
            val step = plan.optJSONObject(index) ?: continue
            val status = step.optString("status")
            val text = step.optString("step").ifBlank { step.optString("text") }
            if (text.isNotBlank()) rows.add(listOf(if (status.isNotBlank()) "[$status]" else "", text).filter { it.isNotBlank() }.joinToString(" "))
        }
        return rows.joinToString("\n")
    }

    private fun jsonSummary(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        return when (value) {
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> value.toString()
        }
    }

    private fun eventCommandText(payload: JSONObject): String {
        val direct = listOf("command", "cmd", "text", "name", "tool", "tool_name", "toolName")
            .firstNotNullOfOrNull { key -> payload.optString(key).takeIf { it.isNotBlank() } }
        if (!direct.isNullOrBlank()) return direct
        val server = payload.optString("server").ifBlank { payload.optString("server_name") }
        val tool = payload.optString("tool").ifBlank { payload.optString("name") }
        return listOf(server, tool).filter { it.isNotBlank() }.joinToString(".").ifBlank { "Tool call started." }
    }

    private fun parseAgent(json: JSONObject): Agent {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val parsedMessages = buildList {
            for (index in 0 until messages.length()) {
                messages.optJSONObject(index)?.let { add(parseMessage(it)) }
            }
        }
        return Agent(
            id = json.optString("id"),
            name = json.optString("name", "EasyCodex"),
            model = json.optString("model", DEFAULT_AGENT_MODEL),
            cwd = json.optString("cwd", "."),
            projectRoot = cleanNullablePath(json.optString("projectRoot")),
            status = json.optString("status", "stopped"),
            serviceTier = normalizeServiceTier(json.optString("serviceTier", DEFAULT_SERVICE_TIER)),
            reasoningEffort = json.optString("reasoningEffort", DEFAULT_REASONING_EFFORT),
            activity = json.optString("activityLabel")
                .ifBlank { json.optString("activity") }
                .takeIf { it.isNotBlank() },
            messages = parsedMessages,
            codexThreadId = json.optString("codexThreadId").takeIf { it.isNotBlank() },
            updatedAt = parsedMessages.maxOfOrNull { it.timestamp }
                ?: jsonTimestamp(json, "updatedAt", System.currentTimeMillis()),
        )
    }

    private fun parseCodexThread(json: JSONObject): Agent {
        val threadId = json.optString("id")
        val cwd = json.optString("cwd", ".")
        val name = json.optString("name").takeIf { it.isNotBlank() }
            ?: cwd.split('\\', '/').lastOrNull { it.isNotBlank() }
            ?: "EasyCodex 任务"
        val preview = json.optString("preview").takeIf { it.isNotBlank() }
        val updatedAt = jsonTimestamp(json, "updatedAt", jsonTimestamp(json, "createdAt", 0L))
        val messages = if (preview.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(AgentMessage("agent", "status", preview, updatedAt))
        }
        return Agent(
            id = "codex_$threadId",
            name = name,
            model = json.optString("model", defaultModel.ifBlank { DEFAULT_AGENT_MODEL }),
            cwd = cwd,
            projectRoot = cleanNullablePath(json.optString("projectRoot")),
            status = codexThreadStatus(json),
            serviceTier = normalizeServiceTier(json.optString("serviceTier", defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER })),
            reasoningEffort = json.optString("reasoningEffort", defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT }),
            activity = json.optString("activityLabel")
                .ifBlank { json.optString("activity") }
                .takeIf { it.isNotBlank() },
            messages = messages,
            codexThreadId = threadId,
            preview = preview,
            resumable = true,
            updatedAt = updatedAt,
        )
    }

    private fun parseCodexThreadDetail(json: JSONObject, fallback: Agent): Agent {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val parsedMessages = buildList {
            for (index in 0 until messages.length()) {
                messages.optJSONObject(index)?.let { add(parseMessage(it)) }
            }
        }
        val summary = parseCodexThread(json)
        val authoritativeUpdatedAt = listOf(
            summary.updatedAt,
            parsedMessages.maxOfOrNull { it.timestamp } ?: 0L,
        ).filter { it > 0L }.maxOrNull()
        return summary.copy(
            id = fallback.id,
            status = if (summary.isBusy()) summary.status else fallback.status,
            messages = parsedMessages.ifEmpty { summary.messages.ifEmpty { fallback.messages } },
            resumable = true,
            updatedAt = authoritativeUpdatedAt ?: fallback.updatedAt,
        )
    }

    private fun isRestorableCodexThread(json: JSONObject): Boolean {
        val hiddenStatus = setOf("archived", "deleted", "removed", "trashed")
        val status = json.optString("status").trim().lowercase(Locale.ROOT)
        if (status in hiddenStatus) return false
        return !listOf("archived", "deleted", "removed", "trashed", "isArchived", "isDeleted")
            .any { json.optBoolean(it, false) }
    }

    private fun parseRuntimeCapabilities(json: JSONObject): RuntimeCapabilities {
        return RuntimeCapabilities(
            providerMode = json.optString("providerMode", "official"),
            supportsServiceTier = json.optBoolean("supportsServiceTier", true),
            supportsReasoningEffort = json.optBoolean("supportsReasoningEffort", true),
            reason = json.optString("reason", "official Codex runtime"),
        )
    }

    private fun parseCodexModel(json: JSONObject): CodexModelOption? {
        val model = json.optString("model").ifBlank { json.optString("id") }
        if (model.isBlank()) return null
        val efforts = json.optJSONArray("supportedReasoningEfforts") ?: JSONArray()
        val parsedEfforts = buildList {
            for (index in 0 until efforts.length()) {
                val item = efforts.optJSONObject(index)
                val effort = item?.optString("reasoningEffort").orEmpty()
                if (effort.isNotBlank()) add(effort)
            }
        }
        val speedTiers = json.optJSONArray("additionalSpeedTiers") ?: JSONArray()
        val parsedSpeedTiers = buildList {
            for (index in 0 until speedTiers.length()) {
                val speedTier = speedTiers.optString(index).orEmpty()
                if (speedTier.isNotBlank()) add(speedTier)
            }
        }
        return CodexModelOption(
            model = model,
            displayName = json.optString("displayName", model).ifBlank { model },
            defaultReasoningEffort = json.optString("defaultReasoningEffort", DEFAULT_REASONING_EFFORT),
            supportedReasoningEfforts = parsedEfforts,
            additionalSpeedTiers = parsedSpeedTiers,
            isDefault = json.optBoolean("isDefault", false),
        )
    }

    private fun parseDirectoryListing(json: JSONObject): DirectoryListing {
        fun parseOptions(array: JSONArray): List<DirectoryOption> = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = cleanNullablePath(item.optString("path")) ?: continue
                val name = item.optString("name").ifBlank { projectNameFromCwd(path) }
                add(DirectoryOption(name = name, path = path))
            }
        }
        fun parseWorktrees(array: JSONArray): List<WorktreeOption> = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = cleanNullablePath(item.optString("path")) ?: continue
                val name = item.optString("name").ifBlank { projectNameFromCwd(path) }
                val branch = item.optString("branch").takeIf { it.isNotBlank() }
                add(
                    WorktreeOption(
                        name = name,
                        path = path,
                        branch = branch,
                        current = item.optBoolean("current", false),
                        locked = item.optBoolean("locked", false),
                    ),
                )
            }
        }
        val path = cleanNullablePath(json.optString("path")) ?: defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        return DirectoryListing(
            path = path,
            parent = cleanNullablePath(json.optString("parent")),
            roots = parseOptions(json.optJSONArray("roots") ?: JSONArray()),
            worktrees = parseWorktrees(json.optJSONArray("worktrees") ?: JSONArray()),
            entries = parseOptions(json.optJSONArray("entries") ?: JSONArray()),
        )
    }

    private fun parseMessage(json: JSONObject): AgentMessage {
        return AgentMessage(
            role = json.optString("role", "agent"),
            type = json.optString("type", "agent"),
            text = json.optString("text"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            itemId = json.optString("_itemId").ifBlank { json.optString("itemId") }.takeIf { it.isNotBlank() },
        )
    }

    private fun upsertAgent(agent: Agent) {
        val index = agents.indexOfFirst { it.id == agent.id }
        if (index >= 0) {
            if (agents[index] != agent) {
                val projectChanged = agents[index].projectOptionKey() != agent.projectOptionKey()
                agents[index] = agent
                if (projectChanged) agentsRevision += 1
            }
        } else {
            agents.add(0, agent)
            agentsRevision += 1
        }
        rememberLiveCodexThread(agent)
    }

    private fun upsertAgentMerged(agent: Agent) {
        val index = agents.indexOfFirst { it.id == agent.id }
        if (index < 0) {
            agents.add(0, agent)
            agentsRevision += 1
            return
        }
        val current = agents[index]
        val next = agent.copy(
            messages = mergeMessageLists(current.messages, agent.messages),
            activity = agent.activity ?: current.activity,
            updatedAt = mergedUpdatedAt(current, agent),
        )
        if (current != next) {
            val projectChanged = current.projectOptionKey() != next.projectOptionKey()
            agents[index] = next
            if (projectChanged) agentsRevision += 1
            rememberLiveCodexThread(next)
        }
    }

    private fun mergeMessageLists(current: List<AgentMessage>, incoming: List<AgentMessage>): List<AgentMessage> {
        if (current.isEmpty()) return incoming
        if (incoming.isEmpty()) return current
        if (current.size == 1 && current.first().type == "status" && incoming.size > 1) return incoming
        if (incoming.size == 1 && incoming.first().type == "status" && current.size > 1) return current
        val merged = current.toMutableList()
        val itemIdIndexes = merged
            .mapIndexedNotNull { index, message -> message.itemId?.takeIf { it.isNotBlank() }?.let { it to index } }
            .toMap()
            .toMutableMap()
        val contentIndexes = merged
            .mapIndexed { index, message -> message.contentKey() to index }
            .toMap()
            .toMutableMap()
        incoming.forEach { message ->
            val existingIndex = message.itemId
                ?.takeIf { it.isNotBlank() }
                ?.let { itemIdIndexes[it] }
                ?: contentIndexes[message.contentKey()]
                ?: -1
            if (existingIndex >= 0) {
                val existing = merged[existingIndex]
                merged[existingIndex] = mergeMessage(existing, message)
            } else {
                merged.add(message)
                val nextIndex = merged.lastIndex
                message.itemId?.takeIf { it.isNotBlank() }?.let { itemIdIndexes[it] = nextIndex }
                contentIndexes[message.contentKey()] = nextIndex
            }
        }
        return merged
    }

    private fun AgentMessage.contentKey(): Triple<String, String, String> {
        return Triple(role, type, text)
    }

    private fun List<Agent>.projectOptionKeys(): List<Pair<String, String>> {
        return map { it.id to it.projectOptionKey() }
    }

    private fun Agent.projectOptionKey(): String {
        return normalizePathKey(cleanNullablePath(projectRoot ?: cwd) ?: DEFAULT_AGENT_CWD)
    }

    private fun mergeMessage(current: AgentMessage, incoming: AgentMessage): AgentMessage {
        val text = when {
            current.streaming && current.text.startsWith(incoming.text) -> current.text
            incoming.text.startsWith(current.text) -> incoming.text
            current.text.startsWith(incoming.text) -> incoming.text
            else -> mergeStreamingText(current.text, incoming.text)
        }
        val keepCurrentStreaming = current.streaming && text == current.text && text.length >= incoming.text.length
        val timestamp = if (current.itemId.isNullOrBlank() && incoming.itemId.isNullOrBlank() && current.text == incoming.text) {
            current.timestamp
        } else {
            maxOf(current.timestamp, incoming.timestamp)
        }
        return current.copy(
            type = incoming.type,
            text = text,
            timestamp = timestamp,
            itemId = current.itemId ?: incoming.itemId,
            streaming = keepCurrentStreaming,
        )
    }

    private fun updateAgent(agentId: String, transform: (Agent) -> Agent) {
        val index = agents.indexOfFirst { it.id == agentId }
        if (index >= 0) {
            val current = agents[index]
            val next = transform(current)
            if (next != current) {
                val projectChanged = current.projectOptionKey() != next.projectOptionKey()
                agents[index] = next
                if (projectChanged) agentsRevision += 1
                rememberLiveCodexThread(next)
            }
        }
    }

    private fun updateAgentActivityThrottled(agentId: String, activity: String) {
        val now = System.currentTimeMillis()
        val lastUpdatedAt = lastActivityUpdateAt[agentId] ?: 0L
        if (now - lastUpdatedAt < AGENT_ACTIVITY_UPDATE_THROTTLE_MS) return
        lastActivityUpdateAt[agentId] = now
        updateAgent(agentId) {
            it.copy(status = "working", activity = activity, updatedAt = now)
        }
    }

    private fun appendMessage(agentId: String, message: AgentMessage) {
        updateAgent(agentId) { it.copy(messages = it.messages + message, updatedAt = message.timestamp) }
    }

    private fun appendDelta(agentId: String, itemId: String, delta: String, type: String) {
        if (agentId.isBlank() || itemId.isBlank() || delta.isBlank()) return
        val key = streamDeltaKey(agentId, itemId)
        val pendingDelta = pendingStreamDeltas[key]
        if (pendingDelta == null) {
            pendingStreamDeltas[key] = PendingStreamDelta(agentId, itemId, type).also {
                it.text.append(delta)
            }
        } else {
            pendingDelta.type = type
            pendingDelta.text.append(delta)
        }
        scheduleStreamDeltaFlush()
    }

    private fun applyDelta(agentId: String, itemId: String, delta: String, type: String) {
        updateAgent(agentId) { agent ->
            val index = agent.messages.indexOfLast { it.itemId == itemId }
            val nextMessages = agent.messages.toMutableList()
            if (index >= 0) {
                val existing = nextMessages[index]
                nextMessages[index] = existing.copy(text = mergeStreamingText(existing.text, delta), type = type, streaming = true)
            } else {
                nextMessages.add(AgentMessage("agent", type, delta, System.currentTimeMillis(), itemId, true))
            }
            agent.copy(messages = nextMessages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun finalizeMessage(agentId: String, itemId: String, text: String, type: String, streaming: Boolean = false) {
        flushPendingDelta(agentId, itemId)
        updateAgent(agentId) { agent ->
            val index = agent.messages.indexOfLast { it.itemId == itemId }
            val nextMessages = agent.messages.toMutableList()
            if (index >= 0) {
                val existing = nextMessages[index]
                val nextText = if (existing.type == type) {
                    mergeFinalText(existing.text, text, streaming)
                } else {
                    text
                }
                nextMessages[index] = existing.copy(text = nextText, type = type, streaming = streaming)
            } else {
                nextMessages.add(AgentMessage("agent", type, text, System.currentTimeMillis(), itemId, streaming))
            }
            agent.copy(messages = nextMessages, updatedAt = System.currentTimeMillis())
        }
        maybePromptPlanReview(agentId, itemId, type, streaming)
    }

    private fun maybePromptPlanReview(agentId: String, itemId: String, type: String, streaming: Boolean) {
        if (type != "plan" || streaming) return
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        val message = agent.messages.lastOrNull { it.itemId == itemId && it.type == "plan" } ?: return
        val key = "${agentId}:${message.stableKey()}"
        if (key == lastPromptedPlanKey) return
        lastPromptedPlanKey = key
        planReview = PlanReview(agentId, message)
    }

    private fun scheduleStreamDeltaFlush() {
        if (streamDeltaFlushRunnable != null) return
        streamDeltaFlushRunnable = Runnable {
            streamDeltaFlushRunnable = null
            flushStreamDeltas()
        }
        main.postDelayed(streamDeltaFlushRunnable!!, STREAM_DELTA_FLUSH_MS)
    }

    private fun flushStreamDeltas() {
        if (pendingStreamDeltas.isEmpty()) return
        val deltas = pendingStreamDeltas.values.toList()
        pendingStreamDeltas.clear()
        deltas.forEach { delta ->
            applyDelta(delta.agentId, delta.itemId, delta.text.toString(), delta.type)
        }
    }

    private fun flushPendingDelta(agentId: String, itemId: String) {
        val delta = pendingStreamDeltas.remove(streamDeltaKey(agentId, itemId)) ?: return
        applyDelta(delta.agentId, delta.itemId, delta.text.toString(), delta.type)
    }

    private fun cancelStreamDeltaFlush() {
        streamDeltaFlushRunnable?.let { main.removeCallbacks(it) }
        streamDeltaFlushRunnable = null
        pendingStreamDeltas.clear()
    }

    private fun streamDeltaKey(agentId: String, itemId: String): String = "$agentId\u0000$itemId"

    private fun mergeStreamingText(current: String, incoming: String): String {
        if (incoming.isBlank()) return current
        if (current.isBlank() || isStreamingPlaceholder(current)) return incoming
        if (isStreamingPlaceholder(incoming)) return current
        if (incoming == current) return current
        if (incoming.startsWith(current)) return incoming
        if (current.startsWith(incoming)) return current
        val overlap = longestSuffixPrefixLength(current, incoming)
        return current + incoming.drop(overlap)
    }

    private fun mergeFinalText(current: String, incoming: String, streaming: Boolean): String {
        if (streaming) return mergeStreamingText(current, incoming)
        if (incoming.isBlank()) return current
        if (current.isBlank() || isStreamingPlaceholder(current)) return incoming
        if (incoming == current) return current
        if (incoming.startsWith(current)) return incoming
        if (current.startsWith(incoming)) return incoming
        return mergeStreamingText(current, incoming)
    }

    private fun isStreamingPlaceholder(text: String): Boolean {
        return text.trim() in setOf(
            "...",
            "Working...",
            "Thinking...",
            "Command started.",
            "Command completed.",
            "Command output received.",
            "File change started.",
            "Files changed.",
            "Status updated.",
            "Tool call started.",
            "Shell command started.",
            "正在思考中...",
            "正在运行中...",
            "命令已开始执行。",
            "命令执行完成。",
            "命令输出已返回。",
            "正在修改文件。",
            "文件改动已更新。",
            "执行计划已更新。",
            "状态已更新。",
        )
    }

    private fun longestSuffixPrefixLength(left: String, right: String): Int {
        val max = minOf(left.length, right.length)
        for (length in max downTo 1) {
            if (left.regionMatches(left.length - length, right, 0, length)) return length
        }
        return 0
    }

    private fun messageType(raw: String): String {
        val normalized = raw.lowercase()
        return when {
            normalized.contains("usermessage") -> "user"
            normalized.contains("agentmessage") || normalized == "message" -> "agent"
            normalized.contains("reasoning") -> "thinking"
            normalized.contains("plan") -> "plan"
            normalized.contains("commandoutput") || normalized.contains("shelloutput") -> "command_output"
            normalized.contains("subagent") || normalized.contains("collabagent") -> "sub_agent"
            normalized.contains("commandexecution") -> "command"
            normalized.contains("mcp") || normalized.contains("dynamictool") -> "command"
            normalized.contains("websearch") || normalized.contains("web_search") -> "command"
            normalized.contains("command") || normalized.contains("tool") -> "command"
            normalized.contains("file") || normalized.contains("codechange") -> "file_change"
            normalized.contains("image") || normalized.contains("review") || normalized.contains("compaction") -> "status"
            normalized.contains("status") || normalized.contains("error") || normalized.contains("failed") -> "status"
            else -> "agent"
        }
    }

    private fun clientId(): String {
        val existing = prefs.getString("client_id", null)
        if (!existing.isNullOrBlank()) return existing
        val next = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", next).apply()
        return next
    }

    private fun localizedConnectionError(raw: String?): String {
        val message = raw.orEmpty()
        val endpoint = relayEndpointLabel()
        return when {
            message.isBlank() -> strings.connectionFailed
            message.equals("Closed", ignoreCase = true) -> strings.connectionClosed
            message.contains("Failed to connect", ignoreCase = true) -> strings.failedToConnect(endpoint)
            message.contains("Connection refused", ignoreCase = true) -> strings.connectionRefused(endpoint)
            message.contains("timeout", ignoreCase = true) -> strings.connectionTimeout(endpoint)
            message.contains("401") || message.contains("unauthorized", ignoreCase = true) -> strings.unauthorized
            message.contains("403") || message.contains("forbidden", ignoreCase = true) -> strings.forbidden
            else -> message
        }
    }

    private fun relayEndpointLabel(): String {
        return runCatching {
            val uri = URI(relayUrl)
            listOfNotNull(uri.host, uri.port.takeIf { it > 0 }?.toString()).joinToString(":")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: relayUrl.ifBlank { strings.endpointNotFilled }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyCodexApp(importedConnection: Boolean = false) {
    val context = LocalContext.current
    val controller = remember { EasyCodexController(context.applicationContext) }
    val strings = appStringsFor(controller.appLanguage)
    val prefs = remember { context.getSharedPreferences(EASY_CODEX_PREFS, Context.MODE_PRIVATE) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var appContentReady by remember { mutableStateOf(false) }
    var startupMaskVisible by remember { mutableStateOf(true) }
    var openedInitialSettings by remember { mutableStateOf(false) }
    var showCreateAgent by remember { mutableStateOf(false) }
    var createAgentInitialCwd by remember { mutableStateOf<String?>(null) }
    var showTroubleshooting by remember { mutableStateOf(false) }
    var showUsageGuide by remember { mutableStateOf(!prefs.getBoolean(PREF_USAGE_GUIDE_SEEN, false)) }
    var planModeEnabled by remember { mutableStateOf(false) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.reloadSettings()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun openSettings() {
        settingsLauncher.launch(Intent(context, SettingsActivity::class.java))
    }
    fun finishUsageGuide(openConfiguration: Boolean = false) {
        prefs.edit().putBoolean(PREF_USAGE_GUIDE_SEEN, true).apply()
        showUsageGuide = false
        if (openConfiguration) {
            openedInitialSettings = true
            openSettings()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        controller.uploadActiveAttachments(uris)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        controller.uploadActiveAttachments(uris)
    }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            controller.appendToInput(matches.firstOrNull().orEmpty())
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(520)
        appContentReady = true
        delay(280)
        startupMaskVisible = false
    }

    LaunchedEffect(appContentReady) {
        if (!appContentReady) return@LaunchedEffect
        controller.connect()
    }

    LaunchedEffect(appContentReady) {
        if (
            appContentReady &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.disconnect() }
    }

    LaunchedEffect(importedConnection, appContentReady) {
        if (importedConnection && appContentReady) {
            controller.reloadSettings()
        }
    }

    LaunchedEffect(controller.apiKey, startupMaskVisible, showUsageGuide) {
        if (!startupMaskVisible && !openedInitialSettings && controller.apiKey.isBlank() && !showUsageGuide) {
            openedInitialSettings = true
            openSettings()
        }
    }

    EasyCodexTheme(
        context = context,
        themeMode = controller.themeMode,
        themeColor = controller.themeColor,
        oledMode = controller.oledMode,
    ) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
        Box(Modifier.fillMaxSize()) {
            if (appContentReady) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        AgentDrawer(
                            agents = controller.agents,
                            activeAgentId = controller.activeAgentId,
                            onHome = {
                                controller.openHome()
                                scope.launch { drawerState.close() }
                            },
                            onSelect = {
                                controller.selectAgent(it)
                                scope.launch { drawerState.close() }
                            },
                            onCreateInProject = { cwd ->
                                controller.startProjectDraft(cwd)
                                scope.launch { drawerState.close() }
                            },
                        )
                    },
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            controller.draftAgent?.let { strings.homeQuestion }
                                                ?: controller.activeAgent?.name
                                                ?: "EasyCodex",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (controller.activeAgent != null) {
                                                agentStatusLabel(controller.activeAgent!!)
                                            } else {
                                                controller.statusText
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = strings.agentsContentDescription)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { openSettings() }) {
                                        Icon(Icons.Default.Settings, contentDescription = strings.settingsContentDescription)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ),
                            )
                        },
                        bottomBar = {
                            val draftAgent = controller.draftAgent
                            val activeAgent = controller.activeAgent
                            val composerAgent = draftAgent ?: activeAgent
                            val projectOptions = controller.projectOptions()
                            MessageComposer(
                                text = controller.inputText,
                                enabled = controller.connectionStatus == "connected" && composerAgent != null,
                                agent = composerAgent,
                                projectOptions = projectOptions,
                                canChangeProject = draftAgent != null && !controller.draftProjectLocked,
                                modelOptions = controller.availableModelOptions(composerAgent),
                                reasoningOptions = controller.reasoningOptionsFor(composerAgent),
                                serviceTierOptions = controller.serviceTierOptionsFor(composerAgent),
                                runtimeCapabilities = controller.runtimeCapabilities,
                                planModeEnabled = planModeEnabled,
                                onTextChange = { controller.inputText = it },
                                onPlanModeChange = { planModeEnabled = it },
                                onProjectChange = { controller.updateDraftProject(it) },
                                onModelChange = { controller.updateActiveModel(it) },
                                onReasoningEffortChange = { controller.updateActiveReasoningEffort(it) },
                                onServiceTierChange = { controller.updateActiveServiceTier(it) },
                                onBrowseDirectories = { path, callback -> controller.browseDirectories(path, callback) },
                                onAttachFiles = { filePicker.launch("*/*") },
                                onAttachImages = { imagePicker.launch("image/*") },
                                onInsertEmoji = { controller.appendToInput(it) },
                                onVoiceInput = {
                                    val intent = buildSystemVoiceInputIntent(context)
                                    runCatching { voiceInput.launch(intent) }
                                        .onFailure { controller.statusText = strings.startVoiceInput }
                                },
                                onSend = { controller.sendActiveMessage(planModeEnabled) },
                            )
                        },
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            ConnectionBanner(
                                status = controller.connectionStatus,
                                detail = controller.statusText,
                                onHelp = { showTroubleshooting = true },
                                onConfigure = if (controller.relayUrl.isBlank() || controller.apiKey.isBlank()) {
                                    { openSettings() }
                                } else {
                                    null
                                },
                            )
                            val draftAgent = controller.draftAgent
                            if (draftAgent != null) {
                                val projectOptions = controller.projectOptions()
                                HomeTaskScreen(
                                    draftAgent = draftAgent,
                                    projectOptions = projectOptions,
                                    reasoningOptions = controller.reasoningOptionsFor(draftAgent),
                                    serviceTierOptions = controller.serviceTierOptionsFor(draftAgent),
                                    recentAgents = controller.agents,
                                    runtimeCapabilities = controller.runtimeCapabilities,
                                    canChangeProject = !controller.draftProjectLocked,
                                    onProjectChange = { controller.updateDraftProject(it) },
                                    onReasoningEffortChange = { controller.updateActiveReasoningEffort(it) },
                                    onServiceTierChange = { controller.updateActiveServiceTier(it) },
                                    onOpenAgent = { controller.selectAgent(it) },
                                    onBrowseDirectories = { path, callback -> controller.browseDirectories(path, callback) },
                                )
                            } else {
                                Conversation(
                                    agent = controller.activeAgent,
                                    layoutMode = controller.appLayout,
                                    emptyMessage = strings.emptyConversation,
                                    onOpenPlan = { message ->
                                        controller.activeAgent?.let { controller.showPlanReview(it.id, message) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = startupMaskVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                exit = fadeOut(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)),
            ) {
                StartupMask()
            }
        }
        }
    }

    if (showCreateAgent) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            CreateAgentDialog(
                busy = controller.isBusy,
                initialModel = controller.defaultModel,
                initialCwd = createAgentInitialCwd ?: controller.defaultCwd,
                modelOptions = controller.availableModelOptions(controller.activeAgent),
                reasoningOptions = controller.reasoningOptionsFor(controller.activeAgent),
                runtimeCapabilities = controller.runtimeCapabilities,
                onDismiss = {
                    showCreateAgent = false
                    createAgentInitialCwd = null
                },
                onCreate = { name, model, cwd, reasoningEffort ->
                    controller.createAgent(name, model, cwd, reasoningEffort)
                    showCreateAgent = false
                    createAgentInitialCwd = null
                },
            )
        }
    }

    if (showTroubleshooting) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            ConnectionTroubleshootingDialog(
                status = controller.connectionStatus,
                detail = controller.statusText,
                relayUrl = controller.relayUrl,
                onConfigure = {
                    showTroubleshooting = false
                    openSettings()
                },
                onDismiss = { showTroubleshooting = false },
            )
        }
    }

    controller.approvalRequests.firstOrNull()?.let { request ->
        ApprovalRequestDialog(
            request = request,
            onApprove = { controller.respondApprovalRequest(request, approved = true) },
            onDeny = { controller.respondApprovalRequest(request, approved = false) },
        )
    }

    val activePlanReview = controller.planReview
    if (activePlanReview != null) {
        PlanReviewDialog(
            review = activePlanReview,
            onDismiss = { controller.dismissPlanReview() },
            onOptimize = { controller.optimizePlan(activePlanReview) },
            onStart = { controller.startPlan(activePlanReview) },
        )
    }

    if (showUsageGuide) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            UsageGuideDialog(
                needsConfiguration = controller.apiKey.isBlank(),
                onConfigure = { finishUsageGuide(openConfiguration = true) },
                onDismiss = { finishUsageGuide() },
            )
        }
    }
}

@Composable
private fun StartupMask() {
    val strings = LocalAppStrings.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                EasyCodexAppIcon(
                    modifier = Modifier.size(72.dp),
                    contentDescription = "EasyCodex",
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
                Text(
                    strings.preparingEasyCodex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EasyCodexAppIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(id = R.drawable.easy_code_app_icon),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun AgentDrawer(
    agents: List<Agent>,
    activeAgentId: String?,
    onHome: () -> Unit,
    onSelect: (String) -> Unit,
    onCreateInProject: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val groupedAgents = agents
        .sortedByDescending { it.updatedAt }
        .groupBy { cleanNullablePath(it.projectRoot) ?: cleanNullablePath(it.cwd) ?: DEFAULT_AGENT_CWD }
    val projectPaths = groupedAgents.keys.toList()
    var collapsedProjectPaths by remember { mutableStateOf(emptySet<String>()) }
    val allProjectsCollapsed = projectPaths.isNotEmpty() && projectPaths.all { it in collapsedProjectPaths }

    LaunchedEffect(projectPaths) {
        collapsedProjectPaths = collapsedProjectPaths.intersect(projectPaths.toSet())
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EasyCodexAppIcon(
                modifier = Modifier.size(40.dp),
                contentDescription = "EasyCodex",
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("EasyCodex", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(strings.easyCodexAgents, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            color = if (activeAgentId == null) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onHome),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (activeAgentId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.home,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (agents.isEmpty()) {
            Text(
                strings.noAgents,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ModalDrawerSheet
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item("drawer_projects_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.projects,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            collapsedProjectPaths = if (allProjectsCollapsed) {
                                emptySet()
                            } else {
                                projectPaths.toSet()
                            }
                        },
                    ) {
                        Text(if (allProjectsCollapsed) strings.expandAll else strings.collapseAll)
                    }
                }
            }
            groupedAgents.forEach { (projectPath, projectAgents) ->
                val expanded = projectPath !in collapsedProjectPaths
                item("project_$projectPath") {
                    ProjectHeader(
                        name = projectNameFromCwd(projectPath),
                        taskCount = projectAgents.size,
                        expanded = expanded,
                        onToggle = {
                            collapsedProjectPaths = if (expanded) {
                                collapsedProjectPaths + projectPath
                            } else {
                                collapsedProjectPaths - projectPath
                            }
                        },
                        onCreate = { onCreateInProject(projectPath) },
                    )
                }
                if (expanded) {
                    items(projectAgents, key = { agent -> "agent_${agent.id}" }) { agent ->
                        AgentProjectRow(
                            agent = agent,
                            selected = agent.id == activeAgentId,
                            onClick = { onSelect(agent.id) },
                        )
                    }
                }
                item("project_spacer_$projectPath") {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ProjectHeader(
    name: String,
    taskCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                strings.tasks(taskCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) strings.projectTaskCollapse else strings.projectTaskExpand,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onCreate,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = strings.createInProject,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AgentProjectRow(agent: Agent, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalAppStrings.current
    val rowColor = if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    agent.isBusy() -> MaterialTheme.colorScheme.primary
                    agent.status.equals("error", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(Modifier.width(10.dp))
            Text(
                agent.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (agent.isBusy()) {
                Row(
                    modifier = Modifier.weight(0.72f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        agent.activity?.takeIf { it.isNotBlank() } ?: "加载中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    relativeTime(agent.updatedAt, strings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTaskScreen(
    draftAgent: Agent,
    projectOptions: List<String>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    recentAgents: List<Agent>,
    runtimeCapabilities: RuntimeCapabilities,
    canChangeProject: Boolean,
    onProjectChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
) {
    val strings = LocalAppStrings.current
    var showProjectPicker by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EasyCodexAppIcon(modifier = Modifier.size(54.dp), contentDescription = "EasyCodex")
                Text(
                    strings.homeQuestion,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    strings.sendToEasyCodex,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (canChangeProject) item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showProjectPicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EasyCodexIconBubble(icon = Icons.Default.Folder)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings.project,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            projectNameFromCwd(draftAgent.cwd),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            draftAgent.cwd,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val recent = recentAgents.sortedByDescending { it.updatedAt }.take(5)
        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.recentTasks,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recent, key = { it.id }) { agent ->
                AgentProjectRow(
                    agent = agent,
                    selected = false,
                    onClick = { onOpenAgent(agent.id) },
                )
            }
        }
    }

    if (showProjectPicker && canChangeProject) {
        DirectoryPickerDialog(
            initialPath = draftAgent.cwd,
            pinnedPaths = projectOptions,
            onBrowseDirectories = onBrowseDirectories,
            onSelect = {
                onProjectChange(it)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectoryPickerDialog(
    initialPath: String,
    pinnedPaths: List<String>,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var currentPath by remember(initialPath) { mutableStateOf(initialPath) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(path: String?) {
        loading = true
        error = null
        onBrowseDirectories(path) { next, nextError ->
            loading = false
            if (next != null) {
                listing = next
                currentPath = next.path
            } else {
                error = nextError ?: strings.directoryUnreadable
            }
        }
    }

    LaunchedEffect(initialPath) {
        load(initialPath)
    }

    val pinned = pinnedPaths
        .mapNotNull { cleanNullablePath(it) }
        .distinctBy { normalizePathKey(it) }
        .filter { normalizePathKey(it) != normalizePathKey(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.chooseProjectDirectory) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pinned.take(4).forEach { path ->
                        AssistChip(
                            onClick = { load(path) },
                            label = { Text(projectNameFromCwd(path), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    listing?.roots.orEmpty().forEach { root ->
                        AssistChip(
                            onClick = { load(root.path) },
                            label = { Text(root.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val worktrees = listing?.worktrees.orEmpty()
                if (worktrees.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Git worktrees",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        worktrees.forEach { worktree ->
                            WorktreePickerRow(
                                worktree = worktree,
                                onClick = { load(worktree.path) },
                            )
                        }
                    }
                }
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(strings.readingDirectory, style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listing?.parent?.let { parent ->
                        item {
                            DirectoryPickerRow(
                                name = "..",
                                path = parent,
                                onClick = { load(parent) },
                            )
                        }
                    }
                    items(listing?.entries.orEmpty()) { entry ->
                        DirectoryPickerRow(
                            name = entry.name,
                            path = entry.path,
                            onClick = { load(entry.path) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(listing?.path ?: currentPath) },
                enabled = listing != null,
            ) {
                Text(strings.useThisDirectory)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun WorktreePickerRow(
    worktree: WorktreeOption,
    onClick: () -> Unit,
) {
    val detail = listOfNotNull(
        worktree.branch?.takeIf { it.isNotBlank() },
        if (worktree.current) "current" else null,
        if (worktree.locked) "locked" else null,
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (worktree.current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (worktree.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(worktree.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    worktree.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DirectoryPickerRow(
    name: String,
    path: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun projectNameFromCwd(cwd: String): String {
    val cleaned = cleanNullablePath(cwd) ?: return "未命名项目"
    return cleaned
        .trimEnd('\\', '/')
        .split('\\', '/')
        .lastOrNull { it.isNotBlank() }
        ?: "未命名项目"
}

private fun normalizePathKey(path: String): String {
    return path.trim().trimEnd('\\', '/').replace('\\', '/').lowercase(Locale.ROOT)
}

fun taskNameFromPrompt(prompt: String): String {
    return prompt
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(32)
        ?: "EasyCodex"
}

private fun cleanNullablePath(value: String?): String? {
    val cleaned = value?.trim().orEmpty()
    return cleaned.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}

private fun jsonTimestamp(json: JSONObject, key: String, fallback: Long = 0L): Long {
    val value = json.opt(key)
    val raw = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    if (raw == null && value is String) {
        return try {
            Instant.parse(value.trim()).toEpochMilli()
        } catch (_: Exception) {
            fallback
        }
    }
    val normalizedRaw = raw ?: return fallback
    return when {
        normalizedRaw > 1_000_000_000_000L -> normalizedRaw
        normalizedRaw > 1_000_000_000L -> normalizedRaw * 1000L
        normalizedRaw > 0L -> normalizedRaw
        else -> fallback
    }
}

fun relativeTime(timestamp: Long, strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE)): String {
    if (timestamp <= 0L) return ""
    val diffMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    val week = 7 * day
    return when {
        diffMillis < minute -> strings.justNow
        diffMillis < hour -> strings.minuteShort(diffMillis / minute)
        diffMillis < day -> strings.hourShort(diffMillis / hour)
        diffMillis < week -> strings.dayShort(diffMillis / day)
        else -> strings.weekShort(diffMillis / week)
    }
}

private fun codexThreadStatus(json: JSONObject): String {
    val raw = json.opt("status")
    return when (raw) {
        is JSONObject -> raw.optString("type")
        is String -> raw
        else -> ""
    }.trim().takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) } ?: "可恢复"
}

private fun Agent.isBusy(): Boolean {
    return status.trim().lowercase(Locale.ROOT) in setOf(
        "initializing",
        "resuming",
        "working",
        "running",
        "active",
        "in_progress",
        "inprogress",
        "in-progress",
        "pending",
        "processing",
        "queued",
        "starting",
        "streaming",
    )
}

@Composable
fun ConnectionBanner(
    status: String,
    detail: String,
    onHelp: () -> Unit,
    onConfigure: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val color = when (status) {
        "connected" -> MaterialTheme.colorScheme.primary
        "connecting" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape),
            ) {
                Surface(color = color, modifier = Modifier.fillMaxSize()) {}
            }
            Spacer(Modifier.width(8.dp))
            Text(
                detail,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onConfigure != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfigure) {
                    Text(strings.fillIn)
                }
            }
            IconButton(onClick = onHelp) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = strings.connectionTroubleshooting)
            }
        }
    }
}

@Composable
private fun ApprovalRequestDialog(
    request: AgentApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    request.method,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    request.detail,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onApprove) {
                Text("批准")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("拒绝")
            }
        },
    )
}

@Composable
private fun PlanReviewDialog(
    review: PlanReview,
    onDismiss: () -> Unit,
    onOptimize: () -> Unit,
    onStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("是否开始这个计划？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "你可以先单独查看完整计划，也可以让 EasyCodex 继续优化后再执行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MarkdownMessageContent(review.message.text.ifBlank { "计划内容为空。" })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onStart) {
                Text("开始计划")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOptimize) {
                    Text("优化计划")
                }
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        },
    )
}

@Composable
fun ConnectionTroubleshootingDialog(
    status: String,
    detail: String,
    relayUrl: String,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val statusLabel = when (status) {
        "connected" -> strings.connected
        "connecting" -> strings.connecting
        else -> strings.disconnected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.connectionTroubleshooting) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.currentStatus(statusLabel))
                Text(strings.messageDetail(detail.ifBlank { strings.noDetail }))
                Text(strings.relayAddress(relayUrl.ifBlank { strings.notFilled }))
                Text(strings.troubleshootingChecklist)
                Text(strings.troubleshootingStepRelay)
                Text(strings.troubleshootingStepNetwork)
                Text(strings.troubleshootingStepPort)
                Text(strings.troubleshootingStepApiKey)
            }
        },
        confirmButton = { Button(onClick = onConfigure) { Text(strings.openSettings) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun UsageGuideDialog(
    needsConfiguration: Boolean,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val darkGuide = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val guideContainerColor = if (darkGuide) Color(0xFF121212) else Color(0xFFFFFFFF)
    val guideStepColor = if (darkGuide) Color(0xFF1B1B1B) else Color(0xFFF6F6F6)
    val guideIconColor = if (darkGuide) Color(0xFF242424) else Color(0xFFEAEAEA)
    val guideBorderColor = if (darkGuide) Color(0xFF343434) else Color(0xFFE1E1E1)
    val guidePrimaryTextColor = if (darkGuide) Color(0xFFEDEDED) else Color(0xFF1F1F1F)
    val guideSecondaryTextColor = if (darkGuide) Color(0xFFB8B8B8) else Color(0xFF666666)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = guideContainerColor,
        titleContentColor = guidePrimaryTextColor,
        textContentColor = guideSecondaryTextColor,
        tonalElevation = 0.dp,
        title = { Text(strings.usageGuideTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    strings.usageGuideIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.Settings,
                    title = strings.usageGuideStepConnectionTitle,
                    body = strings.usageGuideStepConnectionBody,
                    containerColor = guideStepColor,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.Folder,
                    title = strings.usageGuideStepProjectTitle,
                    body = strings.usageGuideStepProjectBody,
                    containerColor = guideStepColor,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = strings.usageGuideStepTaskTitle,
                    body = strings.usageGuideStepTaskBody,
                    containerColor = guideStepColor,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.AttachFile,
                    title = strings.usageGuideStepContextTitle,
                    body = strings.usageGuideStepContextBody,
                    containerColor = guideStepColor,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.TaskAlt,
                    title = strings.usageGuideStepFollowTitle,
                    body = strings.usageGuideStepFollowBody,
                    containerColor = guideStepColor,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = if (needsConfiguration) onConfigure else onDismiss,
                border = BorderStroke(1.dp, guideBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = guidePrimaryTextColor,
                ),
            ) {
                Text(if (needsConfiguration) strings.startConfiguration else strings.startUsing)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = guideSecondaryTextColor,
                ),
            ) {
                Text(strings.later)
            }
        },
    )
}

@Composable
private fun UsageGuideStep(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    iconContainerColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                contentColor = secondaryTextColor,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                )
            }
        }
    }
}

private data class ConversationLayoutMetrics(
    val listPadding: PaddingValues,
    val itemSpacing: androidx.compose.ui.unit.Dp,
    val bubblePadding: androidx.compose.ui.unit.Dp,
    val bubbleShape: androidx.compose.ui.unit.Dp,
    val userBubbleWidth: Float,
    val assistantBubbleWidth: Float,
)

private fun conversationLayoutMetrics(layoutMode: String): ConversationLayoutMetrics {
    return when (layoutMode) {
        "compact" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            itemSpacing = 6.dp,
            bubblePadding = 10.dp,
            bubbleShape = 14.dp,
            userBubbleWidth = 0.92f,
            assistantBubbleWidth = 0.98f,
        )

        "spacious" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            itemSpacing = 14.dp,
            bubblePadding = 16.dp,
            bubbleShape = 24.dp,
            userBubbleWidth = 0.78f,
            assistantBubbleWidth = 0.88f,
        )

        else -> ConversationLayoutMetrics(
            listPadding = PaddingValues(16.dp),
            itemSpacing = 10.dp,
            bubblePadding = 14.dp,
            bubbleShape = 20.dp,
            userBubbleWidth = 0.86f,
            assistantBubbleWidth = 0.94f,
        )
    }
}

@Composable
fun Conversation(
    agent: Agent?,
    layoutMode: String = DEFAULT_APP_LAYOUT,
    emptyMessage: String = "创建或选择一个智能体开始。",
    onOpenPlan: (AgentMessage) -> Unit = {},
) {
    val metrics = conversationLayoutMetrics(layoutMode)
    val listState = rememberLazyListState()
    val messageCount = agent?.messages?.size ?: 0
    val lastMessage = agent?.messages?.lastOrNull()
    val lastMessageStreamMarker = lastMessage?.let { "${it.stableKey()}:${it.text.length}:${it.streaming}" }
    LaunchedEffect(agent?.id, messageCount, lastMessageStreamMarker) {
        if (messageCount <= 0) return@LaunchedEffect
        val lastListIndex = messageCount
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val shouldFollowOutput = visibleItems.isEmpty() || (visibleItems.lastOrNull()?.index ?: 0) >= lastListIndex - 1
        if (shouldFollowOutput) listState.scrollToItem(lastListIndex)
    }

    if (agent == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        contentPadding = metrics.listPadding,
    ) {
        item {
            ConversationStatusHeader(agent)
            if (!agent.activity.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(agent.activity, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(agent.messages, key = { message -> message.stableKey() }) { message ->
            MessageBubble(message, metrics, onOpenPlan = { onOpenPlan(message) })
        }
    }
}

@Composable
private fun ConversationStatusHeader(agent: Agent) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    agent.isBusy() -> MaterialTheme.colorScheme.primary
                    agent.status.equals("error", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(10.dp),
            ) {}
            Column(Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(agent.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    agentStatusLabel(agent),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun agentStatusLabel(agent: Agent): String {
    return when {
        agent.isBusy() && agent.activity?.contains("队列") == true -> "已排队"
        agent.isBusy() -> "正在运行中"
        agent.status.equals("ready", ignoreCase = true) -> "空闲"
        agent.status.equals("error", ignoreCase = true) -> "运行失败"
        agent.status.equals("stopped", ignoreCase = true) -> "已停止"
        agent.status.equals("resuming", ignoreCase = true) -> "正在恢复"
        agent.status.equals("可恢复", ignoreCase = true) -> "可恢复"
        else -> agent.status.ifBlank { "状态未知" }
    }
}

private fun AgentMessage.stableKey(): String {
    return itemId ?: "${timestamp}_${role}_${type}"
}

private data class MarkdownBlock(
    val text: String,
    val language: String? = null,
    val isCode: Boolean = false,
)

private data class DetailDisplay(
    val label: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: List<String> = emptyList(),
)

private data class FileChangeStats(
    val files: List<String>,
    val additions: Int,
    val deletions: Int,
)

private fun AgentMessage.isDetailMessage(): Boolean {
    return type == "command" ||
        type == "command_output" ||
        type == "file_change" ||
        type == "sub_agent" ||
        type == "tool" ||
        type == "tool_call"
}

private fun messageTypeLabel(type: String): String {
    return when (type) {
        "command" -> "命令"
        "command_output" -> "命令输出"
        "file_change" -> "文件改动"
        "sub_agent" -> "子代理"
        "plan" -> "计划"
        "thinking" -> "思考"
        "status" -> "状态"
        else -> type.replace('_', ' ')
    }
}

private fun AgentMessage.detailDisplay(): DetailDisplay {
    return when (type) {
        "file_change" -> fileChangeDisplay(text)
        "sub_agent" -> commandDisplay(text, isOutput = true).copy(label = "子代理")
        "command" -> commandDisplay(text, isOutput = false)
        "command_output" -> commandDisplay(text, isOutput = true)
        else -> DetailDisplay(messageTypeLabel(type), text.lineSequence().firstOrNull { it.isNotBlank() } ?: messageTypeLabel(type), "", text)
    }
}

private fun commandDisplay(raw: String, isOutput: Boolean): DetailDisplay {
    val lines = raw.lines()
    val status = lines.firstOrNull { it.startsWith("status:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        .orEmpty()
    val exit = lines.firstOrNull { it.startsWith("exit:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        .orEmpty()
    val duration = lines.firstOrNull { it.startsWith("duration:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.let(::formatDurationToken)
        .orEmpty()
    val command = lines.firstOrNull { line ->
        val trimmed = line.trim()
        trimmed.isNotBlank() &&
            !trimmed.startsWith("cwd:", ignoreCase = true) &&
            !trimmed.startsWith("status:", ignoreCase = true) &&
            !trimmed.startsWith("exit:", ignoreCase = true) &&
            !trimmed.startsWith("duration:", ignoreCase = true)
    }.orEmpty()
    val title = when {
        isOutput && duration.isNotBlank() -> "已处理 $duration"
        isOutput -> status.ifBlank { "已处理" }
        command.isNotBlank() -> command.trim()
        else -> "命令"
    }
    val subtitleParts = if (isOutput) {
        listOf(command.trim(), exit.takeIf { it.isNotBlank() }?.let { "exit $it" }.orEmpty(), status)
    } else {
        listOf(status.ifBlank { "已开始" }, duration)
    }
    val subtitle = subtitleParts.filter { it.isNotBlank() }.joinToString(" · ")
    return DetailDisplay(if (isOutput) "命令输出" else "命令", title, subtitle, raw)
}

private fun fileChangeDisplay(raw: String): DetailDisplay {
    val stats = fileChangeStats(raw)
    val paths = stats.files
    val status = raw.lineSequence()
        .firstOrNull { it.startsWith("status:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        .orEmpty()
    val title = when {
        paths.size == 1 -> paths.first().substringAfterLast('\\').substringAfterLast('/')
        paths.size > 1 -> "${paths.size} 个文件改动"
        else -> "文件改动"
    }
    val subtitle = listOf(
        status.ifBlank { "已处理" },
        if (stats.additions + stats.deletions > 0) "+${stats.additions} -${stats.deletions}" else "",
        paths.firstOrNull().orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return DetailDisplay("文件改动", title, subtitle, raw, stats.additions, stats.deletions, paths)
}

private fun fileChangeStats(raw: String): FileChangeStats {
    val paths = linkedSetOf<String>()
    var additions = 0
    var deletions = 0
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions += 1
            line.startsWith("-") && !line.startsWith("---") -> deletions += 1
        }
        val diffPath = Regex("^diff --git a/(.+?) b/(.+)$").find(trimmed)?.groupValues?.getOrNull(2)
        val newPath = Regex("^\\+\\+\\+ b/(.+)$").find(trimmed)?.groupValues?.getOrNull(1)
        val oldPath = Regex("^--- a/(.+)$").find(trimmed)?.groupValues?.getOrNull(1)
        val plainPath = trimmed.takeIf { value ->
            value.isNotBlank() &&
                !value.startsWith("@@") &&
                !value.startsWith("+") &&
                !value.startsWith("-") &&
                !value.startsWith("status:", ignoreCase = true) &&
                (value.contains("/") || value.contains("\\") || value.substringAfterLast('.').length in 1..5)
        }
        listOf(diffPath, newPath, oldPath, plainPath)
            .filterNotNull()
            .map { it.trim().removePrefix("a/").removePrefix("b/") }
            .filter { it.isNotBlank() && it != "/dev/null" }
            .forEach { paths.add(it) }
    }
    return FileChangeStats(paths.toList(), additions, deletions)
}

private fun formatDurationToken(raw: String): String {
    val value = raw.trim()
    val millis = value.removeSuffix("ms").toLongOrNull() ?: return value
    if (millis < 1000) return "${millis}ms"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val restSeconds = seconds % 60
    return if (minutes > 0) "${minutes}m ${restSeconds}s" else "${seconds}s"
}

private fun markdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return listOf(MarkdownBlock(""))
    val blocks = mutableListOf<MarkdownBlock>()
    val normal = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var language: String? = null
    text.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks.add(MarkdownBlock(code.toString().trimEnd(), language, isCode = true))
                code.clear()
                inCode = false
                language = null
            } else {
                if (normal.isNotBlank()) {
                    blocks.add(MarkdownBlock(normal.toString().trimEnd()))
                    normal.clear()
                }
                language = line.trim().removePrefix("```").takeIf { it.isNotBlank() }
                inCode = true
            }
            return@forEach
        }
        if (inCode) {
            code.appendLine(line)
        } else {
            normal.appendLine(line)
        }
    }
    if (inCode) blocks.add(MarkdownBlock(code.toString().trimEnd(), language, isCode = true))
    if (normal.isNotBlank()) blocks.add(MarkdownBlock(normal.toString().trimEnd()))
    return blocks.ifEmpty { listOf(MarkdownBlock(text)) }
}

@Composable
private fun MessageBubble(
    message: AgentMessage,
    metrics: ConversationLayoutMetrics = conversationLayoutMetrics(DEFAULT_APP_LAYOUT),
    onOpenPlan: () -> Unit = {},
) {
    val isUser = message.role == "user"
    val container = when {
        isUser -> MaterialTheme.colorScheme.surfaceContainerHighest
        message.type == "thinking" -> MaterialTheme.colorScheme.surfaceContainer
        message.type == "plan" -> MaterialTheme.colorScheme.surfaceContainer
        message.isDetailMessage() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) metrics.userBubbleWidth else metrics.assistantBubbleWidth),
            colors = CardDefaults.cardColors(containerColor = container),
            border = if (isUser || message.isDetailMessage() || message.type == "plan") {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else {
                null
            },
            shape = RoundedCornerShape(metrics.bubbleShape),
        ) {
            Column(Modifier.padding(metrics.bubblePadding)) {
                when {
                    message.type == "plan" -> PlanMessageCard(message, onOpenPlan)
                    message.isDetailMessage() -> DetailMessageCard(message)
                    else -> MarkdownMessageContent(message.text.ifBlank { "..." })
                }
            }
        }
    }
}

@Composable
private fun PlanMessageCard(message: AgentMessage, onOpenPlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "计划已生成",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            message.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "可以单独查看完整计划。",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
            Text("查看、优化或开始计划")
        }
    }
}

@Composable
private fun DetailMessageCard(message: AgentMessage) {
    var expanded by remember(message.stableKey()) { mutableStateOf(false) }
    val detail = remember(message.text, message.type) { message.detailDisplay() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (message.type == "file_change") {
                    FileChangeSummary(detail)
                    Spacer(Modifier.height(6.dp))
                } else {
                    Text(
                        detail.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    detail.title,
                    style = if (message.type == "file_change") MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (message.type == "file_change") 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.subtitle.isNotBlank()) {
                    Text(
                        detail.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起细节" else "展开细节",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    detail.body.ifBlank { "..." },
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun FileChangeSummary(detail: DetailDisplay) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            detail.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail.additions + detail.deletions > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "+${detail.additions}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "-${detail.deletions}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MarkdownMessageContent(text: String) {
    val blocks = remember(text) { markdownBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            if (block.isCode) {
                MarkdownCodeBlock(block)
            } else {
                block.text.lines().forEach { line ->
                    MarkdownTextLine(line)
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!block.language.isNullOrBlank()) {
                Text(
                    block.language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                block.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun MarkdownTextLine(line: String) {
    val trimmed = line.trimStart()
    val headingLevel = trimmed.takeWhile { it == '#' }.length.takeIf { it in 1..3 && trimmed.getOrNull(it) == ' ' } ?: 0
    val bulletPrefix = trimmed.startsWith("- ") || trimmed.startsWith("* ")
    val display = when {
        headingLevel > 0 -> trimmed.drop(headingLevel + 1)
        bulletPrefix -> "• " + trimmed.drop(2)
        else -> line
    }
    Text(
        markdownInline(display),
        style = when (headingLevel) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.bodyMedium
        },
        fontWeight = if (headingLevel > 0) FontWeight.SemiBold else null,
    )
}

@Composable
private fun markdownInline(text: String) = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceVariant,
    )
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Medium,
    )
    var index = 0
    fun appendPlainUntil(next: Int) {
        if (next > index) append(text.substring(index, next))
        index = next
    }
    while (index < text.length) {
        val codeStart = text.indexOf('`', index).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val boldStart = text.indexOf("**", index).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val linkStart = text.indexOf('[', index).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val next = minOf(codeStart, boldStart, linkStart)
        if (next == Int.MAX_VALUE) {
            append(text.substring(index))
            break
        }
        appendPlainUntil(next)
        when (next) {
            codeStart -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(codeStyle) { append(text.substring(index + 1, end)) }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            boldStart -> {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(index + 2, end)) }
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }
            linkStart -> {
                val close = text.indexOf(']', index + 1)
                val openParen = if (close >= 0) text.indexOf('(', close + 1) else -1
                val closeParen = if (openParen == close + 1) text.indexOf(')', openParen + 1) else -1
                if (close > index && closeParen > openParen) {
                    withStyle(linkStyle) { append(text.substring(index + 1, close)) }
                    index = closeParen + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
        }
    }
}

@Composable
fun MessageComposer(
    text: String,
    enabled: Boolean,
    agent: Agent?,
    projectOptions: List<String>,
    canChangeProject: Boolean,
    modelOptions: List<CodexModelOption>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    runtimeCapabilities: RuntimeCapabilities,
    planModeEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onPlanModeChange: (Boolean) -> Unit,
    onProjectChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onAttachFiles: () -> Unit,
    onAttachImages: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onSend: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var activePanel by remember { mutableStateOf(ComposerPanel.None) }
    var sendAnimationKey by remember { mutableStateOf(0) }
    val sendButtonScale = remember { Animatable(1f) }
    val sendIconTravel = remember { Animatable(0f) }

    LaunchedEffect(sendAnimationKey) {
        if (sendAnimationKey == 0) return@LaunchedEffect
        launch {
            sendButtonScale.snapTo(0.88f)
            sendButtonScale.animateTo(
                targetValue = 1.08f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
            )
            sendButtonScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
        launch {
            sendIconTravel.snapTo(0f)
            sendIconTravel.animateTo(
                targetValue = 18f,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            )
            sendIconTravel.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AgentRuntimeBar(
                agent = agent,
                enabled = enabled,
                projectOptions = projectOptions,
                canChangeProject = false,
                modelOptions = modelOptions,
                reasoningOptions = reasoningOptions,
                serviceTierOptions = serviceTierOptions,
                runtimeCapabilities = runtimeCapabilities,
                showProject = false,
                showRuntime = true,
                onProjectChange = onProjectChange,
                onModelChange = onModelChange,
                onReasoningEffortChange = onReasoningEffortChange,
                onServiceTierChange = onServiceTierChange,
                onBrowseDirectories = onBrowseDirectories,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedComposerIconButton(
                    selected = activePanel == ComposerPanel.Tools,
                    onClick = { activePanel = activePanel.toggle(ComposerPanel.Tools) },
                    enabled = enabled,
                    rotationWhenSelected = 45f,
                ) { iconModifier ->
                    Icon(
                        Icons.Default.Add,
                        contentDescription = strings.openAttachmentPanel,
                        modifier = iconModifier,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(strings.sendToEasyCodex) },
                    trailingIcon = {
                        AnimatedComposerIconButton(
                            selected = false,
                            onClick = onVoiceInput,
                            enabled = enabled,
                        ) { iconModifier ->
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = strings.openVoicePanel,
                                modifier = iconModifier,
                            )
                        }
                    },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(26.dp),
                )
                FilledTonalButton(
                    onClick = {
                        activePanel = ComposerPanel.None
                        sendAnimationKey += 1
                        onSend()
                    },
                    enabled = enabled && text.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .scale(sendButtonScale.value),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = strings.send,
                        modifier = Modifier.graphicsLayer(
                            translationX = sendIconTravel.value,
                            translationY = -sendIconTravel.value * 0.35f,
                            rotationZ = sendIconTravel.value * 1.4f,
                            alpha = (1f - sendIconTravel.value / 60f).coerceIn(0.72f, 1f),
                        ),
                    )
                }
            }
            AnimatedVisibility(
                visible = activePanel != ComposerPanel.None,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 140)) + slideInVertically { it / 5 },
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 100)) + slideOutVertically { it / 6 },
            ) {
                ComposerContextPanel(
                    enabled = enabled,
                    agent = agent,
                    projectOptions = projectOptions,
                    canChangeProject = canChangeProject,
                    modelOptions = modelOptions,
                    reasoningOptions = reasoningOptions,
                    serviceTierOptions = serviceTierOptions,
                    runtimeCapabilities = runtimeCapabilities,
                    planModeEnabled = planModeEnabled,
                    onPlanModeChange = onPlanModeChange,
                    onProjectChange = onProjectChange,
                    onModelChange = onModelChange,
                    onReasoningEffortChange = onReasoningEffortChange,
                    onServiceTierChange = onServiceTierChange,
                    onBrowseDirectories = onBrowseDirectories,
                    onAttachFiles = {
                        activePanel = ComposerPanel.None
                        onAttachFiles()
                    },
                    onAttachImages = {
                        activePanel = ComposerPanel.None
                        onAttachImages()
                    },
                )
            }
        }
    }
}

@Composable
private fun ComposerContextPanel(
    enabled: Boolean,
    agent: Agent?,
    projectOptions: List<String>,
    canChangeProject: Boolean,
    modelOptions: List<CodexModelOption>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    runtimeCapabilities: RuntimeCapabilities,
    planModeEnabled: Boolean,
    onPlanModeChange: (Boolean) -> Unit,
    onProjectChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onAttachFiles: () -> Unit,
    onAttachImages: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = EasyCodexDesign.ComposerPanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("计划模式", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (planModeEnabled) "先整理计划，再等待你确认" else "直接把消息发送给 EasyCodex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = planModeEnabled, onCheckedChange = onPlanModeChange, enabled = enabled)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AgentRuntimeBar(
                agent = agent,
                enabled = enabled,
                projectOptions = projectOptions,
                canChangeProject = canChangeProject,
                modelOptions = modelOptions,
                reasoningOptions = reasoningOptions,
                serviceTierOptions = serviceTierOptions,
                runtimeCapabilities = runtimeCapabilities,
                showProject = true,
                showRuntime = false,
                onProjectChange = onProjectChange,
                onModelChange = onModelChange,
                onReasoningEffortChange = onReasoningEffortChange,
                onServiceTierChange = onServiceTierChange,
                onBrowseDirectories = onBrowseDirectories,
            )
            ComposerToolPanel(
                enabled = enabled,
                onAttachFiles = onAttachFiles,
                onAttachImages = onAttachImages,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentRuntimeBar(
    agent: Agent?,
    enabled: Boolean,
    projectOptions: List<String>,
    canChangeProject: Boolean,
    modelOptions: List<CodexModelOption>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    runtimeCapabilities: RuntimeCapabilities,
    showProject: Boolean = true,
    showRuntime: Boolean = true,
    onProjectChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
) {
    val strings = LocalAppStrings.current
    var picker by remember { mutableStateOf<RuntimePicker?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    val selectedModel = agent?.model.orEmpty()
    val selectedModelLabel = modelOptions.firstOrNull { it.model == selectedModel }?.displayName
        ?: selectedModel.ifBlank { strings.detectingModel }
    val selectedProject = agent?.cwd.orEmpty()
    val selectedProjectLabel = cleanNullablePath(selectedProject)?.let { projectNameFromCwd(it) } ?: strings.selectProject
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showProject && selectedProject.isNotBlank() && canChangeProject) {
            FilterChip(
                selected = false,
                enabled = enabled,
                onClick = { showProjectPicker = true },
                label = {
                    Text(
                        "${strings.project} $selectedProjectLabel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (showRuntime) {
            FilterChip(
                selected = false,
                enabled = enabled && modelOptions.isNotEmpty(),
                onClick = { picker = RuntimePicker.Model },
                label = { Text(selectedModelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        if (showRuntime && runtimeCapabilities.supportsReasoningEffort) {
            FilterChip(
                selected = false,
                enabled = enabled && reasoningOptions.isNotEmpty(),
                onClick = { picker = RuntimePicker.Reasoning },
                label = { Text(reasoningLabel(agent?.reasoningEffort ?: DEFAULT_REASONING_EFFORT, strings)) },
            )
        }
        if (showRuntime && runtimeCapabilities.supportsServiceTier && serviceTierOptions.isNotEmpty()) {
            FilterChip(
                selected = false,
                enabled = enabled,
                onClick = { picker = RuntimePicker.ServiceTier },
                label = { Text(serviceTierLabel(agent?.serviceTier ?: DEFAULT_SERVICE_TIER, strings)) },
            )
        }
    }

    if (showProjectPicker) {
        DirectoryPickerDialog(
            initialPath = selectedProject,
            pinnedPaths = projectOptions,
            onBrowseDirectories = onBrowseDirectories,
            onSelect = {
                onProjectChange(it)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false },
        )
    }

    when (picker) {
        RuntimePicker.Model -> RuntimeChoiceDialog(
            title = strings.chooseModel,
            options = modelOptions.map { RuntimeChoice(it.model, it.displayName, it.model) },
            selected = selectedModel,
            onSelect = {
                onModelChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Reasoning -> RuntimeChoiceDialog(
            title = strings.chooseReasoning,
            options = reasoningOptions.map { RuntimeChoice(it, reasoningLabel(it, strings), reasoningDescription(it, strings)) },
            selected = agent?.reasoningEffort ?: DEFAULT_REASONING_EFFORT,
            onSelect = {
                onReasoningEffortChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.ServiceTier -> RuntimeChoiceDialog(
            title = strings.chooseSpeed,
            options = serviceTierOptions.map { RuntimeChoice(it, serviceTierLabel(it, strings), serviceTierDescription(it, strings)) },
            selected = agent?.serviceTier ?: DEFAULT_SERVICE_TIER,
            onSelect = {
                onServiceTierChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Project, null -> Unit
    }
}

private enum class RuntimePicker {
    Project,
    Model,
    Reasoning,
    ServiceTier,
}

private data class RuntimeChoice(val value: String, val label: String, val description: String = "")

@Composable
private fun RuntimeChoiceDialog(
    title: String,
    options: List<RuntimeChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (option.value == selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(option.value) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (option.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

private fun reasoningLabel(value: String, strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE)): String {
    return when (value) {
        "low" -> strings.low
        "medium" -> strings.medium
        "high" -> strings.high
        "xhigh" -> strings.xhigh
        else -> value
    }
}

private fun reasoningDescription(value: String, strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE)): String {
    return when (value) {
        "low" -> strings.reasoningLowDescription
        "medium" -> strings.reasoningMediumDescription
        "high" -> strings.reasoningHighDescription
        "xhigh" -> strings.reasoningXHighDescription
        else -> ""
    }
}

private fun serviceTierLabel(value: String, strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE)): String {
    return when (value) {
        "fast" -> strings.fast
        "flex" -> strings.flex
        "default" -> strings.serviceDefault
        "standard" -> strings.serviceDefault
        else -> value
    }
}

private fun serviceTierDescription(value: String, strings: AppStrings = appStringsFor(DEFAULT_APP_LANGUAGE)): String {
    return when (value) {
        "fast" -> strings.fastDescription
        "flex" -> strings.flexDescription
        "default" -> strings.defaultSpeedDescription
        "standard" -> strings.defaultSpeedDescription
        else -> ""
    }
}

private fun normalizeServiceTier(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when {
        normalized.isBlank() -> DEFAULT_SERVICE_TIER
        normalized == "auto" -> DEFAULT_SERVICE_TIER
        normalized == "standard" -> "default"
        normalized == "flex" -> "default"
        else -> normalized
    }
}

private enum class ComposerPanel {
    None,
    Tools,
    Emoji,
    Voice,
}

private fun ComposerPanel.toggle(panel: ComposerPanel): ComposerPanel {
    return if (this == panel) ComposerPanel.None else panel
}

@Composable
private fun AnimatedComposerIconButton(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    rotationWhenSelected: Float = 0f,
    icon: @Composable (Modifier) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "composer icon container color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "composer icon content color",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "composer icon scale",
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (selected) rotationWhenSelected else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "composer icon rotation",
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        icon(
            Modifier
                .scale(iconScale)
                .graphicsLayer(rotationZ = iconRotation),
        )
    }
}

@Composable
private fun ComposerToolPanel(
    enabled: Boolean,
    onAttachFiles: () -> Unit,
    onAttachImages: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ComposerToolItem(
            icon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
            label = strings.file,
            enabled = enabled,
            onClick = onAttachFiles,
        )
        ComposerToolItem(
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            label = strings.image,
            enabled = enabled,
            onClick = onAttachImages,
        )
    }
}

@Composable
private fun ComposerToolItem(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmojiPanel(
    emoji: List<String>,
    enabled: Boolean,
    onInsertEmoji: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    var page by remember { mutableStateOf(0) }
    val pageCount = remember(emoji) {
        ((emoji.size + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE).coerceAtLeast(1)
    }
    val pageItems = remember(emoji, page) {
        val start = page * EMOJI_PAGE_SIZE
        emoji.drop(start).take(EMOJI_PAGE_SIZE)
    }

    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(EMOJI_ROWS) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(EMOJI_COLUMNS) { column ->
                    val item = pageItems.getOrNull(row * EMOJI_COLUMNS + column)
                    EmojiCell(
                        item = item,
                        enabled = enabled,
                        onInsertEmoji = onInsertEmoji,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = { page = (page - 1).coerceAtLeast(0) },
                enabled = enabled && page > 0,
            ) {
                Text(strings.previousPage)
            }
            Text(
                "${page + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { page = (page + 1).coerceAtMost(pageCount - 1) },
                enabled = enabled && page < pageCount - 1,
            ) {
                Text(strings.nextPage)
            }
        }
    }
}

@Composable
private fun EmojiCell(
    item: String?,
    enabled: Boolean,
    onInsertEmoji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tapKey by remember { mutableStateOf(0) }
    val emojiScale = remember { Animatable(1f) }
    val highlightAlpha = remember { Animatable(0f) }
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer

    LaunchedEffect(tapKey) {
        if (tapKey == 0) return@LaunchedEffect
        launch {
            emojiScale.snapTo(0.78f)
            emojiScale.animateTo(
                targetValue = 1.18f,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
            )
            emojiScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
        launch {
            highlightAlpha.snapTo(0.58f)
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled && item != null) {
                item?.let {
                    tapKey += 1
                    onInsertEmoji(it)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = highlightColor.copy(alpha = highlightAlpha.value),
            shape = CircleShape,
            modifier = Modifier.fillMaxSize(),
        ) {}
        if (item != null) {
            Text(
                text = item,
                modifier = Modifier.scale(emojiScale.value),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
            )
        }
    }
}

@Composable
private fun VoicePanel(
    enabled: Boolean,
    onVoiceInput: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onVoiceInput,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = strings.startVoiceInput,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            strings.tapToStartVoiceInput,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CreateAgentDialog(
    busy: Boolean,
    initialModel: String,
    initialCwd: String,
    modelOptions: List<CodexModelOption>,
    reasoningOptions: List<String>,
    runtimeCapabilities: RuntimeCapabilities,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("EasyCodex") }
    var model by remember { mutableStateOf(initialModel.ifBlank { DEFAULT_AGENT_MODEL }) }
    val initialReasoningEffort = modelOptions.firstOrNull { it.model == initialModel }?.defaultReasoningEffort
        ?.ifBlank { DEFAULT_REASONING_EFFORT }
        ?: DEFAULT_REASONING_EFFORT
    var reasoningEffort by remember { mutableStateOf(initialReasoningEffort) }
    var cwd by remember { mutableStateOf(initialCwd.ifBlank { DEFAULT_AGENT_CWD }) }
    val models = modelOptions.ifEmpty {
        listOf(CodexModelOption(model = model, displayName = model))
    }
    val currentReasoningOptions = models.firstOrNull { it.model == model }?.supportedReasoningEfforts
        ?.takeIf { it.isNotEmpty() }
        ?: reasoningOptions
    var picker by remember { mutableStateOf<RuntimePicker?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.createEasyCodexSession) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.name) }, singleLine = true)
                FilterChip(
                    selected = false,
                    onClick = { picker = RuntimePicker.Model },
                    label = {
                        Text(
                            "${strings.model} ${models.firstOrNull { it.model == model }?.displayName ?: model}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                if (runtimeCapabilities.supportsReasoningEffort) {
                    FilterChip(
                        selected = false,
                        onClick = { if (currentReasoningOptions.isNotEmpty()) picker = RuntimePicker.Reasoning },
                        label = { Text("${strings.reasoningEffort} ${reasoningLabel(reasoningEffort, strings)}") },
                    )
                }
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text(strings.projectPathDesktop) },
                    supportingText = { Text(strings.defaultProjectPathHelp) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, model, cwd, reasoningEffort) }, enabled = !busy) {
                Text(if (busy) strings.creating else strings.create)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
    when (picker) {
        RuntimePicker.Model -> RuntimeChoiceDialog(
            title = strings.chooseModel,
            options = models.map { RuntimeChoice(it.model, it.displayName, it.model) },
            selected = model,
            onSelect = {
                model = it
                val nextReasoningOptions = models.firstOrNull { option -> option.model == it }?.supportedReasoningEfforts.orEmpty()
                if (nextReasoningOptions.isNotEmpty() && reasoningEffort !in nextReasoningOptions) {
                    reasoningEffort = models.firstOrNull { option -> option.model == it }?.defaultReasoningEffort
                        ?.takeIf { effort -> effort in nextReasoningOptions }
                        ?: nextReasoningOptions.first()
                }
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Reasoning -> RuntimeChoiceDialog(
            title = strings.chooseReasoning,
            options = currentReasoningOptions.map { RuntimeChoice(it, reasoningLabel(it, strings), reasoningDescription(it, strings)) },
            selected = reasoningEffort,
            onSelect = {
                reasoningEffort = it
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Project, RuntimePicker.ServiceTier, null -> Unit
    }
}
