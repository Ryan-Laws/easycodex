package com.easycodex.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val CONNECTION_STATUS_CHANNEL_ID = "easycodex-connection-status"
private const val CONNECTION_STATUS_NOTIFICATION_ID = 72001
private const val MAX_MOBILE_MESSAGE_TEXT_CHARS = 20_000
private const val MAX_MOBILE_DETAIL_TEXT_CHARS = 12_000
private const val MOBILE_TEXT_TRUNCATED_NOTICE = "\n\n[EasyCodex mobile truncated this long output. Use the desktop relay/Codex session for the full text.]"
private const val PLAN_MODE_PREFIX_FOR_DISPLAY = "请先进入计划模式处理下面的需求。"
private const val PLAN_MODE_DEMAND_MARKER_FOR_DISPLAY = "需求："
private const val CONTEXT_PLACEHOLDER_FOR_DISPLAY = "已加载项目上下文。"
private const val USER_INPUT_REQUEST_METHOD = "item/tool/requestUserInput"
private const val CODEX_SESSION_USER_INPUT_PREFIX = "codex_user_input_"
private const val WORKING_STATUS_DOWNGRADE_GRACE_MS = 4_500L
private const val RECENT_UNREAD_TASK_WINDOW_MS = 2 * 60 * 60 * 1000L
private const val PROPOSED_PLAN_OPEN_TAG = "<proposed_plan>"
private const val PROPOSED_PLAN_CLOSE_TAG = "</proposed_plan>"
private val PENDING_CODEX_THREAD_NAMES = setOf(
    "easy codex",
    "easycodex",
    "easycodex task",
    "easycodex 任务",
    "null",
    "new",
    "new task",
    "untitled",
    "untitled task",
    "获取到任务",
    "正在获取中",
    "新任务",
    "未命名任务",
)

internal fun isPendingCodexThreadName(name: String): Boolean {
    return name.trim().lowercase(Locale.ROOT) in PENDING_CODEX_THREAD_NAMES
}

internal fun displayTaskNameForMobile(name: String?, fallback: String): String {
    val cleaned = name?.trim().orEmpty()
    return if (cleaned.isBlank() || isPendingCodexThreadName(cleaned)) {
        fallback.trim().takeIf { it.isNotBlank() } ?: "EasyCodex 任务"
    } else {
        cleaned
    }
}

internal fun isAttachmentSizeOverLimit(size: Long?, maxBytes: Int = MAX_ATTACHMENT_BYTES): Boolean {
    return size != null && size > maxBytes
}

internal fun shouldQueueFollowUpOnCurrentAgent(agent: Agent): Boolean {
    return agent.isBusy()
}

internal fun userInputRequestQuestionText(params: JSONObject?): String {
    val questions = params?.optJSONArray("questions") ?: return params?.optString("message").orEmpty()
    return formatUserInputQuestionText(
        buildList {
            for (index in 0 until questions.length()) {
                val question = questions.optJSONObject(index) ?: continue
                add(question.optString("header") to question.optString("question"))
            }
        },
    )
}

internal fun formatUserInputQuestionText(questions: List<Pair<String, String>>): String {
    return buildList {
        for ((header, prompt) in questions) {
            val line = listOf(header, prompt).filter { it.isNotBlank() }.joinToString("：")
            if (line.isNotBlank()) add(line)
        }
    }.joinToString("\n")
}

internal fun planStartAnswersForRequest(request: AgentUserInputRequest): Map<String, String>? {
    if (request.questions.isEmpty()) return null
    val answers = mutableMapOf<String, String>()
    for (question in request.questions) {
        val answer = planStartAnswerForQuestion(question) ?: return null
        answers[question.id] = answer
    }
    return answers.takeIf { it.isNotEmpty() }
}

internal fun planStartAnswerForQuestion(question: AgentUserInputQuestion): String? {
    val preferredLabels = listOf(
        PLAN_START_PROMPT,
        "开始任务",
        "开始计划",
        "开始执行",
    )
    for (preferred in preferredLabels) {
        question.options.firstOrNull { it.label.trim() == preferred }?.let { return it.label }
    }
    question.options.firstOrNull { option ->
        val label = option.label.trim()
        label.contains("开始任务") ||
            label.contains("开始计划") ||
            label.contains("开始执行") ||
            label.contains("start", ignoreCase = true)
    }?.let { return it.label }

    val promptText = listOf(question.header, question.question).joinToString("\n")
    val asksToStart = promptText.contains("开始") || promptText.contains("start", ignoreCase = true)
    return if (question.options.isEmpty() && asksToStart) PLAN_START_PROMPT else null
}

internal fun readAttachmentBytesWithinLimit(input: InputStream, maxBytes: Int = MAX_ATTACHMENT_BYTES): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        totalBytes += read
        if (totalBytes > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal fun isCompletePlanText(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    val normalized = trimmed.lowercase(Locale.ROOT)
    val incompleteMarkers = listOf(
        "计划内容为空",
        "详细内容已省略",
        "内容已省略",
        "plan updated.",
        "执行计划已更新。",
        "正在规划",
        "准备继续执行",
        "easycodex mobile truncated",
    )
    if (incompleteMarkers.any { it in normalized }) return false

    val openIndex = normalized.indexOf(PROPOSED_PLAN_OPEN_TAG)
    val closeIndex = normalized.indexOf(PROPOSED_PLAN_CLOSE_TAG)
    if (openIndex >= 0 || closeIndex >= 0) {
        if (openIndex < 0 || closeIndex <= openIndex + PROPOSED_PLAN_OPEN_TAG.length) return false
        return trimmed.substring(
            openIndex + PROPOSED_PLAN_OPEN_TAG.length,
            closeIndex,
        ).isNotBlank()
    }

    return trimmed.lineSequence().any { line ->
        val value = line.trimStart()
        value.startsWith("- [ ] ") ||
            value.startsWith("- [x] ", ignoreCase = true) ||
            value.startsWith("- [~] ")
    } || hasStructuredPlanSections(trimmed)
}

private fun hasStructuredPlanSections(text: String): Boolean {
    var sectionCount = 0
    var hasListItem = false
    val planSectionNames = setOf(
        "summary",
        "implementation plan",
        "test plan",
        "validation",
        "risks",
        "assumptions",
        "open questions",
        "questions",
        "计划",
        "实施计划",
        "测试计划",
        "验证",
        "风险",
        "假设",
        "需要确认",
        "问题",
    )
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
            .trimEnd(':', '：')
            .replace(Regex("""^#+\s*"""), "")
            .trim()
            .trim('*')
            .trim()
        if (line.isBlank()) continue
        val lower = line.lowercase(Locale.ROOT)
        if (lower in planSectionNames) sectionCount += 1
        if (
            line.startsWith("- ") ||
            line.startsWith("* ") ||
            line.startsWith("• ") ||
            line.matches(Regex("""\d+[.)]\s+.+"""))
        ) {
            hasListItem = true
        }
    }
    return sectionCount >= 2 && hasListItem
}

internal fun proposedPlanBody(text: String): String? {
    val normalized = text.lowercase(Locale.ROOT)
    val openIndex = normalized.indexOf(PROPOSED_PLAN_OPEN_TAG)
    val closeIndex = normalized.indexOf(PROPOSED_PLAN_CLOSE_TAG)
    if (openIndex < 0 || closeIndex <= openIndex + PROPOSED_PLAN_OPEN_TAG.length) return null
    return text.substring(openIndex + PROPOSED_PLAN_OPEN_TAG.length, closeIndex).trim().takeIf { it.isNotBlank() }
}

internal fun planDisplayText(text: String): String {
    return proposedPlanBody(text) ?: text.trim()
}

internal fun normalizedAgentMessageType(role: String, type: String, text: String): String {
    val normalized = when (type) {
        "commandOutput" -> "command_output"
        "fileChange" -> "file_change"
        "subAgent", "subAgentOutput" -> "sub_agent"
        "testResult" -> "test_result"
        "pluginActivity" -> "plugin_activity"
        else -> type
    }
    return if (normalized == "agent" && role == "agent" && proposedPlanBody(text) != null) {
        "plan"
    } else {
        normalized
    }
}

internal fun isActionablePlanMessage(message: AgentMessage): Boolean {
    return normalizedAgentMessageType(message.role, message.type, message.text) == "plan" &&
        !message.streaming &&
        isCompletePlanText(message.text)
}

internal fun pendingRequestIdsFromAgentJson(agentJson: JSONObject): Set<String> {
    val pending = agentJson.optJSONArray("pendingRequests") ?: return emptySet()
    return buildSet {
        for (index in 0 until pending.length()) {
            val requestId = pending.optJSONObject(index)?.optString("requestId").orEmpty()
            if (requestId.isNotBlank()) add(requestId)
        }
    }
}

internal data class CliCommandDraft(
    val mode: String,
    val sessionId: String,
    val reviewTarget: String,
    val profile: String,
    val images: List<String>,
    val addDirs: List<String>,
    val jsonOutput: Boolean,
    val ephemeral: Boolean,
    val ignoreRules: Boolean,
    val prompt: String,
)

internal fun splitCliInput(raw: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        val next = raw.getOrNull(index + 1)
        when {
            char == '\\' && quote != null && (next == quote || next == '\\') -> {
                current.append(next)
                index += 1
            }
            quote != null && char == quote -> quote = null
            quote == null && (char == '"' || char == '\'') -> quote = char
            quote == null && char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            }
            else -> current.append(char)
        }
        index += 1
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

internal fun parseCliCommand(window: CliConsoleWindow): CliCommandDraft {
    val raw = window.input.trim()
    if (!raw.startsWith("/")) {
        return CliCommandDraft(
            mode = window.mode.ifBlank { "exec" },
            sessionId = window.sessionId.trim(),
            reviewTarget = window.reviewTarget.trim(),
            profile = window.profile.trim(),
            images = window.images,
            addDirs = window.addDirs,
            jsonOutput = window.jsonOutput,
            ephemeral = window.ephemeral,
            ignoreRules = window.ignoreRules,
            prompt = raw,
        )
    }
    val tokens = splitCliInput(raw)
    var mode = "exec"
    var sessionId = window.sessionId.trim()
    var reviewTarget = window.reviewTarget.trim()
    var profile = window.profile.trim()
    val images = window.images.toMutableList()
    val addDirs = window.addDirs.toMutableList()
    var jsonOutput = window.jsonOutput
    var ephemeral = window.ephemeral
    var ignoreRules = window.ignoreRules
    val promptParts = mutableListOf<String>()
    var index = 0
    while (index < tokens.size) {
        when (val token = tokens[index]) {
            "/resume" -> {
                mode = "resume"
                val next = tokens.getOrNull(index + 1)
                if (next != null && !next.startsWith("/") && !next.startsWith("--")) {
                    sessionId = next
                    index += 1
                } else if (sessionId.isBlank()) {
                    sessionId = "last"
                }
            }
            "/review" -> {
                mode = "review"
                if (reviewTarget.isBlank()) reviewTarget = "uncommitted"
            }
            "/json" -> jsonOutput = true
            "/ephemeral" -> ephemeral = true
            "/ignore-rules" -> ignoreRules = true
            "/profile" -> {
                tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("/") && !it.startsWith("--") }?.let {
                    profile = it
                    index += 1
                }
            }
            "/image" -> {
                tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("/") && !it.startsWith("--") }?.let {
                    images.add(it)
                    index += 1
                }
            }
            "/add-dir" -> {
                tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("/") && !it.startsWith("--") }?.let {
                    addDirs.add(it)
                    index += 1
                }
            }
            "--last" -> sessionId = "last"
            "--uncommitted" -> reviewTarget = "uncommitted"
            "--base" -> {
                tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("/") && !it.startsWith("--") }?.let {
                    reviewTarget = "base:$it"
                    index += 1
                }
            }
            "--commit" -> {
                tokens.getOrNull(index + 1)?.takeIf { !it.startsWith("/") && !it.startsWith("--") }?.let {
                    reviewTarget = "commit:$it"
                    index += 1
                }
            }
            else -> promptParts.add(token)
        }
        index += 1
    }
    return CliCommandDraft(
        mode = mode,
        sessionId = sessionId,
        reviewTarget = reviewTarget,
        profile = profile,
        images = images.distinct().take(12),
        addDirs = addDirs.distinct().take(12),
        jsonOutput = jsonOutput,
        ephemeral = ephemeral,
        ignoreRules = ignoreRules,
        prompt = promptParts.joinToString(" "),
    )
}

internal fun parseGitStatusSummary(json: JSONObject): GitStatusSummary {
    fun readPathArray(name: String): List<String> {
        val array = json.optJSONArray(name) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                val value = when (item) {
                    is JSONObject -> item.optString("to")
                        .ifBlank { item.optString("path") }
                        .ifBlank { item.optString("file") }
                    else -> array.optString(index)
                }.takeIf { it.isNotBlank() } ?: continue
                add(value)
            }
        }
    }
    val files = listOf("modified", "created", "deleted", "renamed", "notAdded", "conflicted")
        .flatMap(::readPathArray)
        .distinct()
    val restorableFiles = readPathArray("restorableFiles")
        .filter { it in files }
        .distinct()
        .ifEmpty {
            listOf("modified", "created", "deleted", "renamed", "conflicted")
                .flatMap(::readPathArray)
                .distinct()
        }
    return GitStatusSummary(
        branch = json.optString("branch"),
        isClean = json.optBoolean("isClean", files.isEmpty()),
        files = files,
        restorableFiles = restorableFiles,
    )
}

internal fun parseHostHealthState(json: JSONObject, checkedAt: Long): HostHealthState {
    val system = json.optJSONObject("system") ?: JSONObject()
    val runtime = json.optJSONObject("runtime") ?: JSONObject()
    val warnings = buildList {
        val array = json.optJSONArray("warnings") ?: JSONArray()
        for (index in 0 until array.length()) {
            val warning = array.optJSONObject(index)
            val text = listOfNotNull(
                warning?.optString("message")?.takeIf { it.isNotBlank() },
                warning?.optString("recommendation")?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            if (text.isNotBlank()) add(text)
        }
    }
    return HostHealthState(
        online = json.optString("status") == "ok",
        hostname = system.optString("hostname"),
        platform = system.optString("platform"),
        workspaceRoot = json.optString("workspaceRoot"),
        uptimeMs = json.optLong("uptimeMs", 0L),
        connectedClients = json.optInt("connectedClients", 0),
        runtimeMode = runtime.optString("providerMode", runtime.optString("reason")),
        warnings = warnings,
        checkedAt = checkedAt,
    )
}

class EasyCodexController(private val context: android.content.Context) {
    interface ConnectionStateListener {
        fun onConnectionStateChanged(status: String, text: String)
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, (JSONObject?, String?) -> Unit>()
    private val connectionStateListeners = CopyOnWriteArraySet<ConnectionStateListener>()
    private var webSocket: WebSocket? = null
    private var requestCounter = 0
    private var connectionGeneration = 0
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var manuallyDisconnected = false
    private var agentsRefreshInFlight = false
    private var agentsRefreshQueued = false
    private var agentsRefreshRunnable: Runnable? = null
    private var appInForeground = true
    private val prefs = context.getSharedPreferences("easycodex", android.content.Context.MODE_PRIVATE)
        .also(::applyDaylightThemeDefault)
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
    private var lastStreamSessionId = prefs.getString(PREF_LAST_STREAM_SESSION_ID, "") ?: ""
    private var lastStreamSeq = prefs.getLong(PREF_LAST_STREAM_SEQ, 0L)
    private val streamingAgentIds = mutableSetOf<String>()
    private val lastActivityUpdateAt = ConcurrentHashMap<String, Long>()
    private val pendingStreamDeltas = linkedMapOf<String, PendingStreamDelta>()
    private val pendingCliOutputs = linkedMapOf<String, PendingCliOutput>()
    private val fileChangeLiveStats = ConcurrentHashMap<String, FileChangeLiveStat>()
    private val commandOutputLiveStats = ConcurrentHashMap<String, CommandOutputLiveStat>()
    private val pendingPlanReviewCandidates = ConcurrentHashMap<String, PlanReviewCandidate>()
    private val activeTurnSerials = ConcurrentHashMap<String, Long>()
    private val completedTurnSerials = ConcurrentHashMap<String, Long>()
    private val activeTurnStartedAt = ConcurrentHashMap<String, Long>()
    private val autoPromptedPlanKeys = mutableSetOf<String>()
    private var streamDeltaFlushRunnable: Runnable? = null
    private var cliOutputFlushRunnable: Runnable? = null
    private var relayDetailRefreshRunnable: Runnable? = null
    private var threadDetailRequestCounter = 0
    private val latestThreadDetailRequests = ConcurrentHashMap<String, Int>()
    private val detailedCodexThreads = ConcurrentHashMap<String, Agent>()
    private var hiddenTaskIds = prefs.getStringSet(PREF_HIDDEN_TASK_IDS, emptySet())
        ?.toMutableSet()
        ?: mutableSetOf()
    private var readTaskKeys = prefs.getStringSet(PREF_READ_TASK_KEYS, emptySet())
        ?.toMutableSet()
        ?: mutableSetOf()
    private val threadDetailsInFlight = mutableSetOf<String>()
    private val threadDetailRetryCounts = ConcurrentHashMap<String, Int>()
    private val threadDetailRetryRunnables = ConcurrentHashMap<String, Runnable>()
    private val pendingTimeoutRunnables = ConcurrentHashMap<String, Runnable>()
    private var diffReviewRequestCounter = 0
    private val pendingOutboundMessages = ConcurrentHashMap<String, MutableList<AgentMessage>>()
    private val pendingAgentStatusIds = ConcurrentHashMap<String, String>()
    private val pendingQuickReplies = mutableListOf<PendingQuickReply>()
    private val pendingApprovalResponses = mutableListOf<PendingApprovalResponse>()

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
    private var connectionStatusState by mutableStateOf("disconnected")
    var connectionStatus: String
        get() = connectionStatusState
        private set(value) {
            if (connectionStatusState == value) return
            connectionStatusState = value
            notifyConnectionStateChanged()
        }
    private var statusTextState by mutableStateOf(appStringsFor(appLanguage).disconnected)
    var statusText: String
        get() = statusTextState
        set(value) {
            if (statusTextState == value) return
            statusTextState = value
            notifyConnectionStateChanged()
        }
    var activeAgentId by mutableStateOf<String?>(null)
    var draftProjectCwd by mutableStateOf<String?>(null)
    var draftProjectLocked by mutableStateOf(false)
    var draftModel by mutableStateOf(defaultModel.ifBlank { DEFAULT_AGENT_MODEL })
    var draftReasoningEffort by mutableStateOf(defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT })
    var draftServiceTier by mutableStateOf(defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER })
    var draftPermissionMode by mutableStateOf(DEFAULT_PERMISSION_MODE)
    var inputTextValue by mutableStateOf(TextFieldValue(""))
    var inputText: String
        get() = inputTextValue.text
        set(value) {
            inputTextValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    var isBusy by mutableStateOf(false)
    var runtimeCapabilities by mutableStateOf(RuntimeCapabilities())
    var diffReview by mutableStateOf<DiffReviewState?>(null)
        private set
    var gitCommitDraft by mutableStateOf(GitCommitDraft())
        private set
    var notificationLevelState by mutableStateOf<NotificationLevelState?>(null)
        private set
    var cliConsole by mutableStateOf(
        CliConsoleState(
            windows = listOf(
                CliConsoleWindow(
                    id = "cli_1",
                    title = "CLI 1",
                    cwd = defaultCwd.ifBlank { DEFAULT_AGENT_CWD },
                    model = defaultModel.ifBlank { DEFAULT_AGENT_MODEL },
                    reasoningEffort = defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT },
                ),
            ),
        ),
    )
        private set
    val agents = mutableStateListOf<Agent>()
    val codexModels = mutableStateListOf<CodexModelOption>()
    val alerts = mutableStateListOf<AgentAlert>()
    val approvalRequests = mutableStateListOf<AgentApprovalRequest>()
    val userInputRequests = mutableStateListOf<AgentUserInputRequest>()
    val attachmentDrafts = mutableStateListOf<AttachmentDraft>()
    var agentsRevision by mutableStateOf(0)
        private set
    var planReview by mutableStateOf<PlanReview?>(null)
        private set
    private var projectOptionsCacheRevision = -1
    private var projectOptionsCacheDraftCwd: String? = null
    private var projectOptionsCacheDefaultCwd = ""
    private var projectOptionsCacheRelayProjectRootsKey = ""
    private var projectOptionsCache = emptyList<String>()
    private var relayProjectRoots by mutableStateOf(emptyList<String>())
    val relayHostProfiles = mutableStateListOf<RelayHostProfile>()
    var activeRelayHostId by mutableStateOf(prefs.getString(PREF_ACTIVE_RELAY_HOST_ID, "") ?: "")
        private set
    var hostHealth by mutableStateOf(HostHealthState())
        private set

    private val strings: AppStrings
        get() = appStringsFor(appLanguage)

    init {
        refreshRelayHostProfilesFromPrefs()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val activeAgent: Agent?
        get() = activeAgentId?.let { selectedId -> agents.firstOrNull { it.id == selectedId } }

    fun addConnectionStateListener(listener: ConnectionStateListener) {
        connectionStateListeners.add(listener)
        listener.onConnectionStateChanged(connectionStatus, statusText)
    }

    fun removeConnectionStateListener(listener: ConnectionStateListener) {
        connectionStateListeners.remove(listener)
    }

    private fun notifyConnectionStateChanged() {
        if (connectionStatus == "connected") cancelConnectionDisconnectedNotification()
        val status = connectionStatus
        val text = statusText
        connectionStateListeners.forEach { it.onConnectionStateChanged(status, text) }
    }

    val draftAgent: Agent?
        get() {
            if (activeAgentId != null) return null
            val cwd = draftProjectCwd.orEmpty()
            return Agent(
                id = "draft_project_task",
                name = if (draftProjectLocked && cwd.isNotBlank()) projectNameFromCwd(cwd) else strings.homeSubtitle,
                model = draftModel.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
                cwd = cwd,
                projectRoot = cleanNullablePath(cwd),
                status = strings.homeSubtitle,
                serviceTier = normalizeServiceTier(draftServiceTier.ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } }),
                reasoningEffort = draftReasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } },
                permissionMode = normalizePermissionMode(draftPermissionMode),
            )
        }

    fun projectOptions(): List<String> {
        if (
            projectOptionsCacheRevision == agentsRevision &&
            projectOptionsCacheDraftCwd == draftProjectCwd &&
            projectOptionsCacheDefaultCwd == defaultCwd &&
            projectOptionsCacheRelayProjectRootsKey == relayProjectRoots.joinToString("\n")
        ) {
            return projectOptionsCache
        }
        val paths = mutableListOf<String>()
        relayProjectRoots.forEach { paths.add(it) }
        cleanNullablePath(draftProjectCwd)?.let { paths.add(it) }
        cleanNullablePath(defaultCwd)?.let { paths.add(it) }
        agents.sortedByDescending { it.updatedAt }.forEach { agent ->
            cleanNullablePath(agent.projectRoot ?: agent.cwd)?.let { paths.add(it) }
        }
        projectOptionsCache = paths.distinctBy { normalizePathKey(it) }
        projectOptionsCacheRevision = agentsRevision
        projectOptionsCacheDraftCwd = draftProjectCwd
        projectOptionsCacheDefaultCwd = defaultCwd
        projectOptionsCacheRelayProjectRootsKey = relayProjectRoots.joinToString("\n")
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

    private fun refreshRelayHostProfilesFromPrefs() {
        val stored = parseRelayHostProfiles(prefs.getString(PREF_RELAY_HOST_PROFILES, "[]"))
        relayHostProfiles.clear()
        if (stored.isNotEmpty()) {
            relayHostProfiles.addAll(stored)
        } else if (relayUrl.isNotBlank() && apiKey.isNotBlank()) {
            relayHostProfiles.add(
                RelayHostProfile(
                    id = relayHostIdFor(relayUrl),
                    name = relayHostNameFor(relayUrl),
                    relayUrl = relayUrl,
                    apiKey = apiKey,
                ),
            )
        }
        if (activeRelayHostId.isBlank() || relayHostProfiles.none { it.id == activeRelayHostId }) {
            activeRelayHostId = relayHostProfiles.firstOrNull()?.id.orEmpty()
        }
    }

    fun selectRelayHost(profileId: String) {
        val profile = relayHostProfiles.firstOrNull { it.id == profileId } ?: return
        prefs.edit()
            .putString(PREF_ACTIVE_RELAY_HOST_ID, profile.id)
            .putString(PREF_RELAY_URL, profile.relayUrl)
            .putString(PREF_API_KEY, profile.apiKey)
            .apply()
        activeRelayHostId = profile.id
        relayUrl = profile.relayUrl
        apiKey = profile.apiKey
        hostHealth = HostHealthState(loading = true)
        connect()
    }

    fun reloadSettings() {
        val nextRelayUrl = prefs.getString(PREF_RELAY_URL, DEFAULT_RELAY_URL)?.trim().orEmpty()
            .ifBlank { DEFAULT_RELAY_URL }
        val nextApiKey = prefs.getString(PREF_API_KEY, "")?.trim().orEmpty()
        val shouldReconnect = nextRelayUrl != relayUrl || nextApiKey != apiKey
        val previousLanguage = appLanguage
        activeRelayHostId = prefs.getString(PREF_ACTIVE_RELAY_HOST_ID, "") ?: ""
        relayUrl = nextRelayUrl
        apiKey = nextApiKey
        refreshRelayHostProfilesFromPrefs()
        defaultModel = prefs.getString(PREF_DEFAULT_MODEL, DEFAULT_AGENT_MODEL)?.ifBlank { DEFAULT_AGENT_MODEL }
            ?: DEFAULT_AGENT_MODEL
        defaultCwd = prefs.getString(PREF_DEFAULT_CWD, DEFAULT_AGENT_CWD)?.trim().orEmpty()
            .ifBlank { DEFAULT_AGENT_CWD }
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

    fun setAppInForeground(inForeground: Boolean) {
        if (appInForeground == inForeground) return
        appInForeground = inForeground
        if (inForeground) {
            flushStreamDeltas()
            flushCliOutputs()
            reloadSettings()
            if (connectionStatus == "connected") {
                refreshAgents()
                refreshActiveCodexThreadDetail()
            }
        } else {
            cancelAgentsRefresh()
        }
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
        val relaySecurityError = validateRelayEndpoint(relayUrl, strings)
        if (relaySecurityError != null) {
            connectionStatus = "disconnected"
            statusText = relaySecurityError
            return
        }
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
                        refreshHostHealth()
                        syncClientLanguage()
                        refreshRuntimeOptions()
                        refreshAgents()
                        replayMissedStream()
                        flushPendingQuickReplies()
                        flushPendingApprovalResponses()
                    } else {
                        connectionStatus = "disconnected"
                        statusText = localizedConnectionError(error)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                main.post {
                    if (generation == connectionGeneration) handleIncoming(msg)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post {
                    if (generation != connectionGeneration) return@post
                    val notifyDisconnected = connectionStatus == "connected"
                    this@EasyCodexController.webSocket = null
                    val error = localizedConnectionError(t.message)
                    failPending(error)
                    scheduleReconnect(error, notifyDisconnected)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post {
                    if (generation != connectionGeneration) return@post
                    val notifyDisconnected = connectionStatus == "connected"
                    this@EasyCodexController.webSocket = null
                    val error = if (reason.isBlank()) strings.connectionClosed else localizedConnectionError(reason)
                    failPending(error)
                    if (manuallyDisconnected) {
                        connectionStatus = "disconnected"
                        statusText = error
                    } else {
                        scheduleReconnect(error, notifyDisconnected)
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
        cancelCliOutputFlush()
        cancelRelayDetailRefresh()
        cancelThreadDetailRetries()
        cancelPendingTimeouts()
        streamingAgentIds.clear()
        connectionGeneration++
        webSocket?.close(1000, "Closed")
        webSocket = null
        failPending(strings.connectionClosed)
        connectionStatus = "disconnected"
        statusText = strings.connectionClosed
    }

    fun clearConnectionSensitiveState() {
        prefs.edit()
            .remove(PREF_RELAY_URL)
            .remove(PREF_API_KEY)
            .remove(PREF_ACTIVE_RELAY_HOST_ID)
            .remove(PREF_RELAY_HOST_PROFILES)
            .apply()
        relayUrl = DEFAULT_RELAY_URL
        apiKey = ""
        activeRelayHostId = ""
        relayHostProfiles.clear()
        statusText = strings.connectionConfigCleared
    }

    fun refreshAgents() {
        cancelAgentsRefresh()
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
                array.optJSONObject(index)?.let {
                    nextAgents.add(parseAgent(it))
                    syncPendingRequests(it)
                }
            }
            val visibleNextAgents = nextAgents.filterNot(::isHiddenAgent)
            val selectedAgent = activeAgent
            val selectedMissingFromRelay = activeAgentId != null && visibleNextAgents.none { it.id == activeAgentId }
            val keepLocalSelected = selectedAgent != null
            if (selectedMissingFromRelay && !keepLocalSelected) openHome()
            val runningAgents = if (selectedMissingFromRelay && keepLocalSelected) {
                visibleNextAgents + selectedAgent
            } else {
                visibleNextAgents
            }
            refreshCodexThreads(runningAgents) {
                finishAgentsRefresh()
            }
        }
    }

    fun openHome() {
        activeAgentId = null
        notificationLevelState = null
        draftProjectLocked = false
        draftProjectCwd = null
    }

    fun selectAgent(agentId: String) {
        if (agentId in hiddenTaskIds) return
        draftProjectCwd = null
        draftProjectLocked = false
        activeAgentId = agentId
        markAgentRead(agentId)
        alerts.removeAll { it.agentId == agentId }
        loadNotificationLevel(agentId)
        val selected = activeAgent
        val threadId = selected?.codexThreadId
        if (selected?.resumable == true && !threadId.isNullOrBlank() && detailedCodexThreads[threadId] == null) {
            updateAgent(agentId) { it.copy(activity = CODEX_DETAIL_LOADING_LABEL) }
        }
        refreshActiveCodexThreadDetail()
    }

    fun openSubAgentThread(message: AgentMessage) {
        val threadId = message.subAgentThreadId.trim()
        if (threadId.isBlank()) return
        val existing = agents.firstOrNull { it.codexThreadId == threadId || it.id == threadId || it.id == "codex_$threadId" }
        if (existing != null) {
            selectAgent(existing.id)
            return
        }
        statusText = "正在打开子代理线程"
        refreshCodexThreads(agents.toList()) {
            agents.firstOrNull { it.codexThreadId == threadId || it.id == "codex_$threadId" }?.let {
                selectAgent(it.id)
            }
        }
    }

    fun startCliConsole(cwd: String? = null) {
        val active = cliConsole.activeWindow
        val requestedCwd = cleanNullablePath(cwd)
            ?: cleanNullablePath(activeAgent?.projectRoot ?: activeAgent?.cwd)
            ?: cleanNullablePath(draftProjectCwd)
            ?: cleanNullablePath(active.cwd)
            ?: defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        val requestedModel = activeAgent?.model?.takeIf { it.isNotBlank() }
            ?: active.model.ifBlank { draftModel.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } } }
        val requestedReasoning = activeAgent?.reasoningEffort?.takeIf { it.isNotBlank() }
            ?: active.reasoningEffort.ifBlank { draftReasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } } }
        updateCliWindow(active.id) { it.copy(cwd = requestedCwd, model = requestedModel, reasoningEffort = requestedReasoning) }
        send(
            "cli_start",
            mapOf(
                "windowId" to active.id,
                "cwd" to requestedCwd,
                "model" to requestedModel,
                "reasoningEffort" to requestedReasoning,
                "sandboxMode" to active.sandboxMode,
                "skipGitRepoCheck" to active.skipGitRepoCheck,
            ),
        ) { data, error ->
            if (error != null) {
                appendCliLine("status", "Codex CLI 启动失败：$error")
                updateCliWindow(active.id) { it.copy(running = false, busy = false) }
                return@send
            }
            val payload = data?.optJSONObject("data") ?: JSONObject()
            val replayed = replayCliEvents(payload.optJSONArray("events"), active.id)
            updateCliWindow(active.id) { window ->
                window.copy(
                    cwd = payload.optString("cwd", requestedCwd).ifBlank { requestedCwd },
                    model = payload.optString("model", requestedModel).ifBlank { requestedModel },
                    reasoningEffort = payload.optString("reasoningEffort", requestedReasoning).ifBlank { requestedReasoning },
                    sandboxMode = payload.optString("sandboxMode", window.sandboxMode).ifBlank { window.sandboxMode },
                    skipGitRepoCheck = payload.optBoolean("skipGitRepoCheck", window.skipGitRepoCheck),
                    version = payload.optString("version", window.version),
                    running = true,
                    busy = payload.optBoolean("running", false),
                    runId = payload.optString("runId").takeIf { it.isNotBlank() && it != "null" },
                )
            }
            if (!replayed && cliWindow(active.id).lines.isEmpty()) {
                appendCliLine("status", "Codex CLI 已就绪。当前目录：${cliWindow(active.id).cwd}")
            }
        }
    }

    fun updateCliInput(value: String) {
        updateActiveCliWindow { it.copy(input = value) }
    }

    fun updateCliCwd(value: String) {
        updateActiveCliWindow { it.copy(cwd = value) }
    }

    fun updateCliModel(model: String) {
        updateActiveCliWindow { window ->
            val reasoningOptions = codexModels.firstOrNull { it.model == model }?.supportedReasoningEfforts.orEmpty()
            val nextReasoning = if (reasoningOptions.isEmpty() || window.reasoningEffort in reasoningOptions) {
                window.reasoningEffort
            } else {
                codexModels.firstOrNull { it.model == model }?.defaultReasoningEffort
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT }
            }
            window.copy(model = model, reasoningEffort = nextReasoning)
        }
    }

    fun updateCliReasoningEffort(reasoningEffort: String) {
        updateActiveCliWindow { it.copy(reasoningEffort = reasoningEffort) }
    }

    fun updateCliSandboxMode(sandboxMode: String) {
        val clean = when (sandboxMode) {
            "read-only", "workspace-write", "danger-full-access" -> sandboxMode
            else -> "workspace-write"
        }
        updateActiveCliWindow { it.copy(sandboxMode = clean) }
    }

    fun updateCliSkipGitRepoCheck(skip: Boolean) {
        updateActiveCliWindow { it.copy(skipGitRepoCheck = skip) }
    }

    fun sendCliCommand() {
        val window = cliConsole.activeWindow
        val draft = parseCliCommand(window)
        if (draft.prompt.isBlank() && draft.mode == "exec" || window.busy) return
        appendCliLine("user", window.input.trim())
        updateCliWindow(window.id) { it.copy(input = "", busy = true, running = true) }
        send(
            "cli_run",
            mapOf(
                "windowId" to window.id,
                "cwd" to window.cwd.ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } },
                "model" to window.model.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
                "reasoningEffort" to window.reasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } },
                "sandboxMode" to window.sandboxMode.ifBlank { "workspace-write" },
                "skipGitRepoCheck" to window.skipGitRepoCheck,
                "mode" to draft.mode,
                "sessionId" to draft.sessionId,
                "reviewTarget" to draft.reviewTarget,
                "profile" to draft.profile,
                "images" to draft.images,
                "addDirs" to draft.addDirs,
                "jsonOutput" to draft.jsonOutput,
                "ephemeral" to draft.ephemeral,
                "ignoreRules" to draft.ignoreRules,
                "prompt" to draft.prompt,
            ),
        ) { data, error ->
            if (error != null) {
                appendCliLine("status", "Codex CLI 执行失败：$error")
                updateCliWindow(window.id) { it.copy(busy = false) }
                return@send
            }
            val payload = data?.optJSONObject("data") ?: JSONObject()
            updateCliWindow(window.id) { current ->
                current.copy(
                    cwd = payload.optString("cwd", current.cwd).ifBlank { current.cwd },
                    runId = payload.optString("runId").takeIf { runId -> runId.isNotBlank() && runId != "null" },
                )
            }
        }
    }

    fun stopCliCommand() {
        val windowId = cliConsole.activeWindow.id
        send("cli_stop", mapOf("windowId" to windowId)) { _, error ->
            if (error != null) {
                appendCliLine("status", "停止 Codex CLI 失败：$error")
            }
        }
    }

    fun createCliWindow() {
        val nextIndex = cliConsole.windows.size + 1
        val active = cliConsole.activeWindow
        val next = CliConsoleWindow(
            id = "cli_${UUID.randomUUID()}",
            title = "CLI $nextIndex",
            cwd = active.cwd,
            model = active.model,
            reasoningEffort = active.reasoningEffort,
            sandboxMode = active.sandboxMode,
            skipGitRepoCheck = active.skipGitRepoCheck,
            mode = active.mode,
            sessionId = active.sessionId,
            reviewTarget = active.reviewTarget,
            profile = active.profile,
            images = active.images,
            addDirs = active.addDirs,
            jsonOutput = active.jsonOutput,
            ephemeral = active.ephemeral,
            ignoreRules = active.ignoreRules,
            version = active.version,
        )
        cliConsole = cliConsole.copy(activeWindowId = next.id, windows = cliConsole.windows + next)
        startCliConsole(next.cwd)
    }

    fun selectCliWindow(windowId: String) {
        if (cliConsole.windows.none { it.id == windowId }) return
        cliConsole = cliConsole.copy(activeWindowId = windowId)
        startCliConsole(cliConsole.activeWindow.cwd)
    }

    fun deleteAgent(agentId: String) {
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        val threadId = agent.codexThreadId?.trim().orEmpty()
        if (threadId.isNotEmpty()) {
            val params = mutableMapOf<String, Any?>(
                "threadId" to threadId,
                "agentId" to agentId,
            )
            send("archive_codex_thread", params) { _, error ->
                if (error != null) {
                    statusText = strings.taskArchiveFailed(error)
                    return@send
                }
                removeArchivedCodexThread(threadId, agentId)
                statusText = strings.taskArchived
            }
            return
        }

        hideAgentLocally(agent)
        statusText = strings.taskRemovedLocally
        if (!agent.resumable || agent.isBusy()) {
            send("stop_agent", mapOf("agentId" to agentId)) { _, error ->
                if (error != null && !error.contains("not found", ignoreCase = true)) {
                    statusText = strings.taskArchiveFailed(error)
                }
            }
        }
    }

    private fun hideAgentLocally(agent: Agent, persistHidden: Boolean = true) {
        val agentId = agent.id
        if (persistHidden) {
            val idsToHide = listOfNotNull(agent.id, agent.codexThreadId).filter { it.isNotBlank() }
            if (idsToHide.isNotEmpty()) {
                hiddenTaskIds.addAll(idsToHide)
                prefs.edit().putStringSet(PREF_HIDDEN_TASK_IDS, hiddenTaskIds.toSet()).apply()
            }
        }
        removeLocalAgent(agentId)
        agent.codexThreadId?.let { threadId ->
            detailedCodexThreads.remove(threadId)
            threadDetailRetryRunnables.remove(threadId)?.let { main.removeCallbacks(it) }
            threadDetailRetryCounts.remove(threadId)
            latestThreadDetailRequests.remove(threadId)
            threadDetailsInFlight.remove(threadId)
        }
        streamingAgentIds.remove(agentId)
        approvalRequests.removeAll { it.agentId == agentId }
        userInputRequests.removeAll { it.agentId == agentId }
        alerts.removeAll { it.agentId == agentId }
        notificationLevelState = notificationLevelState?.takeUnless { it.agentId == agentId }
    }

    private fun removeArchivedCodexThread(threadId: String, agentId: String? = null) {
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isBlank()) return
        val idsToRemove = buildSet {
            add("codex_$normalizedThreadId")
            add(normalizedThreadId)
            agentId?.takeIf { it.isNotBlank() }?.let(::add)
            agents
                .filter { it.codexThreadId == normalizedThreadId }
                .mapTo(this) { it.id }
        }
        idsToRemove.forEach(::removeLocalAgent)
        detailedCodexThreads.remove(normalizedThreadId)
        latestThreadDetailRequests.remove(normalizedThreadId)
        threadDetailsInFlight.remove(normalizedThreadId)
        threadDetailRetryCounts.remove(normalizedThreadId)
        threadDetailRetryRunnables.remove(normalizedThreadId)?.let { main.removeCallbacks(it) }
        streamingAgentIds.remove(normalizedThreadId)
        streamingAgentIds.remove("codex_$normalizedThreadId")
        idsToRemove.forEach { id ->
            streamingAgentIds.remove(id)
            pendingOutboundMessages.remove(id)
            clearPendingAgentStatus(id)
            approvalRequests.removeAll { it.agentId == id }
            userInputRequests.removeAll { it.agentId == id }
            alerts.removeAll { it.agentId == id }
        }
        notificationLevelState = notificationLevelState?.takeUnless { it.agentId in idsToRemove }
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
        draftPermissionMode = normalizePermissionMode(current?.permissionMode)
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

    fun updateDraftPermissionMode(permissionMode: String) {
        activeAgentId = null
        draftPermissionMode = normalizePermissionMode(permissionMode)
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
        val selectedModel = agent?.model?.takeIf { it.isNotBlank() } ?: defaultModel
        val fromModel = codexModels.firstOrNull { it.model == selectedModel }?.supportedReasoningEfforts
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return if (fromModel.isNotEmpty()) fromModel else listOf("low", "medium", "high", "xhigh")
    }

    fun serviceTierOptionsFor(agent: Agent?): List<String> {
        if (!runtimeCapabilities.supportsServiceTier) return emptyList()
        val selectedModel = agent?.model?.takeIf { it.isNotBlank() } ?: defaultModel
        val additional = codexModels.firstOrNull { it.model == selectedModel }?.additionalSpeedTiers
            ?.map(::normalizeServiceTier)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return (listOf(DEFAULT_SERVICE_TIER) + additional).distinct()
    }

    fun updateActiveModel(model: String) {
        if (activeAgentId == null) {
            draftModel = model
            val speedTiers = serviceTierOptionsFor(draftAgent)
            draftServiceTier = if (draftServiceTier in speedTiers) draftServiceTier else DEFAULT_SERVICE_TIER
            return
        }
        val currentTier = normalizeServiceTier(activeAgent?.serviceTier?.takeIf { it.isNotBlank() } ?: defaultServiceTier)
        val speedTiers = serviceTierOptionsFor(activeAgent?.copy(model = model))
        val nextTier = if (currentTier in speedTiers) currentTier else DEFAULT_SERVICE_TIER
        updateActiveConfig(model = model, serviceTier = nextTier)
    }

    fun updateActiveReasoningEffort(reasoningEffort: String) {
        if (activeAgentId == null) {
            draftReasoningEffort = reasoningEffort
            return
        }
        updateActiveConfig(reasoningEffort = reasoningEffort)
    }

    fun updateActiveServiceTier(serviceTier: String) {
        if (activeAgentId == null) {
            draftServiceTier = normalizeServiceTier(serviceTier)
            return
        }
        updateActiveConfig(serviceTier = serviceTier)
    }

    fun updateActivePermissionMode(permissionMode: String) {
        val clean = normalizePermissionMode(permissionMode)
        if (activeAgentId == null) {
            draftPermissionMode = clean
            return
        }
        updateActiveConfig(permissionMode = clean)
    }

    private fun updateActiveConfig(
        model: String? = null,
        reasoningEffort: String? = null,
        serviceTier: String? = null,
        permissionMode: String? = null,
    ) {
        val agent = activeAgent ?: return
        val next = agent.copy(
            model = model ?: agent.model,
            reasoningEffort = reasoningEffort ?: agent.reasoningEffort,
            serviceTier = normalizeServiceTier(serviceTier ?: agent.serviceTier),
            permissionMode = normalizePermissionMode(permissionMode ?: agent.permissionMode),
        )
        updateAgent(agent.id) { next }
        if (agent.resumable) return
        val params = mutableMapOf<String, Any?>("agentId" to agent.id)
        model?.let { params["model"] = it }
        reasoningEffort?.takeIf { runtimeCapabilities.supportsReasoningEffort }?.let { params["reasoningEffort"] = it }
        serviceTier?.takeIf { runtimeCapabilities.supportsServiceTier }?.let { params["serviceTier"] = normalizeServiceTier(it) }
        permissionMode?.let { params["permissionMode"] = normalizePermissionMode(it) }
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
        permissionMode: String = draftPermissionMode,
        projectless: Boolean = false,
        firstMessage: String? = null,
        firstDisplayMessage: String? = null,
        firstAttachments: List<AttachmentDraft> = emptyList(),
    ) {
        isBusy = true
        val effectiveName = name.ifBlank { "EasyCodex" }
        val effectiveModel = model.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } }
        val effectiveCwd = if (projectless) "" else cwd.ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } }
        val effectiveReasoningEffort = reasoningEffort.ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } }
        val effectiveServiceTier = normalizeServiceTier(serviceTier.ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } })
        val effectivePermissionMode = normalizePermissionMode(permissionMode)
        val optimisticAgentId = firstMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { "local_agent_${UUID.randomUUID()}" }
        if (optimisticAgentId != null) {
            val now = System.currentTimeMillis()
            val optimisticText = firstDisplayMessage ?: firstMessage.orEmpty()
            val pendingStatusId = "local_pending_${UUID.randomUUID()}"
            val optimisticAgent = Agent(
                id = optimisticAgentId,
                name = taskNameFromPrompt(optimisticText),
                model = effectiveModel,
                cwd = effectiveCwd,
                projectRoot = if (projectless) null else effectiveCwd,
                status = "working",
                serviceTier = effectiveServiceTier,
                reasoningEffort = effectiveReasoningEffort,
                permissionMode = effectivePermissionMode,
                activity = "已提交，正在创建任务",
                messages = listOf(
                    AgentMessage(
                        role = "user",
                        type = "user",
                        text = optimisticText,
                        timestamp = now,
                        itemId = "local_user_${UUID.randomUUID()}",
                        attachments = firstAttachments,
                    ),
                    AgentMessage(
                        role = "agent",
                        type = "status",
                        text = "已发送，正在创建 EasyCodex 任务...",
                        timestamp = now + 1,
                        itemId = pendingStatusId,
                        streaming = true,
                    ),
                ),
                updatedAt = now,
            )
            pendingAgentStatusIds[optimisticAgentId] = pendingStatusId
            agents.add(0, optimisticAgent)
            agentsRevision += 1
            activeAgentId = optimisticAgentId
            draftProjectCwd = null
        }
        val params = mutableMapOf<String, Any?>(
            "name" to effectiveName,
            "model" to effectiveModel,
            "cwd" to effectiveCwd,
            "projectless" to projectless,
            "permissionMode" to effectivePermissionMode,
        )
        if (runtimeCapabilities.supportsServiceTier) {
            params["serviceTier"] = effectiveServiceTier
        }
        if (runtimeCapabilities.supportsReasoningEffort) {
            params["reasoningEffort"] = effectiveReasoningEffort
        }
        if (!firstMessage.isNullOrBlank()) {
            params["firstMessage"] = firstMessage
            params["attachments"] = attachmentRequestPayload(firstAttachments)
        }
        send(
            "create_agent",
            params,
        ) { data, error ->
            isBusy = false
            val restoreText = firstDisplayMessage ?: firstMessage
            if (error != null) {
                statusText = error
                restoreDraftOnSendFailure(restoreText, firstAttachments)
                optimisticAgentId?.let { localId ->
                    clearPendingAgentStatus(localId)
                    val timedOut = error.contains("timeout", ignoreCase = true) || error.contains("超时")
                    updateAgent(localId) {
                        if (timedOut) {
                            it.copy(status = "working", activity = "创建请求超时，正在等待中继同步结果", updatedAt = System.currentTimeMillis())
                        } else {
                            it.copy(status = "error", activity = null, updatedAt = System.currentTimeMillis())
                        }
                    }
                    appendMessage(localId, AgentMessage("agent", "status", "发送失败：$error", System.currentTimeMillis()))
                }
                return@send
            }
            data?.optJSONObject("data")?.let {
                val agent = parseAgent(it)
                optimisticAgentId?.let { localId ->
                    pendingAgentStatusIds.remove(localId)
                    val index = agents.indexOfFirst { existing -> existing.id == localId }
                    if (index >= 0) {
                        agents.removeAt(index)
                        agentsRevision += 1
                    }
                }
                upsertAgent(agent)
                activeAgentId = agent.id
                draftProjectCwd = null
                if (!firstMessage.isNullOrBlank()) {
                    if (inputText == firstMessage || inputText == restoreText) inputText = ""
                    val acceptedDisplayText = simplifyUserMessageForDisplay(restoreText ?: firstMessage, "user", "user").trim()
                    val acceptedTransportText = simplifyUserMessageForDisplay(firstMessage, "user", "user").trim()
                    val relayAlreadyAcceptedMessage = agent.messages.any { message ->
                        (message.role == "user" || message.type == "user") &&
                            simplifyUserMessageForDisplay(message.text, message.role, message.type).trim().let { accepted ->
                                accepted == acceptedDisplayText || accepted == acceptedTransportText
                            }
                    }
                    if (!relayAlreadyAcceptedMessage) {
                        sendMessageToAgent(
                            agent.id,
                            firstMessage,
                            restoreText ?: firstMessage,
                            firstAttachments,
                        )
                    }
                }
            }
        }
    }

    fun clearCliWindow() {
        updateActiveCliWindow { it.copy(lines = emptyList(), truncated = false) }
    }

    private fun replayCliEvents(events: JSONArray?, windowId: String): Boolean {
        if (events == null || events.length() == 0 || cliWindow(windowId).lines.isNotEmpty()) return false
        var replayed = false
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            handleStreamEnvelope(event)
            replayed = true
        }
        return replayed
    }

    fun sendActiveMessage(planMode: Boolean = false) {
        val typedText = inputText.trim()
        val selectedAttachments = attachmentDrafts.toList()
        val attachmentText = attachmentPrompt(selectedAttachments)
        val text = listOf(typedText, attachmentText).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isBlank()) return
        val transportText = if (planMode) "$PLAN_MODE_PROMPT$text" else text
        val displayText = listOf(typedText.ifBlank { "处理已上传附件" }, attachmentDisplayText(selectedAttachments))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        draftAgent?.let { draft ->
            val projectless = draft.cwd.isBlank()
            inputText = ""
            attachmentDrafts.clear()
            statusText = "已发送，正在创建 EasyCodex 任务..."
            createAgent(
                name = taskNameFromPrompt(typedText.ifBlank { text }),
                model = draft.model,
                cwd = draft.cwd,
                reasoningEffort = draft.reasoningEffort,
                serviceTier = draft.serviceTier,
                permissionMode = draft.permissionMode,
                projectless = projectless,
                firstMessage = transportText,
                firstDisplayMessage = displayText,
                firstAttachments = selectedAttachments,
            )
            return
        }
        val agent = activeAgent ?: return
        if (agent.resumable) {
            inputText = ""
            attachmentDrafts.clear()
            statusText = "已发送，正在恢复任务..."
            resumeCodexThread(agent, transportText, displayText, selectedAttachments)
            return
        }
        inputText = ""
        attachmentDrafts.clear()
        val queueFollowUp = shouldQueueFollowUpOnCurrentAgent(agent)
        statusText = if (queueFollowUp) {
            "已排队，等待当前任务完成"
        } else {
            "已发送，等待 AI 接收"
        }
        if (queueFollowUp) {
            sendMessageToAgent(agent.id, transportText, displayText, selectedAttachments)
            return
        }
        sendMessageToAgent(agent.id, transportText, displayText, selectedAttachments)
    }

    private fun restoreDraftOnSendFailure(text: String?, attachments: List<AttachmentDraft>) {
        if (!text.isNullOrBlank() && inputText.isBlank()) inputText = text
        attachments.forEach { draft ->
            if (attachmentDrafts.none { it.path == draft.path }) attachmentDrafts.add(draft)
        }
    }

    fun showPlanReview(agentId: String, message: AgentMessage) {
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        val latest = latestPlanMessage(agent, message.itemId) ?: message
        planReview = PlanReview(
            agentId = agentId,
            message = latest,
            taskName = agent.displayTaskName(),
            projectPath = agent.displayProjectPath(),
        )
    }

    fun dismissPlanReview() {
        planReview = null
    }

    fun optimizePlan(review: PlanReview, adjustment: String = "") {
        planReview = null
        val cleanAdjustment = adjustment.trim()
        val prompt = if (cleanAdjustment.isBlank()) {
            PLAN_OPTIMIZE_PROMPT
        } else {
            "$PLAN_OPTIMIZE_PROMPT\n\n我的补充要求：$cleanAdjustment"
        }
        sendMessageToAgent(review.agentId, prompt, if (cleanAdjustment.isBlank()) "优化这个计划" else "调整这个计划：$cleanAdjustment")
    }

    fun startPlan(review: PlanReview) {
        planReview = null
        val pendingStartRequest = userInputRequests
            .firstOrNull { it.agentId == review.agentId }
            ?.let { request -> planStartAnswersForRequest(request)?.let { request to it } }
        if (pendingStartRequest != null) {
            respondUserInputRequest(pendingStartRequest.first, pendingStartRequest.second)
            return
        }
        sendMessageToAgent(review.agentId, PLAN_START_PROMPT, PLAN_START_PROMPT)
    }

    fun requestUndoFileChanges(files: List<String>) {
        val agent = activeAgent ?: return
        val cleanFiles = files
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val fileList = cleanFiles
            .take(20)
            .joinToString("\n") { "- $it" }
        val prompt = buildString {
            append("请撤销你刚才对工作区文件做出的改动。")
            if (fileList.isNotBlank()) {
                append("\n\n需要优先检查并撤销这些文件：\n")
                append(fileList)
                if (cleanFiles.size > 20) append("\n- 以及另外 ${cleanFiles.size - 20} 个文件")
            }
            append("\n\n请先确认 diff，再只撤销这些改动，不要影响用户或其他任务已有的未提交改动。完成后说明撤销了哪些文件，并重新汇报当前状态。")
        }
        val displayText = if (cleanFiles.isEmpty()) {
            "撤销刚才的文件改动"
        } else {
            "撤销 ${cleanFiles.size} 个文件的改动"
        }
        sendMessageToAgent(agent.id, prompt, displayText)
    }

    fun appendToInput(value: String) {
        if (value.isBlank()) return
        inputText = when {
            inputText.isBlank() -> value
            inputText.endsWith(" ") || inputText.endsWith("\n") -> inputText + value
            else -> "$inputText $value"
        }
    }

    fun sendQuickReply(agentId: String, text: String) {
        val cleanText = text.trim()
        if (agentId.isBlank() || cleanText.isBlank()) return
        if (connectionStatus != "connected") {
            queueQuickReply(agentId, cleanText)
            connect()
            statusText = "正在重连中继，快速回复会在连接恢复后发送"
            return
        }
        deliverQuickReply(agentId, cleanText)
    }

    fun removeAttachmentDraft(path: String) {
        attachmentDrafts.removeAll { it.path == path }
    }

    private fun attachmentPrompt(attachments: List<AttachmentDraft> = attachmentDrafts): String {
        if (attachments.isEmpty()) return ""
        val imageCount = attachments.count { it.mimeType?.startsWith("image/") == true }
        val fileLines = attachments
            .filterNot { it.mimeType?.startsWith("image/") == true }
            .map { "- ${it.name}: ${it.path}" }
        val imageText = when (imageCount) {
            0 -> ""
            1 -> "已上传 1 张图片。"
            else -> "已上传 $imageCount 张图片。"
        }
        val fileText = fileLines.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "已上传附件：\n",
            separator = "\n",
        ).orEmpty()
        return listOf(imageText, fileText, "请结合这些附件继续处理。")
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun attachmentDisplayText(attachments: List<AttachmentDraft>): String {
        if (attachments.isEmpty()) return ""
        val imageCount = attachments.count { it.mimeType?.startsWith("image/") == true }
        val fileNames = attachments
            .filterNot { it.mimeType?.startsWith("image/") == true }
            .map { it.name }
        val imageText = when (imageCount) {
            0 -> ""
            1 -> "已附加 1 张图片"
            else -> "已附加 $imageCount 张图片"
        }
        val fileText = fileNames.takeIf { it.isNotEmpty() }?.joinToString(prefix = "已附加文件：") ?: ""
        return listOf(imageText, fileText).filter { it.isNotBlank() }.joinToString("\n")
    }

    fun uploadActiveAttachments(uris: List<Uri>) {
        val cwd = draftProjectCwd ?: activeAgent?.cwd
        if (cwd == null) {
            statusText = "请先选择项目目录，再上传附件"
            return
        }
        if (uris.isEmpty()) return
        isBusy = true
        statusText = "正在读取 ${uris.take(12).size} 个附件"
        thread(name = "EasyCodexAttachmentReader") {
            val files = mutableListOf<Map<String, Any?>>()
            var errorText: String? = null
            for (uri in uris.take(12)) {
                when (val result = attachmentPayload(uri)) {
                    is AttachmentPayloadResult.Success -> files.add(result.payload)
                    is AttachmentPayloadResult.TooLarge -> {
                        errorText = strings.attachmentTooLarge(result.name)
                        break
                    }
                    AttachmentPayloadResult.Unreadable -> Unit
                }
            }
            main.post {
                if (errorText != null) {
                    isBusy = false
                    statusText = errorText.orEmpty()
                    return@post
                }
                if (files.isEmpty()) {
                    isBusy = false
                    statusText = "没有读取到可上传的附件"
                    return@post
                }
                val oversized = files.firstOrNull { (it["size"] as? Int ?: 0) > MAX_ATTACHMENT_BYTES }
                if (oversized != null) {
                    isBusy = false
                    statusText = strings.attachmentTooLarge((oversized["name"] as? String) ?: strings.attachmentFallbackName)
                    return@post
                }
                val totalBytes = files.sumOf { it["size"] as? Int ?: 0 }
                if (totalBytes > MAX_ATTACHMENT_BATCH_BYTES) {
                    isBusy = false
                    statusText = "本次附件总大小超过 48 MB，未上传任何附件"
                    return@post
                }
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
                        statusText = strings.attachmentUploadFailed(error)
                        return@send
                    }
                    val uploaded = data?.optJSONObject("data")?.optJSONArray("files")
                        ?: data?.optJSONArray("files")
                        ?: JSONArray()
                    val uploadedDrafts = mutableListOf<AttachmentDraft>()
                    for (index in 0 until uploaded.length()) {
                        val file = uploaded.optJSONObject(index) ?: continue
                        val name = file.optString("name", strings.attachmentFallbackName)
                        val path = file.optString("path")
                        val mimeType = file.optString("mimeType").takeIf { it.isNotBlank() }
                        val previewUri = files.getOrNull(index)?.get("previewUri") as? String
                        if (path.isNotBlank()) uploadedDrafts.add(
                            AttachmentDraft(
                                name = name,
                                path = path,
                                mimeType = mimeType,
                                previewUri = previewUri.takeIf { mimeType?.startsWith("image/") == true },
                            ),
                        )
                    }
                    if (uploadedDrafts.isEmpty()) {
                        statusText = strings.attachmentNoPath
                        return@send
                    }
                    uploadedDrafts.forEach { draft ->
                        if (attachmentDrafts.none { it.path == draft.path }) attachmentDrafts.add(draft)
                    }
                    statusText = "已上传 ${uploadedDrafts.size} 个附件"
                }
            }
        }
    }

    private fun attachmentPayload(uri: Uri): AttachmentPayloadResult {
        val resolver = context.contentResolver
        val name = displayName(uri)
        if (isAttachmentSizeOverLimit(declaredAttachmentSize(uri))) {
            return AttachmentPayloadResult.TooLarge(name)
        }
        val input = runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: return AttachmentPayloadResult.Unreadable
        val bytes = input.use { readAttachmentBytesWithinLimit(it) }
        if (bytes == null) return AttachmentPayloadResult.TooLarge(name)
        val mimeType = resolver.getType(uri)
        return AttachmentPayloadResult.Success(
            mapOf(
                "name" to name,
                "mimeType" to mimeType,
                "size" to bytes.size,
                "base64" to Base64.encodeToString(bytes, Base64.NO_WRAP),
                "previewUri" to uri.toString(),
            ),
        )
    }

    private fun declaredAttachmentSize(uri: Uri): Long? {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                return cursor.getLong(index)
            }
        }
        return null
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

    private fun attachmentRequestPayload(attachments: List<AttachmentDraft>): List<Map<String, Any?>> {
        return attachments.map {
            mapOf(
                "name" to it.name,
                "path" to it.path,
                "mimeType" to it.mimeType,
            )
        }
    }

    private sealed class AttachmentPayloadResult {
        data class Success(val payload: Map<String, Any?>) : AttachmentPayloadResult()
        data class TooLarge(val name: String) : AttachmentPayloadResult()
        data object Unreadable : AttachmentPayloadResult()
    }

    private fun sendMessageToAgent(
        agentId: String,
        text: String,
        displayText: String = text,
        attachments: List<AttachmentDraft> = emptyList(),
    ) {
        val targetAgent = agents.firstOrNull { it.id == agentId }
        val wasBusy = targetAgent?.isBusy() == true
        streamingAgentIds.add(agentId)
        val localMessage = AgentMessage(
            role = "user",
            type = "user",
            text = displayText,
            timestamp = System.currentTimeMillis(),
            itemId = "local_user_${UUID.randomUUID()}",
            attachments = attachments,
        )
        rememberPendingOutbound(agentId, targetAgent?.codexThreadId, localMessage)
        appendMessage(
            agentId,
            localMessage,
        )
        val pendingStatus = AgentMessage(
            role = "agent",
            type = "thinking",
            text = if (wasBusy) "已排队" else "思考中",
            timestamp = System.currentTimeMillis(),
            itemId = "local_pending_${UUID.randomUUID()}",
            streaming = true,
        )
        pendingAgentStatusIds[agentId] = pendingStatus.itemId.orEmpty()
        appendMessage(agentId, pendingStatus)
        updateAgent(agentId) {
            it.copy(
                status = "working",
                activity = if (wasBusy) "已加入队列，等待当前任务完成" else "已提交，等待 AI 接收",
            )
        }
        send(
            "send_message",
            mapOf(
                "agentId" to agentId,
                "text" to text,
                "attachments" to attachmentRequestPayload(attachments),
            ),
        ) { _, error ->
            if (error != null) {
                streamingAgentIds.remove(agentId)
                forgetPendingOutbound(agentId, targetAgent?.codexThreadId, localMessage)
                clearPendingAgentStatus(agentId)
                if (error.contains("Agent not found", ignoreCase = true) && targetAgent?.codexThreadId?.isNotBlank() == true) {
                    removeMessage(agentId, localMessage)
                    resumeCodexThread(targetAgent, text, displayText, attachments)
                    return@send
                }
                restoreDraftOnSendFailure(displayText, attachments)
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

    private fun queueQuickReply(agentId: String, text: String) {
        pendingQuickReplies.add(PendingQuickReply(agentId, text, System.currentTimeMillis()))
        while (pendingQuickReplies.size > 20) pendingQuickReplies.removeAt(0)
    }

    private fun flushPendingQuickReplies() {
        if (pendingQuickReplies.isEmpty() || connectionStatus != "connected") return
        val replies = pendingQuickReplies.toList()
        pendingQuickReplies.clear()
        replies.forEach { deliverQuickReply(it.agentId, it.text) }
    }

    private fun deliverQuickReply(agentId: String, text: String) {
        val agent = agents.firstOrNull { it.id == agentId }
        if (agent != null) {
            sendMessageToAgent(agent.id, text)
            return
        }
        send(
            "send_message",
            mapOf(
                "agentId" to agentId,
                "text" to text,
                "attachments" to emptyList<Map<String, Any?>>(),
            ),
        ) { _, error ->
            if (error != null) {
                statusText = "快速回复失败：$error"
            } else {
                statusText = "快速回复已发送"
                refreshAgents()
            }
        }
    }

    private fun refreshCodexThreads(runningAgents: List<Agent>, onComplete: () -> Unit = {}) {
        refreshCodexThreadsPage(runningAgents, emptyList(), emptyMap(), emptySet(), emptyList(), null, onComplete)
    }

    private fun refreshCodexThreadsPage(
        runningAgents: List<Agent>,
        importedSoFar: List<Agent>,
        refreshedRunningSoFar: Map<String, Agent>,
        visibleThreadIdsSoFar: Set<String>,
        projectRootsSoFar: List<String>,
        cursor: String?,
        onComplete: () -> Unit,
    ) {
        val params = mutableMapOf<String, Any?>(
            "limit" to 100,
            "includeGlobal" to true,
        )
        if (!cursor.isNullOrBlank()) params["cursor"] = cursor
        send("list_codex_threads", params) { data, error ->
            if (error != null) {
                statusText = "已连接，EasyCodex 任务同步失败：$error"
                settleStaleResumableAgents(runningAgents)
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
            val refreshedRunning = mutableMapOf<String, Agent>()
            val pageVisibleThreadIds = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val thread = array.optJSONObject(index) ?: continue
                val threadId = thread.optString("id")
                if (!isRestorableCodexThread(thread)) continue
                if (threadId in hiddenTaskIds) continue
                if (threadId.isBlank()) continue
                syncPendingRequests(thread)
                pageVisibleThreadIds.add(threadId)
                if (threadId in existingThreadIds) {
                    val existing = runningAgents.firstOrNull { it.codexThreadId == threadId } ?: continue
                    val refreshed = mergeCodexThreadSummary(parseCodexThread(thread)).copy(id = existing.id)
                    refreshedRunning[existing.id] = refreshed
                    continue
                }
                val agent = mergeCodexThreadSummary(parseCodexThread(thread))
                if (isHiddenAgent(agent)) continue
                if (agent.id !in existingIds && imported.none { it.id == agent.id }) imported.add(agent)
            }
            val nextImported = importedSoFar + imported
            val nextRefreshedRunning = refreshedRunningSoFar + refreshedRunning
            val nextVisibleThreadIds = visibleThreadIdsSoFar + pageVisibleThreadIds
            val nextProjectRoots = (projectRootsSoFar + parseProjectRoots(data)).distinctBy { normalizePathKey(it) }
            val nextCursor = listOf(
                data?.optJSONObject("data")?.optString("nextCursor").orEmpty(),
                data?.optString("nextCursor").orEmpty(),
            ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            if (nextCursor.isNotBlank()) {
                refreshCodexThreadsPage(runningAgents, nextImported, nextRefreshedRunning, nextVisibleThreadIds, nextProjectRoots, nextCursor, onComplete)
                return@send
            }
            val selected = activeAgentId
            val visibleRunningAgents = runningAgents.filter {
                it.codexThreadId.isNullOrBlank() ||
                    it.codexThreadId in nextVisibleThreadIds ||
                    it.id == selected ||
                    it.isBusy()
            }.map { agent ->
                nextRefreshedRunning[agent.id]?.let { refreshed ->
                    refreshed.copy(messages = mergeMessagesForSnapshot(agent.messages, refreshed))
                } ?: agent
            }
            pruneMissingCodexThreads(nextVisibleThreadIds)
            updateRelayProjectRoots(nextProjectRoots)
            replaceAgents(visibleRunningAgents + nextImported)
            activeAgentId = when {
                selected != null && agents.any { it.id == selected } -> selected
                else -> null
            }
            refreshUnreadCompletedAlerts()
            prefetchCodexThreadDetails(visibleRunningAgents + nextImported)
            refreshActiveCodexThreadDetail()
            onComplete()
        }
    }

    private fun settleStaleResumableAgents(runningAgents: List<Agent>) {
        val liveThreadIds = runningAgents.mapNotNull { it.codexThreadId }.toSet()
        val staleAgents = agents
            .filter { it.resumable && it.isBusy() }
            .filter { agent -> agent.codexThreadId?.let { it !in liveThreadIds } != false }
        if (staleAgents.isEmpty()) return
        staleAgents.forEach { agent ->
            updateAgent(agent.id) { current ->
                current.asIdle(status = "可恢复", updatedAt = current.updatedAt)
            }
            if (alerts.none { it.agentId == agent.id }) {
                recordAgentAlert(agent.id, AgentAlertKind.Completed, "任务状态已刷新，可点开查看最新内容。", notify = false)
            }
        }
    }

    private fun refreshUnreadCompletedAlerts() {
        agents
            .filter(::shouldShowUnreadCompletedAlert)
            .forEach { agent ->
                recordAgentAlert(agent.id, AgentAlertKind.Completed, agent.preview.orEmpty(), notify = false)
            }
    }

    private fun shouldShowUnreadCompletedAlert(agent: Agent): Boolean {
        if (!agent.resumable || agent.isBusy() || agent.id == activeAgentId) return false
        if (alerts.any { it.agentId == agent.id }) return false
        val updatedAt = agent.updatedAt
        if (updatedAt <= 0L) return false
        val age = System.currentTimeMillis() - updatedAt
        if (age < 0L || age > RECENT_UNREAD_TASK_WINDOW_MS) return false
        return taskReadKey(agent) !in readTaskKeys
    }

    private fun markAgentRead(agentId: String) {
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        val key = taskReadKey(agent)
        if (key in readTaskKeys) return
        readTaskKeys.add(key)
        prefs.edit().putStringSet(PREF_READ_TASK_KEYS, readTaskKeys.toSet()).apply()
    }

    private fun taskReadKey(agent: Agent): String {
        return "${agent.codexThreadId ?: agent.id}:${agent.updatedAt}"
    }

    private fun finishAgentsRefresh() {
        agentsRefreshInFlight = false
        if (agentsRefreshQueued) {
            agentsRefreshQueued = false
            scheduleAgentsRefresh()
        }
    }

    private fun removeLocalAgent(agentId: String) {
        val removedActive = activeAgentId == agentId
        agents.removeAll { it.id == agentId }
        agentsRevision += 1
        if (removedActive) openHome()
    }

    private fun replaceAgents(nextAgents: List<Agent>) {
        reconcileAgents(mergedAgents(nextAgents.filterNot(::isHiddenAgent)), removeMissing = true)
    }

    private fun mergeAgents(nextAgents: List<Agent>) {
        reconcileAgents(mergedAgents(nextAgents.filterNot(::isHiddenAgent)), removeMissing = false)
    }

    private fun isHiddenAgent(agent: Agent): Boolean {
        return agent.id in hiddenTaskIds || agent.codexThreadId?.let { it in hiddenTaskIds } == true
    }

    private fun pruneMissingCodexThreads(visibleThreadIds: Set<String>) {
        val staleThreadIds = detailedCodexThreads.keys.filter { it !in visibleThreadIds }
        staleThreadIds.forEach { threadId ->
            detailedCodexThreads.remove(threadId)
            latestThreadDetailRequests.remove(threadId)
            threadDetailsInFlight.remove(threadId)
            threadDetailRetryCounts.remove(threadId)
            threadDetailRetryRunnables.remove(threadId)?.let { main.removeCallbacks(it) }
            streamingAgentIds.remove(threadId)
            streamingAgentIds.remove("codex_$threadId")
        }
    }

    private fun pendingOutboundKeys(agentId: String, threadId: String?): List<String> {
        return listOfNotNull(
            agentId.takeIf { it.isNotBlank() },
            threadId?.takeIf { it.isNotBlank() },
            threadId?.takeIf { it.isNotBlank() }?.let { "codex_$it" },
        ).distinct()
    }

    private fun rememberPendingOutbound(agentId: String, threadId: String?, message: AgentMessage) {
        pendingOutboundKeys(agentId, threadId).forEach { key ->
            val messages = pendingOutboundMessages.getOrPut(key) { mutableListOf() }
            if (messages.none { it.itemId == message.itemId }) messages.add(message)
        }
    }

    private fun forgetPendingOutbound(agentId: String, threadId: String?, message: AgentMessage) {
        pendingOutboundKeys(agentId, threadId).forEach { key ->
            pendingOutboundMessages[key]?.removeAll { it.itemId == message.itemId }
            if (pendingOutboundMessages[key]?.isEmpty() == true) pendingOutboundMessages.remove(key)
        }
    }

    private fun clearPendingOutbound(agentId: String, threadId: String?) {
        pendingOutboundKeys(agentId, threadId).forEach { pendingOutboundMessages.remove(it) }
    }

    private fun pendingOutboundFor(agent: Agent): List<AgentMessage> {
        val keys = pendingOutboundKeys(agent.id, agent.codexThreadId)
        return keys.flatMap { pendingOutboundMessages[it].orEmpty() }
            .distinctBy { it.itemId ?: it.contentKey().toString() }
    }

    private fun pruneConfirmedPendingOutbound(agent: Agent) {
        val confirmedUserMessages = agent.messages
            .filter { it.role == "user" || it.type == "user" }
            .map { simplifyUserMessageForDisplay(it.text, it.role, it.type).trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (confirmedUserMessages.isEmpty()) return
        pendingOutboundKeys(agent.id, agent.codexThreadId).forEach { key ->
            pendingOutboundMessages[key]?.removeAll { pending ->
                pending.text.trim() in confirmedUserMessages
            }
            if (pendingOutboundMessages[key]?.isEmpty() == true) pendingOutboundMessages.remove(key)
        }
    }

    private fun mergedAgents(nextAgents: List<Agent>): List<Agent> {
        val currentById = agents.associateBy { it.id }
        return nextAgents.map { rawIncoming ->
            val incoming = authoritativeSnapshot(rawIncoming)
            pruneConfirmedPendingOutbound(incoming)
            val current = currentById[incoming.id]
            if (current == null) {
                incoming
            } else {
                val status = mergedStatus(current, incoming)
                incoming.copy(
                    status = status,
                    messages = mergeMessagesForSnapshot(current.messages, incoming),
                    activity = mergedActivity(current, incoming, status),
                    updatedAt = mergedUpdatedAt(current, incoming),
                )
            }
        }
    }

    private fun mergedStatus(current: Agent, incoming: Agent): String {
        if (incoming.isBusy() || !current.isBusy()) return incoming.status
        if (incoming.hasTerminalProgressMessage()) return incoming.status

        val incomingIsOlderSnapshot = incoming.updatedAt <= current.updatedAt
        val currentWasJustWorking = System.currentTimeMillis() - current.updatedAt <= WORKING_STATUS_DOWNGRADE_GRACE_MS
        return if (incoming.hasStreamingProgressMessage() || (incomingIsOlderSnapshot && currentWasJustWorking)) {
            current.status
        } else {
            incoming.status
        }
    }

    private fun mergedActivity(current: Agent, incoming: Agent, mergedStatus: String): String? {
        return if (mergedStatus.trim().lowercase(Locale.ROOT) in setOf(
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
        ) {
            incoming.activity ?: current.activity
        } else {
            null
        }
    }

    private fun authoritativeSnapshot(agent: Agent): Agent {
        if (agent.isBusy()) return agent
        clearStreamingState(agent)
        return agent.asIdle()
    }

    private fun authoritativeDetailSnapshot(agent: Agent): Agent {
        if (!agent.isBusy() || agent.hasActiveProgressMessage()) return authoritativeSnapshot(agent)
        clearStreamingState(agent)
        return agent.copy(
            status = "可恢复",
            activity = null,
            messages = agent.messages.map { it.copy(streaming = false) },
        )
    }

    private fun clearStreamingState(agent: Agent) {
        streamingAgentIds.remove(agent.id)
        agent.codexThreadId?.let { threadId ->
            streamingAgentIds.remove(threadId)
            streamingAgentIds.remove("codex_$threadId")
        }
    }

    private fun Agent.asIdle(status: String = this.status, updatedAt: Long = this.updatedAt): Agent {
        clearStreamingState(this)
        return copy(
            status = status,
            activity = null,
            updatedAt = updatedAt,
            messages = messages.map { it.copy(streaming = false) },
        )
    }

    private fun mergeMessagesForSnapshot(currentMessages: List<AgentMessage>, incoming: Agent): List<AgentMessage> {
        val merged = mergeMessageLists(
            mergeMessageLists(currentMessages, pendingOutboundFor(incoming)),
            incoming.messages,
        )
        return if (incoming.isBusy()) merged else merged.map { it.copy(streaming = false) }
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
            if (agent.isBusy()) removeCompletedAlertsFor(agent)
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
        val authoritativeSummary = authoritativeSnapshot(summary)
        val authoritativeDetail = authoritativeDetailSnapshot(detail)
        val statusSource = if (authoritativeSummary.isBusy() && authoritativeDetail.isBusy()) {
            authoritativeDetail
        } else {
            authoritativeSummary
        }
        val messages = authoritativeDetail.messages.ifEmpty { summary.messages }
        return statusSource.copy(
            messages = if (statusSource.isBusy()) messages else messages.map { it.copy(streaming = false) },
            model = detail.model.ifBlank { summary.model },
            serviceTier = detail.serviceTier.ifBlank { summary.serviceTier },
            reasoningEffort = detail.reasoningEffort.ifBlank { summary.reasoningEffort },
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

    private fun Agent.hasActiveProgressMessage(): Boolean {
        return messages.asReversed().firstOrNull { it.role == "agent" }?.streaming == true
    }

    private fun Agent.hasStreamingProgressMessage(): Boolean {
        return messages.asReversed().firstOrNull { it.role == "agent" }?.streaming == true
    }

    private fun Agent.hasTerminalProgressMessage(): Boolean {
        val message = messages.asReversed().firstOrNull { it.role == "agent" } ?: return false
        if (message.type == "error") return true
        if (message.type != "status") return false
        val text = message.text.trim().lowercase(Locale.ROOT)
        return listOf(
            "task complete",
            "completed the task",
            "turn aborted",
            "task failed",
            "任务失败",
            "已完成任务",
            "已中止",
        ).any { marker -> marker in text }
    }

    private fun isNoisyAgentStderr(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf(
            "startup remote plugin sync failed",
            "failed to warm featured plugin ids cache",
            "chatgpt authentication required to sync remote plugins",
            "remote plugin sync request to https://chatgpt.com/backend-api/plugins/featured failed with status 403",
            "failed to create shell snapshot for powershell",
        ).any { marker -> marker in normalized }
    }

    private fun refreshActiveCodexThreadDetail() {
        val agent = activeAgent ?: return
        refreshCodexThreadDetail(agent, force = true)
    }

    private fun prefetchCodexThreadDetails(importedAgents: List<Agent>) {
        if (!appInForeground) return
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
            val detailed = authoritativeDetailSnapshot(parseCodexThreadDetail(detailJson, agent))
            syncPendingRequests(detailJson)
            val cached = detailedCodexThreads[threadId]
            val merged = cached?.let {
                detailed.copy(
                    messages = mergeMessagesForSnapshot(it.messages, detailed),
                    status = detailed.status,
                    activity = detailed.activity,
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

    private fun resumeCodexThread(
        agent: Agent,
        firstMessage: String? = null,
        firstDisplayMessage: String? = null,
        firstAttachments: List<AttachmentDraft> = emptyList(),
    ) {
        val threadId = agent.codexThreadId ?: return
        isBusy = true
        updateAgent(agent.id) { it.copy(status = "resuming", activity = "正在恢复 EasyCodex 任务") }
        val params = mutableMapOf<String, Any?>(
            "agentId" to agent.id,
            "name" to agent.name,
            "model" to agent.model.ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
            "cwd" to agent.cwd.ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } },
            "permissionMode" to normalizePermissionMode(agent.permissionMode),
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
                restoreDraftOnSendFailure(firstDisplayMessage ?: firstMessage, firstAttachments)
                updateAgent(agent.id) { it.copy(status = "error", activity = null) }
                return@send
            }
            data?.optJSONObject("data")?.let {
                val resumed = parseAgent(it)
                upsertAgentMerged(resumed)
                activeAgentId = resumed.id
                if (!firstMessage.isNullOrBlank()) sendMessageToAgent(
                    resumed.id,
                    firstMessage,
                    firstDisplayMessage ?: firstMessage,
                    firstAttachments,
                )
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

    fun interruptActiveAgent() {
        val agent = activeAgent ?: return
        if (!agent.isBusy()) return
        updateAgent(agent.id) { it.copy(activity = "正在请求中断当前任务") }
        send("interrupt", mapOf("agentId" to agent.id)) { _, error ->
            if (error == null) {
                appendMessage(agent.id, AgentMessage("agent", "status", "已请求中断当前任务。", System.currentTimeMillis()))
                updateAgent(agent.id) { it.copy(activity = "已请求中断，等待任务停止", updatedAt = System.currentTimeMillis()) }
            } else {
                statusText = strings.interruptFailed(error)
                updateAgent(agent.id) { it.copy(activity = agent.activity) }
            }
        }
    }

    fun openDiffReview(agent: Agent? = activeAgent) {
        if (agent == null) return
        val requestId = ++diffReviewRequestCounter
        diffReview = DiffReviewState(agentId = agent.id, cwd = agent.cwd, requestId = requestId)
        gitCommitDraft = GitCommitDraft(message = defaultCommitMessage(agent), files = emptyList())
        gitStatus(agent.cwd) { status, statusError ->
            if (diffReview?.requestId != requestId) return@gitStatus
            if (statusError != null) {
                diffReview = diffReview?.copy(loading = false, error = statusError)
                return@gitStatus
            }
            val changedFiles = status?.files.orEmpty()
            val initiallySelectedFiles = status?.restorableFiles.orEmpty().ifEmpty { changedFiles }
            gitCommitDraft = gitCommitDraft.copy(files = initiallySelectedFiles, error = null)
            gitDiff(agent.cwd) { diff, diffError ->
                if (diffReview?.requestId != requestId) return@gitDiff
                if (diffError != null) {
                    diffReview = diffReview?.copy(loading = false, status = status, error = diffError)
                    return@gitDiff
                }
                diffReview = diffReview?.copy(
                    loading = false,
                    status = status,
                    diff = diff.orEmpty(),
                    files = changedFiles.map(::gitStatusFileEntry),
                    selectedFiles = initiallySelectedFiles,
                )
            }
        }
    }

    fun dismissDiffReview() {
        diffReview = null
        gitCommitDraft = GitCommitDraft()
    }

    fun selectDiffReviewFile(path: String?) {
        val review = diffReview ?: return
        val requestId = ++diffReviewRequestCounter
        diffReview = review.copy(
            requestId = requestId,
            selectedFile = path,
            loading = true,
            fileLoading = !path.isNullOrBlank(),
            error = null,
            fileContent = "",
        )
        gitDiff(review.cwd, path) { diff, diffError ->
            if (diffReview?.requestId != requestId) return@gitDiff
            if (diffError != null) {
                diffReview = diffReview?.copy(loading = false, fileLoading = false, error = diffError)
                return@gitDiff
            }
            diffReview = diffReview?.copy(loading = false, diff = diff.orEmpty())
            if (path.isNullOrBlank()) {
                diffReview = diffReview?.copy(fileLoading = false)
                return@gitDiff
            }
            readFile(review.cwd, path) { content, readError ->
                if (diffReview?.requestId != requestId) return@readFile
                diffReview = diffReview?.copy(
                    fileLoading = false,
                    fileContent = content.orEmpty(),
                    error = readError,
                )
            }
        }
    }

    fun gitStatus(cwd: String, callback: (GitStatusSummary?, String?) -> Unit) {
        gitStatusResult(cwd) { result ->
            when (result) {
                is RelayResult.Success -> callback(result.value, null)
                is RelayResult.Failure -> callback(null, result.message)
            }
        }
    }

    fun gitStatusResult(cwd: String, callback: (RelayResult<GitStatusSummary>) -> Unit) {
        send("git_status", mapOf("cwd" to cwd.ifBlank { "." })) { data, error ->
            if (error != null) {
                callback(RelayResult.Failure(error))
                return@send
            }
            callback(RelayResult.Success(parseGitStatus(data?.optJSONObject("data") ?: JSONObject())))
        }
    }

    fun gitDiff(cwd: String, file: String? = null, callback: (String?, String?) -> Unit) {
        gitDiffResult(cwd, file) { result ->
            when (result) {
                is RelayResult.Success -> callback(result.value, null)
                is RelayResult.Failure -> callback(null, result.message)
            }
        }
    }

    fun gitDiffResult(cwd: String, file: String? = null, callback: (RelayResult<String>) -> Unit) {
        val params = mutableMapOf<String, Any?>("cwd" to cwd.ifBlank { "." })
        file?.takeIf { it.isNotBlank() }?.let { params["file"] = it }
        send("git_diff", params) { data, error ->
            if (error != null) callback(RelayResult.Failure(error))
            else callback(RelayResult.Success(data?.optJSONObject("data")?.optString("diff").orEmpty()))
        }
    }

    fun toggleDiffReviewFileSelection(path: String) {
        val review = diffReview ?: return
        val nextSelected = if (path in review.selectedFiles) {
            review.selectedFiles - path
        } else {
            review.selectedFiles + path
        }
        diffReview = review.copy(selectedFiles = nextSelected, restoreError = null)
        gitCommitDraft = gitCommitDraft.copy(files = nextSelected, error = null)
    }

    fun setDiffReviewFileSelection(files: List<String>) {
        val review = diffReview ?: return
        val available = review.files.mapTo(linkedSetOf()) { it.path }
        val nextSelected = files.filter { it in available }.distinct()
        diffReview = review.copy(selectedFiles = nextSelected, restoreError = null)
        gitCommitDraft = gitCommitDraft.copy(files = nextSelected, error = null)
    }

    fun refreshHostHealth() {
        if (connectionStatus != "connected") {
            hostHealth = HostHealthState(online = false, error = statusText, checkedAt = System.currentTimeMillis())
            return
        }
        hostHealth = hostHealth.copy(loading = true, error = null)
        send("host_health") { data, error ->
            val now = System.currentTimeMillis()
            if (error != null) {
                hostHealth = HostHealthState(online = false, error = localizedConnectionError(error), checkedAt = now)
                return@send
            }
            val next = parseHostHealth(data?.optJSONObject("data") ?: JSONObject(), now)
            hostHealth = next
            updateActiveHostProfile(next)
        }
    }

    private fun updateActiveHostProfile(health: HostHealthState) {
        val activeId = activeRelayHostId.ifBlank { relayHostIdFor(relayUrl) }
        val current = relayHostProfiles.firstOrNull { it.id == activeId }
            ?: RelayHostProfile(activeId, relayHostNameFor(relayUrl), relayUrl, apiKey)
        val updated = current.copy(
            name = health.hostname.ifBlank { current.name },
            hostname = health.hostname,
            platform = health.platform,
            workspaceRoot = health.workspaceRoot,
            lastSeen = health.checkedAt,
            warnings = health.warnings,
        )
        saveRelayHostProfile(prefs, updated, makeActive = true)
        activeRelayHostId = updated.id
        refreshRelayHostProfilesFromPrefs()
    }

    fun listFiles(cwd: String, path: String? = null, callback: (FileListing?, String?) -> Unit) {
        listFilesResult(cwd, path) { result ->
            when (result) {
                is RelayResult.Success -> callback(result.value, null)
                is RelayResult.Failure -> callback(null, result.message)
            }
        }
    }

    fun listFilesResult(cwd: String, path: String? = null, callback: (RelayResult<FileListing>) -> Unit) {
        send("list_files", mapOf("cwd" to cwd.ifBlank { "." }, "path" to path.orEmpty())) { data, error ->
            if (error != null) {
                callback(RelayResult.Failure(error))
                return@send
            }
            callback(RelayResult.Success(parseFileListing(data?.optJSONObject("data") ?: JSONObject())))
        }
    }

    fun readFile(cwd: String, path: String, callback: (String?, String?) -> Unit) {
        readFileResult(cwd, path) { result ->
            when (result) {
                is RelayResult.Success -> callback(result.value, null)
                is RelayResult.Failure -> callback(null, result.message)
            }
        }
    }

    fun readFileResult(cwd: String, path: String, callback: (RelayResult<String>) -> Unit) {
        send("read_file", mapOf("cwd" to cwd.ifBlank { "." }, "path" to path)) { data, error ->
            if (error != null) callback(RelayResult.Failure(error))
            else callback(RelayResult.Success(data?.optJSONObject("data")?.optString("content").orEmpty()))
        }
    }

    fun updateGitCommitMessage(message: String) {
        gitCommitDraft = gitCommitDraft.copy(message = message, error = null)
    }

    fun commitDiffReviewDraft() {
        val review = diffReview ?: return
        val draft = gitCommitDraft
        if (draft.files.isEmpty()) {
            gitCommitDraft = draft.copy(error = strings.noChangesToCommit)
            return
        }
        val message = draft.message.trim().ifBlank { "chore: update via EasyCodex mobile" }
        gitCommitDraft = draft.copy(message = message, busy = true, error = null)
        gitCommit(review.cwd, message, draft.files) { _, error ->
            if (error != null) {
                gitCommitDraft = gitCommitDraft.copy(busy = false, error = error)
                return@gitCommit
            }
            gitCommitDraft = GitCommitDraft(message = message, files = emptyList(), busy = false)
            statusText = strings.gitCommitComplete
            openDiffReview(activeAgent)
        }
    }

    fun gitCommit(cwd: String, message: String, files: List<String>, callback: (JSONObject?, String?) -> Unit) {
        gitCommitResult(cwd, message, files) { result ->
            when (result) {
                is RelayResult.Success -> callback(result.value, null)
                is RelayResult.Failure -> callback(null, result.message)
            }
        }
    }

    fun gitCommitResult(cwd: String, message: String, files: List<String>, callback: (RelayResult<JSONObject>) -> Unit) {
        send(
            "git_commit",
            mapOf(
                "cwd" to cwd.ifBlank { "." },
                "message" to message.ifBlank { "chore: update via EasyCodex mobile" },
                "files" to JSONArray(files),
            ),
        ) { data, error ->
            if (error != null) callback(RelayResult.Failure(error))
            else callback(RelayResult.Success(data?.optJSONObject("data") ?: JSONObject()))
        }
    }

    fun loadNotificationLevel(agentId: String = activeAgentId.orEmpty()) {
        if (agentId.isBlank()) return
        notificationLevelState = NotificationLevelState(agentId = agentId, loading = true)
        send("get_notification_prefs") { data, error ->
            if (notificationLevelState?.agentId != agentId) return@send
            if (error != null) {
                notificationLevelState = NotificationLevelState(agentId = agentId, loading = false, error = error)
                return@send
            }
            val prefs = data?.optJSONObject("data") ?: JSONObject()
            notificationLevelState = NotificationLevelState(
                agentId = agentId,
                level = normalizeNotificationLevel(prefs.optString(agentId, "all")),
                loading = false,
            )
        }
    }

    fun updateNotificationLevel(agentId: String, level: String) {
        val normalized = normalizeNotificationLevel(level)
        notificationLevelState = NotificationLevelState(agentId = agentId, level = normalized, loading = true)
        send("update_notification_prefs", mapOf("agentId" to agentId, "level" to normalized)) { _, error ->
            if (notificationLevelState?.agentId != agentId) return@send
            notificationLevelState = if (error != null) {
                NotificationLevelState(agentId = agentId, level = normalized, loading = false, error = error)
            } else {
                NotificationLevelState(agentId = agentId, level = normalized, loading = false)
            }
        }
    }

    fun respondApprovalRequest(request: AgentApprovalRequest, approved: Boolean) {
        if (connectionStatus != "connected") {
            queueApprovalResponse(request.agentId, request.id, approved)
            connect()
            statusText = "正在重连中继，审批响应会在连接恢复后发送"
            return
        }
        deliverApprovalResponse(request, approved)
    }

    private fun deliverApprovalResponse(request: AgentApprovalRequest, approved: Boolean) {
        send(
            "respond_agent_request",
            mapOf(
                "agentId" to request.agentId,
                "requestId" to request.id,
                "approved" to approved,
                "reason" to if (approved) "Approved from EasyCodex mobile" else "Denied from EasyCodex mobile",
            ),
        ) { _, error ->
            if (error != null) {
                statusText = "审批响应失败：$error"
            } else {
                approvalRequests.removeAll { it.id == request.id && it.agentId == request.agentId }
            }
        }
    }

    private fun queueApprovalResponse(agentId: String, requestId: String, approved: Boolean) {
        if (agentId.isBlank() || requestId.isBlank()) return
        pendingApprovalResponses.removeAll { it.agentId == agentId && it.requestId == requestId }
        pendingApprovalResponses.add(PendingApprovalResponse(agentId, requestId, approved, System.currentTimeMillis()))
        while (pendingApprovalResponses.size > 20) pendingApprovalResponses.removeAt(0)
    }

    private fun flushPendingApprovalResponses() {
        if (pendingApprovalResponses.isEmpty() || connectionStatus != "connected") return
        val responses = pendingApprovalResponses.toList()
        pendingApprovalResponses.clear()
        responses.forEach { response ->
            val request = approvalRequests.firstOrNull { it.agentId == response.agentId && it.id == response.requestId }
                ?: AgentApprovalRequest(
                    id = response.requestId,
                    agentId = response.agentId,
                    method = "approval",
                    title = "EasyCodex 需要确认",
                    detail = "",
                    timestamp = response.createdAt,
                )
            deliverApprovalResponse(request, response.approved)
        }
    }

    fun respondApprovalForAgentRequest(agentId: String, requestId: String, approved: Boolean) {
        val request = approvalRequests.firstOrNull { it.agentId == agentId && it.id == requestId }
            ?: approvalRequests.firstOrNull { it.agentId == agentId }
            ?: return
        respondApprovalRequest(request, approved)
    }

    fun restoreDiffReviewSelection() {
        val review = diffReview ?: return
        val restorable = review.status?.restorableFiles.orEmpty().ifEmpty { review.selectedFiles }
        val files = review.selectedFiles.filter { it in restorable }
        if (files.isEmpty()) {
            diffReview = review.copy(restoreError = "请选择已跟踪文件；未跟踪文件不能通过丢弃改动删除。")
            return
        }
        diffReview = review.copy(restoreBusy = true, restoreError = null)
        gitRestoreFiles(review.cwd, files) { error ->
            if (error != null) {
                diffReview = diffReview?.copy(restoreBusy = false, restoreError = error)
                return@gitRestoreFiles
            }
            statusText = "已丢弃选中文件改动"
            openDiffReview(activeAgent)
        }
    }

    fun gitRestoreFiles(cwd: String, files: List<String>, callback: (String?) -> Unit) {
        send(
            "git_restore_files",
            mapOf(
                "cwd" to cwd.ifBlank { "." },
                "files" to JSONArray(files),
            ),
        ) { _, error ->
            callback(error)
        }
    }

    fun respondUserInputRequest(request: AgentUserInputRequest, answers: Map<String, String>) {
        val agent = agents.firstOrNull { it.id == request.agentId }
        if (agent?.resumable == true && request.id.startsWith(CODEX_SESSION_USER_INPUT_PREFIX)) {
            val answerText = formatCodexSessionUserInputAnswer(request, answers)
            if (answerText.isBlank()) {
                statusText = "回答不能为空"
                return
            }
            userInputRequests.removeAll { it.id == request.id && it.agentId == request.agentId }
            selectAgent(request.agentId)
            resumeCodexThread(agent, answerText, "已回答：${request.detail.ifBlank { "Codex 提问" }}")
            return
        }
        val payload = JSONObject()
        answers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) payload.put(key, value)
        }
        send(
            "respond_agent_user_input",
            mapOf(
                "agentId" to request.agentId,
                "requestId" to request.id,
                "answers" to payload,
            ),
        ) { _, error ->
            if (error != null) {
                statusText = "回答发送失败：$error"
            } else {
                userInputRequests.removeAll { it.id == request.id && it.agentId == request.agentId }
            }
        }
    }

    fun deferUserInputRequest(request: AgentUserInputRequest) {
        userInputRequests.removeAll { it.id == request.id && it.agentId == request.agentId }
        selectAgent(request.agentId)
    }

    private fun formatCodexSessionUserInputAnswer(
        request: AgentUserInputRequest,
        answers: Map<String, String>,
    ): String {
        val lines = request.questions.mapNotNull { question ->
            val answer = answers[question.id].orEmpty().trim()
            if (answer.isBlank()) return@mapNotNull null
            val label = question.question.ifBlank { question.header.ifBlank { question.id } }
            "- $label\n  回答：$answer"
        }
        if (lines.isEmpty()) return ""
        return buildString {
            append("针对你刚才等待我确认的问题，我的回答如下：\n\n")
            append(lines.joinToString("\n"))
            append("\n\n请按这些选择继续执行。")
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
        val timeoutRunnable = Runnable {
            pendingTimeoutRunnables.remove(requestId)
            val timeoutCallback = pending.remove(requestId)
            timeoutCallback?.invoke(null, strings.connectionTimeout(relayUrl))
        }
        pendingTimeoutRunnables[requestId] = timeoutRunnable
        main.postDelayed(timeoutRunnable, RELAY_REQUEST_TIMEOUT_MS)
    }

    private fun syncClientLanguage() {
        sendRaw("update_client_language", mapOf("language" to resolvedAppLanguage(appLanguage))) { _, _ -> }
    }

    private fun scheduleReconnect(error: String, notifyDisconnected: Boolean = false) {
        if (relayUrl.isBlank() || apiKey.isBlank() || manuallyDisconnected) {
            connectionStatus = "disconnected"
            statusText = error
            return
        }
        val delayMillis = reconnectDelayMillis()
        reconnectAttempts += 1
        val seconds = delayMillis / 1000
        connectionStatus = "disconnected"
        statusText = disconnectedStatusMessage(seconds)
        if (notifyDisconnected) showConnectionDisconnectedNotification(seconds)
        cancelReconnect()
        reconnectRunnable = Runnable { connect() }
        main.postDelayed(reconnectRunnable!!, delayMillis)
    }

    private fun disconnectedStatusMessage(seconds: Long): String {
        val reconnecting = strings.reconnectingIn(seconds)
        return if (strings.settings == "Settings") {
            "${strings.connectionDisconnected}. $reconnecting"
        } else {
            "${strings.connectionDisconnected}，$reconnecting"
        }
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

    private fun scheduleAgentsRefresh(delayMillis: Long = AGENTS_REFRESH_DEBOUNCE_MS) {
        val effectiveDelay = if (appInForeground) {
            delayMillis
        } else {
            maxOf(delayMillis, BACKGROUND_AGENTS_REFRESH_DEBOUNCE_MS)
        }
        agentsRefreshRunnable?.let { main.removeCallbacks(it) }
        agentsRefreshRunnable = Runnable {
            agentsRefreshRunnable = null
            refreshAgents()
        }
        main.postDelayed(agentsRefreshRunnable!!, effectiveDelay.coerceAtLeast(0L))
    }

    private fun cancelAgentsRefresh() {
        agentsRefreshRunnable?.let { main.removeCallbacks(it) }
        agentsRefreshRunnable = null
    }

    private fun failPending(error: String) {
        cancelPendingTimeouts()
        val callbacks = pending.values.toList()
        pending.clear()
        callbacks.forEach { it(null, error) }
    }

    private fun cancelPendingTimeouts() {
        pendingTimeoutRunnables.values.forEach { main.removeCallbacks(it) }
        pendingTimeoutRunnables.clear()
    }

    private fun cancelThreadDetailRetries() {
        threadDetailRetryRunnables.values.forEach { main.removeCallbacks(it) }
        threadDetailRetryRunnables.clear()
        threadDetailRetryCounts.clear()
        threadDetailsInFlight.clear()
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

    private fun handleIncoming(msg: JSONObject) {
        val requestId = msg.optString("requestId")
        if (requestId.isNotBlank() && (msg.optString("type") == "response" || msg.optString("type") == "error")) {
            pendingTimeoutRunnables.remove(requestId)?.let { main.removeCallbacks(it) }
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
            .putString(PREF_LAST_STREAM_SESSION_ID, sessionId)
            .putLong(PREF_LAST_STREAM_SEQ, seq)
            .apply()
    }

    private fun handleStream(agentId: String, event: String, data: JSONObject) {
        if (agentId.isBlank()) return
        if (agentId == "cli") {
            handleCliStream(event, data)
            return
        }
        when (event) {
            "codex/threads_changed" -> {
                if (data.optString("reason") == "thread_archived") {
                    removeArchivedCodexThread(data.optString("threadId"), data.optString("agentId"))
                }
                scheduleRelayStateRefresh(immediate = true)
            }
            "agents/changed" -> {
                scheduleRelayStateRefresh(immediate = false)
            }
            "turn/started" -> {
                clearPendingAgentStatus(agentId)
                streamingAgentIds.add(agentId)
                val turnSerial = (activeTurnSerials[agentId] ?: completedTurnSerials[agentId] ?: 0L) + 1L
                activeTurnSerials[agentId] = turnSerial
                activeTurnStartedAt[agentId] = jsonTimestamp(data, "timestamp", System.currentTimeMillis())
                pendingPlanReviewCandidates.remove(agentId)
                updateAgent(agentId) { it.copy(status = "working", activity = "正在运行中，AI 正在接手任务", updatedAt = System.currentTimeMillis()) }
            }
            "turn/queued" -> {
                clearPendingAgentStatus(agentId)
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
                clearPendingAgentStatus(agentId)
                updateAgent(agentId) { it.copy(status = "working", activity = "队列任务已开始，正在运行中", updatedAt = System.currentTimeMillis()) }
            }
            "turn/completed" -> {
                clearPendingAgentStatus(agentId)
                streamingAgentIds.remove(agentId)
                completedTurnSerials[agentId] = activeTurnSerials[agentId] ?: ((completedTurnSerials[agentId] ?: 0L) + 1L)
                val completedAt = System.currentTimeMillis()
                val durationMs = turnDurationMs(data, agentId, completedAt)
                updateAgent(agentId) { it.asIdle(status = "ready", updatedAt = completedAt) }
                annotateLatestAgentMessageDuration(agentId, durationMs)
                activeTurnStartedAt.remove(agentId)
                maybePromptCompletedPlanReview(agentId)
                recordAgentAlert(agentId, AgentAlertKind.Completed, data.optString("preview"))
                scheduleAgentsRefresh()
            }
            "turn/failed" -> {
                clearPendingAgentStatus(agentId)
                streamingAgentIds.remove(agentId)
                activeTurnStartedAt.remove(agentId)
                val text = data.optJSONObject("error")?.optString("message") ?: "运行失败"
                appendMessage(agentId, AgentMessage("agent", "status", text, System.currentTimeMillis()))
                updateAgent(agentId) { it.asIdle(status = "error", updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Error, text)
                scheduleAgentsRefresh()
            }
            "agent/stopped" -> {
                clearPendingAgentStatus(agentId)
                streamingAgentIds.remove(agentId)
                updateAgent(agentId) { it.asIdle(status = "stopped", updatedAt = System.currentTimeMillis()) }
                val code = data.optString("code")
                if (code.isNotBlank() && code != "0" && code != "null") {
                    recordAgentAlert(agentId, AgentAlertKind.Error, "任务进程已退出，退出码 $code")
                }
                scheduleAgentsRefresh()
            }
            "agent/stderr" -> {
                clearPendingAgentStatus(agentId)
                val text = data.optString("text")
                if (text.isNotBlank() && !isNoisyAgentStderr(text)) {
                    appendMessage(agentId, AgentMessage("agent", "status", text, System.currentTimeMillis()))
                }
            }
            "agent/requested" -> {
                clearPendingAgentStatus(agentId)
                val requestId = data.optString("requestId")
                if (requestId.isNotBlank()) {
                    val method = data.optString("method").ifBlank { "approval" }
                    val text = requestText(data, method)
                    upsertPendingRequest(agentId, requestId, method, data.optJSONObject("params"), text, data.optLong("timestamp", System.currentTimeMillis()), announce = true)
                    finalizeMessage(agentId, "request_$requestId", text, "status", streaming = true)
                    updateAgent(agentId) { it.copy(status = "working", activity = if (isUserInputRequest(method)) "正在等待你回答问题" else "正在等待你的确认", updatedAt = System.currentTimeMillis()) }
                }
            }
            "agent/request_resolved" -> {
                val requestId = data.optString("requestId")
                approvalRequests.removeAll { it.id == requestId && it.agentId == agentId }
                userInputRequests.removeAll { it.id == requestId && it.agentId == agentId }
                updateAgent(agentId) { it.copy(activity = "确认已发送，等待 Codex 继续执行", updatedAt = System.currentTimeMillis()) }
            }
            "item/started" -> {
                clearPendingAgentStatus(agentId)
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item)
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = true)
                if (itemId.isNotBlank()) finalizeMessage(agentId, itemId, text, type, streaming = true, metadata = messageMetadata(item, type))
                updateAgent(agentId) {
                    it.copy(
                        status = "working",
                        activity = activityForMessageType(type, started = true),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
            "item/agentMessage/delta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "agent")
                updateAgentActivityThrottled(agentId, "正在生成回复")
            }
            "item/reasoning/delta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，推理内容持续返回")
            }
            "item/reasoning/textDelta",
            "item/reasoning/summaryTextDelta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，整理执行步骤")
            }
            "item/reasoning/summaryPartAdded" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = listOf("text", "summary", "part", "content")
                    .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
                    .orEmpty()
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "thinking")
                updateAgentActivityThrottled(agentId, "正在思考中，整理执行步骤")
            }
            "item/commandOutput/delta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "正在运行命令，输出持续返回")
            }
            "item/commandExecution/outputDelta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "正在运行命令，检查执行结果")
            }
            "item/fileChange/delta",
            "item/fileChange/outputDelta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "file_change")
                updateAgentActivityThrottled(agentId, "正在修改文件，改动内容持续更新")
            }
            "item/fileChange/patchUpdated" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val text = fileChangesText(data.optJSONArray("changes"))
                    .ifBlank { data.optJSONObject("item")?.let { streamItemText(it, "file_change", started = false) }.orEmpty() }
                if (itemId.isNotBlank() && text.isNotBlank()) finalizeMessage(agentId, itemId, text, "file_change")
            }
            "item/plan/delta" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = streamDeltaText(data)
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "plan")
                updateAgentActivityThrottled(agentId, "正在规划步骤，准备继续执行")
            }
            "item/mcpToolCall/progress" -> {
                clearPendingAgentStatus(agentId)
                val itemId = data.optString("itemId").ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                val delta = data.optString("message").let { if (it.isNotBlank()) "$it\n" else "" }
                if (itemId.isNotBlank() && delta.isNotBlank()) appendDelta(agentId, itemId, delta, "command_output")
                updateAgentActivityThrottled(agentId, "AI 正在使用工具，等待工具进度返回")
            }
            "item/completed" -> {
                clearPendingAgentStatus(agentId)
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item)
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (itemId.isNotBlank() && text.isNotBlank()) finalizeMessage(agentId, itemId, text, type, metadata = messageMetadata(item, type))
            }
            "rawResponseItem/completed" -> {
                clearPendingAgentStatus(agentId)
                val item = data.optJSONObject("item") ?: return
                val itemId = itemId(item).ifBlank { "raw_${System.currentTimeMillis()}" }
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (text.isNotBlank()) finalizeMessage(agentId, itemId, text, type, metadata = messageMetadata(item, type))
            }
            "response_item" -> {
                clearPendingAgentStatus(agentId)
                val item = data.optJSONObject("payload") ?: data
                val itemId = itemId(item).ifBlank { "response_${System.currentTimeMillis()}" }
                val type = messageType(item.optString("type"))
                val text = streamItemText(item, type, started = false)
                if (text.isNotBlank()) finalizeMessage(agentId, itemId, text, type, metadata = messageMetadata(item, type))
            }
            "turn/diff/updated" -> {
                clearPendingAgentStatus(agentId)
                val turnId = data.optString("turnId").ifBlank { data.optString("turn_id") }.ifBlank { "turn_${System.currentTimeMillis()}" }
                val text = data.optString("diff")
                if (text.isNotBlank()) finalizeMessage(agentId, "turn_diff_$turnId", text, "file_change")
            }
            "turn/plan/updated" -> {
                clearPendingAgentStatus(agentId)
                val turnId = data.optString("turnId").ifBlank { data.optString("turn_id") }.ifBlank { "turn_${System.currentTimeMillis()}" }
                val text = listOf(data.optString("explanation"), planText(data.optJSONArray("plan")))
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                if (text.isNotBlank()) finalizeMessage(agentId, "plan_$turnId", text, "plan")
            }
            "thread/tokenUsage/updated" -> {
                return
            }
            "event_msg" -> {
                clearPendingAgentStatus(agentId)
                handleEventMessage(agentId, data.optJSONObject("payload") ?: data)
            }
            else -> {
                if (event.contains("delta", ignoreCase = true)) {
                    val itemId = data.optString("itemId")
                        .ifBlank { data.optJSONObject("item")?.let { itemId(it) }.orEmpty() }
                        .ifBlank { "stream_${event.hashCode()}" }
                    val delta = streamDeltaText(data)
                    val type = messageType(event)
                    if (delta.isNotBlank()) appendDelta(agentId, itemId, delta, type)
                }
            }
        }
    }

    private fun scheduleRelayStateRefresh(immediate: Boolean) {
        if (appInForeground) {
            scheduleActiveCodexThreadDetailRefresh(if (immediate) RELAY_STATE_DETAIL_REFRESH_DEBOUNCE_MS else AGENTS_REFRESH_DEBOUNCE_MS)
            scheduleAgentsRefresh(if (immediate) AGENTS_REFRESH_DEBOUNCE_MS else AGENTS_REFRESH_DEBOUNCE_MS)
        } else {
            scheduleAgentsRefresh(BACKGROUND_AGENTS_REFRESH_DEBOUNCE_MS)
        }
    }

    private fun scheduleActiveCodexThreadDetailRefresh(delayMillis: Long) {
        relayDetailRefreshRunnable?.let { main.removeCallbacks(it) }
        relayDetailRefreshRunnable = Runnable {
            relayDetailRefreshRunnable = null
            refreshActiveCodexThreadDetail()
        }
        main.postDelayed(relayDetailRefreshRunnable!!, delayMillis.coerceAtLeast(0L))
    }

    private fun cancelRelayDetailRefresh() {
        relayDetailRefreshRunnable?.let { main.removeCallbacks(it) }
        relayDetailRefreshRunnable = null
    }

    private fun handleCliStream(event: String, data: JSONObject) {
        val windowId = data.optString("windowId").takeIf { it.isNotBlank() } ?: cliConsole.activeWindowId
        ensureCliWindow(windowId)
        when (event) {
            "cli/started" -> {
                val runId = data.optString("runId").takeIf { it.isNotBlank() }
                val window = cliWindow(windowId)
                val cwd = data.optString("cwd", window.cwd).ifBlank { window.cwd }
                updateCliWindow(windowId) { current ->
                    current.copy(
                        running = true,
                        busy = true,
                        runId = runId,
                        cwd = cwd,
                        model = data.optString("model", current.model).ifBlank { current.model },
                        reasoningEffort = data.optString("reasoningEffort", current.reasoningEffort).ifBlank { current.reasoningEffort },
                        sandboxMode = data.optString("sandboxMode", current.sandboxMode).ifBlank { current.sandboxMode },
                        skipGitRepoCheck = data.optBoolean("skipGitRepoCheck", current.skipGitRepoCheck),
                        mode = data.optString("mode", current.mode).ifBlank { current.mode },
                        jsonOutput = data.optBoolean("structured", current.jsonOutput),
                    )
                }
                val command = data.optString("command").trim()
                appendCliLine(
                    "status",
                    if (command.isNotBlank()) {
                        "Codex CLI 已启动\ncommand: $command\ncwd: $cwd\nstatus: running"
                    } else {
                        "Codex CLI 已启动\ncwd: $cwd\nstatus: running"
                    },
                    windowId,
                )
            }

            "cli/output" -> {
                val chunk = data.optString("chunk")
                if (chunk.isNotBlank()) appendCliOutput(chunk, data.optString("stream", "stdout"), windowId)
            }

            "cli/status" -> {
                val chunk = data.optString("chunk").ifBlank { data.optString("title") }
                if (chunk.isNotBlank()) appendCliLine("status", chunk, windowId)
            }

            "cli/final" -> {
                val chunk = data.optString("finalText").ifBlank { data.optString("chunk") }
                if (chunk.isNotBlank()) appendCliLine("stdout", chunk, windowId)
            }

            "cli/exited" -> {
                val code = data.optString("code").takeIf { it.isNotBlank() && it != "null" } ?: "0"
                val durationMs = data.optLong("durationMs", 0L)
                val succeeded = code == "0"
                updateCliWindow(windowId) { it.copy(running = false, busy = false, runId = null) }
                appendCliLine(
                    "status",
                    if (succeeded) {
                        "Codex CLI 已完成\ncwd: ${data.optString("cwd", cliWindow(windowId).cwd)}\nexit: $code\nstatus: completed${formatCliDuration(durationMs)}"
                    } else {
                        "Codex CLI 已结束\ncwd: ${data.optString("cwd", cliWindow(windowId).cwd)}\nexit: $code\nstatus: failed${formatCliDuration(durationMs)}"
                    },
                    windowId,
                )
            }

            "cli/failed" -> {
                val error = data.optString("error", "unknown error")
                updateCliWindow(windowId) { it.copy(running = false, busy = false, runId = null) }
                appendCliLine("status", "Codex CLI 运行失败\nerror: $error\nstatus: failed", windowId)
            }
        }
    }

    private fun appendCliLine(role: String, text: String, windowId: String = cliConsole.activeWindowId) {
        if (text.isBlank()) return
        flushCliOutputs(windowId)
        val line = CliConsoleLine(
            id = "cli_${System.currentTimeMillis()}_${UUID.randomUUID()}",
            role = role,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        updateCliWindow(windowId) {
            val nextLines = it.lines + line
            it.copy(lines = nextLines.takeLast(120), truncated = it.truncated || nextLines.size > 120)
        }
    }

    private fun appendCliOutput(chunk: String, stream: String, windowId: String) {
        val normalizedRole = if (stream == "stderr") "diagnostic" else "stdout"
        val key = cliOutputKey(windowId, normalizedRole)
        val pendingOutput = pendingCliOutputs[key]
        if (pendingOutput == null) {
            pendingCliOutputs[key] = PendingCliOutput(windowId, normalizedRole).also {
                appendCappedDelta(it.text, chunk, "command_output")
            }
        } else {
            appendCappedDelta(pendingOutput.text, chunk, "command_output")
        }
        scheduleCliOutputFlush()
    }

    private fun applyCliOutput(windowId: String, role: String, text: String) {
        if (text.isBlank()) return
        val window = cliWindow(windowId)
        val lines = window.lines.toMutableList()
        val lastIndex = lines.indexOfLast { it.role == role && it.streaming }
        if (lastIndex >= 0) {
            val current = lines[lastIndex]
            lines[lastIndex] = current.copy(text = capMobileMessageText(current.text + text, "command_output"))
        } else {
            lines.add(
                CliConsoleLine(
                    id = "cli_${System.currentTimeMillis()}_${UUID.randomUUID()}",
                    role = role,
                    text = capMobileMessageText(text, "command_output"),
                    timestamp = System.currentTimeMillis(),
                    streaming = true,
                ),
            )
        }
        updateCliWindow(windowId) { it.copy(lines = lines.takeLast(120), truncated = it.truncated || lines.size > 120) }
    }

    private fun scheduleCliOutputFlush() {
        if (cliOutputFlushRunnable != null) return
        cliOutputFlushRunnable = Runnable {
            cliOutputFlushRunnable = null
            flushCliOutputs()
        }
        val delayMs = if (appInForeground) CLI_OUTPUT_FLUSH_MS else BACKGROUND_STREAM_DELTA_FLUSH_MS
        main.postDelayed(cliOutputFlushRunnable!!, delayMs)
    }

    private fun flushCliOutputs(windowId: String? = null) {
        if (pendingCliOutputs.isEmpty()) return
        val outputs = pendingCliOutputs.values
            .filter { windowId == null || it.windowId == windowId }
            .toList()
        outputs.forEach { output ->
            pendingCliOutputs.remove(cliOutputKey(output.windowId, output.role))
            applyCliOutput(output.windowId, output.role, output.text.toString())
        }
        if (pendingCliOutputs.isEmpty()) {
            cliOutputFlushRunnable?.let { main.removeCallbacks(it) }
            cliOutputFlushRunnable = null
        }
    }

    private fun cancelCliOutputFlush() {
        cliOutputFlushRunnable?.let { main.removeCallbacks(it) }
        cliOutputFlushRunnable = null
        pendingCliOutputs.clear()
    }

    private fun cliOutputKey(windowId: String, role: String): String = "$windowId\u0000$role"

    private fun cliWindow(windowId: String): CliConsoleWindow {
        return cliConsole.windows.firstOrNull { it.id == windowId } ?: cliConsole.activeWindow
    }

    private fun ensureCliWindow(windowId: String) {
        if (windowId.isBlank() || cliConsole.windows.any { it.id == windowId }) return
        val active = cliConsole.activeWindow
        val next = CliConsoleWindow(
            id = windowId,
            title = "CLI ${cliConsole.windows.size + 1}",
            cwd = active.cwd,
            model = active.model,
            reasoningEffort = active.reasoningEffort,
            sandboxMode = active.sandboxMode,
            skipGitRepoCheck = active.skipGitRepoCheck,
            mode = active.mode,
            sessionId = active.sessionId,
            reviewTarget = active.reviewTarget,
            profile = active.profile,
            images = active.images,
            addDirs = active.addDirs,
            jsonOutput = active.jsonOutput,
            ephemeral = active.ephemeral,
            ignoreRules = active.ignoreRules,
            version = active.version,
        )
        cliConsole = cliConsole.copy(windows = cliConsole.windows + next)
    }

    private fun updateActiveCliWindow(transform: (CliConsoleWindow) -> CliConsoleWindow) {
        updateCliWindow(cliConsole.activeWindowId, transform)
    }

    private fun updateCliWindow(windowId: String, transform: (CliConsoleWindow) -> CliConsoleWindow) {
        val windows = cliConsole.windows.map { window ->
            if (window.id == windowId) transform(window) else window
        }
        cliConsole = cliConsole.copy(windows = windows)
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
                updateAgent(agentId) { it.asIdle(status = "ready", updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, classifyCompletionAlert(text), text)
                scheduleAgentsRefresh()
            }
            "error" -> {
                val text = payload.optString("message").ifBlank { payload.optString("error") }.ifBlank { "运行失败" }
                finalizeMessage(agentId, "error_${System.currentTimeMillis()}", text, "status")
                updateAgent(agentId) { it.asIdle(status = "error", updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Error, text)
            }
            "turn_aborted" -> {
                val reason = payload.optString("reason")
                val text = if (reason.isNotBlank()) "任务已中止：$reason" else "任务已中止"
                finalizeMessage(agentId, "aborted_${System.currentTimeMillis()}", text, "status")
                streamingAgentIds.remove(agentId)
                updateAgent(agentId) { it.asIdle(status = "ready", updatedAt = System.currentTimeMillis()) }
                recordAgentAlert(agentId, AgentAlertKind.Confirmation, text)
            }
            "exec_command_begin" -> {
                val itemId = itemId(payload).ifBlank { "exec_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, eventCommandText(payload), "command", streaming = true)
            }
            "exec_command_end",
            "mcp_tool_call_end" -> {
                val itemId = itemId(payload).ifBlank { "output_${System.currentTimeMillis()}" }
                finalizeMessage(agentId, itemId, "命令输出已省略。", "command_output")
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
                return
            }
            "web_search_end" -> {
                finalizeMessage(agentId, "web_${System.currentTimeMillis()}", "网页搜索已完成，详细结果已省略。", "command_output")
            }
        }
    }

    private fun recordAgentAlert(
        agentId: String,
        kind: AgentAlertKind,
        detail: String = "",
        notify: Boolean = true,
        requestId: String = "",
    ) {
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
            requestId = requestId,
        )
        alerts.add(0, alert)
        while (alerts.size > 60) alerts.removeAt(alerts.lastIndex)
        if (notify) showAgentSystemNotification(alert)
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

    private fun showConnectionDisconnectedNotification(seconds: Long) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureConnectionStatusChannel(manager)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                CONNECTION_STATUS_NOTIFICATION_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val detail = "${strings.connectionDisconnectedNotificationBody} ${strings.reconnectingIn(seconds)}"
            val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
            val notification = Notification.Builder(context, CONNECTION_STATUS_CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(strings.connectionDisconnected)
                .setContentText(detail)
                .setStyle(Notification.BigTextStyle().bigText(detail))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_STATUS)
                .build()
            manager.notify(CONNECTION_STATUS_NOTIFICATION_ID, notification)
        }
    }

    private fun cancelConnectionDisconnectedNotification() {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(CONNECTION_STATUS_NOTIFICATION_ID)
        }
    }

    private fun ensureConnectionStatusChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CONNECTION_STATUS_CHANNEL_ID,
            "EasyCodex 连接状态",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "连接断开和自动重连提醒"
        }
        manager.createNotificationChannel(channel)
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
            val notificationId = AGENT_ALERT_NOTIFICATION_BASE_ID + (alert.agentId.hashCode() and 0x0FFF)
            val builder = Notification.Builder(context, AGENT_ALERT_CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(alert.title)
                .setContentText(alert.detail)
                .setStyle(Notification.BigTextStyle().bigText(alert.detail))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(alert.timestamp)
                .setCategory(Notification.CATEGORY_STATUS)
            agentNotificationActionSpecs(alert.kind, canApprove = alert.requestId.isNotBlank(), strings = strings).forEach { spec ->
                val action = when (spec.kind) {
                    AgentNotificationActionKind.QuickReply -> quickReplyAction(
                        context = context,
                        agentId = alert.agentId,
                        notificationId = notificationId,
                        title = spec.title,
                        inputLabel = strings.notificationQuickInputLabel,
                    )
                    AgentNotificationActionKind.PresetReply -> presetReplyAction(
                        context = context,
                        agentId = alert.agentId,
                        notificationId = notificationId,
                        title = spec.title,
                        text = spec.presetText,
                    )
                    AgentNotificationActionKind.Approval -> approvalAction(
                        context = context,
                        agentId = alert.agentId,
                        requestId = alert.requestId,
                        notificationId = notificationId,
                        title = spec.title,
                        approved = spec.approved,
                    )
                    AgentNotificationActionKind.Dismiss -> dismissAgentNotificationAction(
                        context = context,
                        agentId = alert.agentId,
                        notificationId = notificationId,
                        title = spec.title,
                    )
                }
                builder.addAction(action)
            }
            manager.notify(notificationId, builder.build())
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
            strings.agentAlertChannel,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = strings.agentAlertChannelDescription
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

    private fun messageMetadata(item: JSONObject, type: String): AgentMessage {
        val detailText = messageDetailText(item, type)
        if (type != "sub_agent") {
            return AgentMessage("agent", type, "", System.currentTimeMillis(), detailText = detailText)
        }
        val content = item.optJSONObject("result") ?: item.optJSONObject("output") ?: JSONObject()
        val threadId = listOf("subAgentThreadId", "threadId", "thread_id", "agentThreadId", "agent_thread_id")
            .firstNotNullOfOrNull { key -> item.optString(key).ifBlank { content.optString(key) }.takeIf { it.isNotBlank() } }
            .orEmpty()
        val nickname = listOf("subAgentNickname", "agent_nickname", "agentNickname", "nickname")
            .firstNotNullOfOrNull { key -> item.optString(key).ifBlank { content.optString(key) }.takeIf { it.isNotBlank() } }
            .orEmpty()
        val role = listOf("subAgentRole", "agent_role", "agentRole")
            .firstNotNullOfOrNull { key -> item.optString(key).ifBlank { content.optString(key) }.takeIf { it.isNotBlank() } }
            .orEmpty()
        val status = item.optString("subAgentStatus")
            .ifBlank { item.optString("status") }
            .ifBlank { content.optString("status") }
        return AgentMessage(
            role = "agent",
            type = type,
            text = "",
            timestamp = System.currentTimeMillis(),
            subAgentThreadId = threadId,
            subAgentNickname = nickname,
            subAgentStatus = status,
            subAgentRole = role,
            toolCallId = itemId(item),
            detailText = detailText,
        )
    }

    private fun messageDetailText(item: JSONObject, type: String): String {
        val explicit = itemValueText(item, "detailText").ifBlank { itemValueText(item, "detail_text") }
        if (explicit.isNotBlank()) return explicit
        val keys = when (type) {
            "command" -> listOf("command", "cmd", "shell", "input", "text", "message", "arguments", "args")
            "command_output" -> listOf("output", "aggregatedOutput", "aggregated_output", "stdout", "stderr", "text", "message", "content")
            "file_change" -> listOf("diff", "patch", "text", "message", "changes")
            "sub_agent" -> listOf("output", "result", "error", "text", "message", "content")
            "screenshot" -> listOf("text", "path", "file", "url", "source", "message", "summary")
            "test_result" -> listOf("command", "output", "aggregatedOutput", "aggregated_output", "stdout", "stderr", "text", "message", "summary")
            "plugin_activity" -> listOf("tool", "name", "status", "output", "text", "message", "summary")
            else -> emptyList()
        }
        val body = keys.firstNotNullOfOrNull { key -> itemValueText(item, key).takeIf { it.isNotBlank() } }.orEmpty()
        if (body.isBlank()) return ""
        return itemDetailPrefix(item, type).let { prefix ->
            listOf(prefix, body).filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }

    private fun itemValueText(item: JSONObject, key: String): String {
        if (!item.has(key) || item.isNull(key)) return ""
        val value = item.opt(key)
        return when (value) {
            null, JSONObject.NULL -> ""
            is String -> value
            else -> jsonSummary(value)
        }
    }

    private fun activityForMessageType(type: String, started: Boolean): String {
        return when (type) {
            "command" -> if (started) "正在运行命令，等待执行结果" else "命令执行完成"
            "command_output" -> "正在读取命令输出"
            "file_change" -> if (started) "正在修改文件，准备生成改动" else "文件改动已更新"
            "sub_agent" -> if (started) "子代理正在工作，等待返回结果" else "子代理结果已返回"
            "screenshot" -> "截图已生成，手机端可预览"
            "test_result" -> "测试结果已返回"
            "plugin_activity" -> "插件/技能活动已更新"
            "thinking" -> "正在思考中，AI 正在使用中"
            "plan" -> "正在规划步骤，准备继续执行"
            "status" -> "状态已更新，等待下一步反馈"
            else -> if (started) "正在运行中，等待 AI 反馈" else "运行阶段已更新"
        }
    }

    private fun streamItemText(item: JSONObject, type: String, started: Boolean): String {
        when (type) {
            "command" -> return commandSummaryFromItem(item, if (started) "运行命令" else "命令已完成")
            "command_output" -> return commandSummaryFromItem(item, "命令已完成").ifBlank { "命令已完成，输出已省略。" }
            "file_change" -> {
                val changes = fileChangesText(item.optJSONArray("changes")).ifBlank { fileChangesText(item.optJSONObject("changes")) }
                if (changes.isNotBlank()) return changes
                val path = item.optString("path").ifBlank { item.optString("file") }.ifBlank { item.optString("filePath") }
                if (path.isNotBlank()) return "文件改动\n- $path"
                val direct = listOf("text", "diff", "patch", "message")
                    .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
                if (!direct.isNullOrBlank()) return direct
                return if (started) "正在修改文件。" else "文件已修改。"
            }
            "sub_agent" -> {
                val status = item.optString("subAgentStatus").ifBlank { item.optString("status") }.lowercase(Locale.ROOT)
                val label = when {
                    status in setOf("failed", "errored") -> "子代理失败"
                    started || status in setOf("inprogress", "running", "pendinginit") -> "子代理正在工作"
                    else -> "子代理已完成"
                }
                val name = item.optString("subAgentNickname")
                    .ifBlank { item.optString("agent_nickname") }
                    .ifBlank { item.optString("nickname") }
                    .ifBlank { item.optString("subAgentRole") }
                    .ifBlank { item.optString("agent_role") }
                return listOf(label, name).filter { it.isNotBlank() }.joinToString(" · ")
            }
            "screenshot" -> return artifactText(item, "screenshot", "Screenshot")
            "test_result" -> return artifactText(item, "command", "test/check")
            "plugin_activity" -> return artifactText(item, "tool", "plugin/skill")
            "thinking" -> return "正在思考中。"
        }
        if (type == "plan") {
            val structuredPlan = listOf(
                item.optString("explanation"),
                planText(item.optJSONArray("plan")).ifBlank { planText(item.optJSONArray("steps")) },
            )
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (structuredPlan.isNotBlank()) return structuredPlan
        }
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

    private fun commandSummaryFromItem(item: JSONObject, label: String): String {
        val command = listOf("command", "cmd", "shell", "input", "tool", "name")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            ?.readableCommandSummary()
            .orEmpty()
        val exit = when {
            item.has("exitCode") && !item.isNull("exitCode") -> "exit ${item.optInt("exitCode")}"
            item.has("exit_code") && !item.isNull("exit_code") -> "exit ${item.optInt("exit_code")}"
            else -> ""
        }
        val duration = item.optLong("durationMs", -1).takeIf { it >= 0 }?.let { "${it}ms" }
            ?: item.optLong("duration_ms", -1).takeIf { it >= 0 }?.let { "${it}ms" }
            ?: ""
        val meta = listOf(exit, duration).filter { it.isNotBlank() }.joinToString(" · ")
        if (command.isBlank() && meta.isBlank() && label == "命令已完成") return "命令已完成，输出已省略。"
        return listOf(label, command, meta).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun streamDeltaText(data: JSONObject): String {
        return listOf("delta", "text", "message", "content", "output")
            .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
            .orEmpty()
    }

    private fun artifactText(item: JSONObject, primaryKey: String, fallbackTitle: String): String {
        val title = listOf(primaryKey, "title", "name", "command", "tool")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            ?: fallbackTitle
        val source = listOf("source", "path", "file", "url")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            .orEmpty()
        val status = listOf("status", "result", "outcome")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            .orEmpty()
        val output = listOf("summary", "output", "aggregatedOutput", "aggregated_output", "stdout", "stderr", "text", "message")
            .firstNotNullOfOrNull { key -> itemValueText(item, key).takeIf { it.isNotBlank() } }
            .orEmpty()
        val prefix = when (primaryKey) {
            "screenshot" -> "screenshot"
            "tool" -> "tool"
            else -> "command"
        }
        return listOf(
            "$prefix: $title",
            source.takeIf { it.isNotBlank() }?.let { "source: $it" }.orEmpty(),
            status.takeIf { it.isNotBlank() }?.let { "status: $it" }.orEmpty(),
            output,
        ).filter { it.isNotBlank() }.joinToString("\n")
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
            val stats = diffStats(change.optString("diff").ifBlank { change.optString("unified_diff") }.ifBlank { change.optString("content") })
            blocks.add(fileChangeSummaryLine(path, kind, stats))
        }
        return compactFileChangeBlocks(blocks)
    }

    private fun fileChangesText(obj: JSONObject?): String {
        if (obj == null) return ""
        val blocks = mutableListOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            val change = obj.optJSONObject(path)
            if (change == null) {
                blocks.add("- $path")
                continue
            }
            val kind = change.optString("kind").ifBlank { change.optString("type") }
            val stats = diffStats(change.optString("diff").ifBlank { change.optString("unified_diff") }.ifBlank { change.optString("content") })
            blocks.add(fileChangeSummaryLine(path, kind, stats))
        }
        return compactFileChangeBlocks(blocks)
    }

    private fun fileChangeSummaryLine(path: String, kind: String, stats: Pair<Int, Int>): String {
        if (path.isBlank()) return ""
        val statText = if (stats.first + stats.second > 0) " (+${stats.first} -${stats.second})" else ""
        val kindText = kind.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        return "- $path$statText$kindText"
    }

    private fun compactFileChangeBlocks(blocks: List<String>): String {
        val visible = blocks.filter { it.isNotBlank() }
        if (visible.isEmpty()) return ""
        val lines = visible.take(8).toMutableList()
        if (visible.size > lines.size) lines.add("- 另有 ${visible.size - lines.size} 个文件")
        return "文件改动\n${lines.joinToString("\n")}"
    }

    private fun String.compactMobileLine(limit: Int): String {
        val singleLine = stripAnsi().trim().replace(Regex("\\s+"), " ")
        if (singleLine.length <= limit) return singleLine
        return singleLine.take(limit).trimEnd() + "..."
    }

    private fun String.stripAnsi(): String {
        return replace(Regex("""\u001B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])"""), "")
    }

    private fun String.readableCommandSummary(): String {
        var command = stripAnsi().trim()
        Regex("""^"[^"]*\\(?:pwsh|powershell)(?:\.exe)?"\s+-Command\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(command)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { command = it.trim() }
        Regex("""^(?:pwsh|powershell)(?:\.exe)?\s+-Command\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(command)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { command = it.trim() }
        command = command.trim('"', '\'').replace("\\\"", "\"").replace("\\\\", "\\")
        Regex("""\bGet-Content\s+-Path\s+(['"]?)([^'"\r\n|]+)\1""", RegexOption.IGNORE_CASE)
            .find(command)
            ?.groupValues
            ?.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { return "读取文件 ${it.compactPathForMobile()}" }
        Regex("""\brg\s+(?:-[^\s]+\s+)*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
            .find(command)
            ?.groupValues
            ?.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { return "搜索 ${it.compactMobileLine(72)}" }
        if (command.startsWith("apply_patch", ignoreCase = true) || command.contains("*** Begin Patch")) return "应用补丁"
        return command.compactMobileLine(120)
    }

    private fun String.compactPathForMobile(limit: Int = 72): String {
        val normalized = trim().trim('"', '\'').replace('\\', '/')
        if (normalized.length <= limit) return normalized
        val tail = normalized.split('/').filter { it.isNotBlank() }.takeLast(2).joinToString("/")
        if (tail.length + 4 < limit) return ".../$tail"
        return normalized.take((limit - 15).coerceAtLeast(12)).trimEnd() + "..." + normalized.takeLast(12)
    }

    private fun diffStats(diff: String): Pair<Int, Int> {
        if (diff.isBlank()) return 0 to 0
        var additions = 0
        var deletions = 0
        diff.lineSequence().forEach { line ->
            if (line.startsWith("+") && !line.startsWith("+++")) additions += 1
            if (line.startsWith("-") && !line.startsWith("---")) deletions += 1
        }
        return additions to deletions
    }

    private fun fileChangeLiveText(agentId: String, itemId: String, delta: String): String {
        val key = streamDeltaKey(agentId, itemId)
        val stats = fileChangeLiveStats.getOrPut(key) { FileChangeLiveStat() }
        val deltaStats = diffStats(delta)
        stats.additions += deltaStats.first
        stats.deletions += deltaStats.second
        fileChangeLivePaths(delta).forEach { stats.paths.add(it) }
        val statText = if (stats.additions + stats.deletions > 0) {
            " +${stats.additions} -${stats.deletions}"
        } else {
            ""
        }
        val paths = stats.paths.toList()
        val title = when (paths.size) {
            0 -> "正在编辑文件"
            1 -> "正在编辑 ${paths.first().substringAfterLast('/').substringAfterLast('\\')}"
            else -> "正在编辑 ${paths.size} 个文件"
        }
        val pathLines = paths.take(8).joinToString("\n") { "- $it" }
        val moreLine = if (paths.size > 8) "\n- 另有 ${paths.size - 8} 个文件" else ""
        return listOf("$title$statText", pathLines + moreLine)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun fileChangeLivePaths(delta: String): List<String> {
        val paths = linkedSetOf<String>()
        delta.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val summary = Regex("^[-•]\\s+(.+?)(?:\\s+\\(?\\+\\d+\\s+-\\d+\\)?)?$").find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            val diffPath = Regex("^diff --git a/(.+?) b/(.+)$").find(trimmed)
                ?.groupValues
                ?.getOrNull(2)
            val newPath = Regex("^\\+\\+\\+ b/(.+)$").find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            val explicit = Regex("^(?:file|path|target|filename|filePath):\\s+(.+)$", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            listOf(summary, diffPath, newPath, explicit)
                .filterNotNull()
                .map { it.trim().removePrefix("a/").removePrefix("b/") }
                .filter { candidate ->
                    candidate.isNotBlank() &&
                        candidate != "/dev/null" &&
                        (candidate.contains("/") || candidate.contains("\\") || candidate.substringAfterLast('.', "").length in 1..8)
                }
                .forEach { paths.add(it) }
        }
        return paths.toList()
    }

    private fun commandOutputLiveText(agentId: String, itemId: String, delta: String): String {
        val key = streamDeltaKey(agentId, itemId)
        val stats = commandOutputLiveStats.getOrPut(key) { CommandOutputLiveStat() }
        stats.chars += delta.length
        stats.lines += delta.count { it == '\n' }
        val lastText = delta.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() }
            .orEmpty()
            .compactMobileLine(120)
        if (lastText.isNotBlank()) stats.lastText = lastText
        val lineText = when {
            stats.lines > 0 -> "已输出 ${stats.lines} 行"
            stats.chars > 0 -> "已输出 ${stats.chars} 字符"
            else -> "输出持续返回"
        }
        val latest = stats.lastText.takeIf { it.isNotBlank() }?.let { "最近：$it" }.orEmpty()
        return listOf("正在运行命令，$lineText", latest).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun planText(plan: JSONArray?): String {
        if (plan == null) return ""
        val rows = mutableListOf<String>()
        for (index in 0 until plan.length()) {
            val step = plan.optJSONObject(index) ?: continue
            val status = step.optString("status")
            val text = step.optString("step").ifBlank { step.optString("text") }
            if (text.isNotBlank()) {
                val checkbox = when (status.trim().lowercase(Locale.ROOT)) {
                    "completed", "complete", "done", "finished" -> "[x]"
                    else -> "[ ]"
                }
                val statusLabel = status.takeIf { it.isNotBlank() }?.let { " **${it}**" }.orEmpty()
                rows.add("- $checkbox $text$statusLabel")
            }
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
        val server = payload.optString("server").ifBlank { payload.optString("server_name") }
        val tool = payload.optString("tool").ifBlank { payload.optString("name") }
        val label = listOf(server, tool).filter { it.isNotBlank() }.joinToString(".")
        return if (label.isNotBlank()) "正在运行命令：$label" else "正在运行命令。"
    }

    private fun syncPendingRequests(agentJson: JSONObject) {
        val rawAgentId = agentJson.optString("id")
        if (rawAgentId.isBlank()) return
        val agentId = agents.firstOrNull {
            it.id == rawAgentId || it.codexThreadId == rawAgentId || it.id == "codex_$rawAgentId"
        }?.id ?: rawAgentId
        val pending = agentJson.optJSONArray("pendingRequests")
        if (pending == null) {
            approvalRequests.removeAll { it.agentId == agentId }
            userInputRequests.removeAll { it.agentId == agentId }
            return
        }
        val seen = mutableSetOf<String>()
        for (index in 0 until pending.length()) {
            val requestJson = pending.optJSONObject(index) ?: continue
            val requestId = requestJson.optString("requestId")
            if (requestId.isBlank()) continue
            seen.add(requestId)
            val method = requestJson.optString("method").ifBlank { "approval" }
            upsertPendingRequest(
                agentId = agentId,
                requestId = requestId,
                method = method,
                params = requestJson.optJSONObject("params"),
                text = requestText(requestJson, method),
                timestamp = requestJson.optLong("timestamp", System.currentTimeMillis()),
            )
        }
        approvalRequests.removeAll { it.agentId == agentId && it.id !in seen }
        userInputRequests.removeAll { it.agentId == agentId && it.id !in seen }
    }

    private fun upsertPendingRequest(
        agentId: String,
        requestId: String,
        method: String,
        params: JSONObject?,
        text: String,
        timestamp: Long,
        announce: Boolean = false,
    ) {
        val agent = agents.firstOrNull { it.id == agentId }
        if (isUserInputRequest(method)) {
            userInputRequests.removeAll { it.id == requestId && it.agentId == agentId }
            userInputRequests.add(
                AgentUserInputRequest(
                    id = requestId,
                    agentId = agentId,
                    title = "CodeX 等你回答",
                    detail = text,
                    questions = parseUserInputQuestions(params),
                    timestamp = timestamp,
                ),
            )
            if (announce) recordAgentAlert(agentId, AgentAlertKind.Question, text)
            return
        }
        if (normalizePermissionMode(agent?.permissionMode) == PERMISSION_MODE_FULL_ACCESS) {
            approvalRequests.removeAll { it.id == requestId && it.agentId == agentId }
            return
        }
        approvalRequests.removeAll { it.id == requestId && it.agentId == agentId }
        approvalRequests.add(
            AgentApprovalRequest(
                id = requestId,
                agentId = agentId,
                method = method,
                title = "${agent?.name ?: "EasyCodex"} 需要确认",
                detail = text,
                timestamp = timestamp,
            ),
        )
        if (announce) recordAgentAlert(agentId, AgentAlertKind.Confirmation, text, requestId = requestId)
    }

    private fun isUserInputRequest(method: String): Boolean {
        return method == USER_INPUT_REQUEST_METHOD || method.contains("requestUserInput", ignoreCase = true)
    }

    private fun requestText(json: JSONObject, method: String): String {
        if (isUserInputRequest(method)) {
            val params = json.optJSONObject("params")
            val questionText = params?.let { userInputRequestQuestionText(it).ifBlank { jsonSummary(it) } }.orEmpty()
            if (questionText.isNotBlank()) return questionText
        }
        return json.optString("text")
            .ifBlank { json.optJSONObject("params")?.let { userInputRequestQuestionText(it).ifBlank { jsonSummary(it) } }.orEmpty() }
            .ifBlank { method }
    }

    private fun userInputRequestText(params: JSONObject): String {
        return userInputRequestQuestionText(params)
    }

    private fun parseUserInputQuestions(params: JSONObject?): List<AgentUserInputQuestion> {
        val questions = params?.optJSONArray("questions") ?: JSONArray()
        val parsed = buildList {
            for (index in 0 until questions.length()) {
                val question = questions.optJSONObject(index) ?: continue
                val questionId = question.optString("id").ifBlank { "question_$index" }
                val options = question.optJSONArray("options")
                add(
                    AgentUserInputQuestion(
                        id = questionId,
                        header = question.optString("header"),
                        question = question.optString("question"),
                        isOther = question.optBoolean("isOther", true),
                        isSecret = question.optBoolean("isSecret", false),
                        options = buildList {
                            if (options == null) return@buildList
                            for (optionIndex in 0 until options.length()) {
                                val option = options.optJSONObject(optionIndex) ?: continue
                                add(
                                    AgentUserInputOption(
                                        label = option.optString("label"),
                                        description = option.optString("description"),
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }
        if (parsed.isNotEmpty()) return parsed
        return listOf(
            AgentUserInputQuestion(
                id = "answer",
                header = "",
                question = params?.optString("message").orEmpty().ifBlank { "请输入你的回答。" },
                isOther = true,
                isSecret = false,
                options = emptyList(),
            ),
        )
    }

    private fun parseAgent(json: JSONObject): Agent {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val parsedMessages = buildList {
            for (index in 0 until messages.length()) {
                messages.optJSONObject(index)?.let { add(parseMessage(it)) }
            }
        }
        val fallbackName = firstUserMessageTitle(parsedMessages) ?: "EasyCodex"
        val projectless = json.optBoolean("projectless", false)
        val cwd = if (projectless) "" else json.optString("cwd", ".")
        return Agent(
            id = json.optString("id"),
            name = displayTaskNameForMobile(jsonNullableString(json, "name"), fallbackName),
            model = json.optString("model", DEFAULT_AGENT_MODEL).ifBlank { DEFAULT_AGENT_MODEL },
            cwd = cwd,
            projectRoot = if (projectless) null else cleanNullablePath(json.optString("projectRoot")),
            status = json.optString("status", "stopped"),
            serviceTier = normalizeServiceTier(json.optString("serviceTier", DEFAULT_SERVICE_TIER).ifBlank { DEFAULT_SERVICE_TIER }),
            reasoningEffort = json.optString("reasoningEffort", DEFAULT_REASONING_EFFORT).ifBlank { DEFAULT_REASONING_EFFORT },
            permissionMode = permissionModeFromRuntimeFields(
                jsonNullableString(json, "permissionMode"),
                jsonNullableString(json, "approvalPolicy"),
                jsonNullableString(json, "sandboxMode") ?: jsonNullableString(json, "sandbox"),
            ),
            activity = json.optString("activityLabel")
                .ifBlank { json.optString("activity") }
                .takeIf { it.isNotBlank() },
            messages = parsedMessages,
            codexThreadId = json.optString("codexThreadId")
                .ifBlank { json.optString("threadId") }
                .takeIf { it.isNotBlank() },
            pinned = json.optBoolean("pinned", false),
            updatedAt = jsonTimestamp(json, "updatedAt", parsedMessages.maxOfOrNull { it.timestamp }
                ?: System.currentTimeMillis()),
            queuedFollowUps = parseQueuedFollowUps(json),
        )
    }

    private fun parseCodexThread(json: JSONObject): Agent {
        val threadId = json.optString("id")
        val cwd = cleanNullablePath(json.optString("cwd")).orEmpty()
        val projectless = json.optBoolean("projectless", false)
        val projectRoot = cleanNullablePath(json.optString("projectRoot"))
            ?: if (projectless || cwd.isBlank()) CONVERSATION_PROJECT_PATH else null
        val preview = json.optString("preview").takeIf { it.isNotBlank() }
        val fallbackName = preview
            ?.let(::taskNameFromPrompt)
            ?.takeIf { it.isNotBlank() }
            ?: cwd.split('\\', '/').lastOrNull { it.isNotBlank() }
            ?: "EasyCodex 任务"
        val name = displayTaskNameForMobile(
            jsonNullableString(json, "name"),
            fallbackName,
        )
        val updatedAt = jsonTimestamp(json, "updatedAt", jsonTimestamp(json, "createdAt", 0L))
        val queuedFollowUps = parseQueuedFollowUps(json)
        val messages = buildList {
            if (!preview.isNullOrBlank()) add(AgentMessage("agent", "status", preview, updatedAt))
            queuedFollowUpMessage(queuedFollowUps, updatedAt)?.let { add(it) }
        }
        val queuedActivity = queuedFollowUps.takeIf { it.isNotEmpty() }?.let { "已排队 ${it.size} 个后续任务" }
        return Agent(
            id = "codex_$threadId",
            name = name,
            model = json.optString("model", defaultModel.ifBlank { DEFAULT_AGENT_MODEL })
                .ifBlank { defaultModel.ifBlank { DEFAULT_AGENT_MODEL } },
            cwd = cwd,
            projectRoot = projectRoot,
            status = codexThreadStatus(json),
            serviceTier = normalizeServiceTier(
                json.optString("serviceTier", defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER })
                    .ifBlank { defaultServiceTier.ifBlank { DEFAULT_SERVICE_TIER } },
            ),
            reasoningEffort = json.optString("reasoningEffort", defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT })
                .ifBlank { defaultReasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT } },
            permissionMode = permissionModeFromRuntimeFields(
                jsonNullableString(json, "permissionMode"),
                jsonNullableString(json, "approvalPolicy"),
                jsonNullableString(json, "sandboxMode") ?: jsonNullableString(json, "sandbox"),
            ),
            activity = queuedActivity ?: json.optString("activityLabel")
                .ifBlank { json.optString("activity") }
                .takeIf { it.isNotBlank() },
            messages = messages,
            codexThreadId = threadId,
            preview = preview,
            resumable = true,
            pinned = json.optBoolean("pinned", false),
            updatedAt = updatedAt,
            queuedFollowUps = queuedFollowUps,
        )
    }

    private fun jsonNullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).trim().takeUnless {
            it.isBlank() || it.equals("null", ignoreCase = true)
        }
    }

    private fun firstUserMessageTitle(messages: List<AgentMessage>): String? {
        val message = messages.firstOrNull { it.role == "user" || it.type == "user" } ?: return null
        return simplifyUserMessageForDisplay(message.text, message.role, message.type)
            .let(::taskNameFromPrompt)
            .takeIf { it.isNotBlank() }
    }

    private fun parseProjectRoots(json: JSONObject?): List<String> {
        val payload = json?.optJSONObject("data") ?: json ?: return emptyList()
        val roots = payload.optJSONArray("projectRoots") ?: return emptyList()
        return buildList {
            for (index in 0 until roots.length()) {
                cleanNullablePath(roots.optString(index))?.let { add(it) }
            }
        }
    }

    private fun updateRelayProjectRoots(projectRoots: List<String>) {
        val nextRoots = projectRoots.distinctBy { normalizePathKey(it) }
        val primaryRoot = nextRoots.firstOrNull()
        if (!primaryRoot.isNullOrBlank() && normalizePathKey(defaultCwd) == normalizePathKey(DEFAULT_AGENT_CWD)) {
            defaultCwd = primaryRoot
        }
        if (nextRoots == relayProjectRoots) return
        relayProjectRoots = nextRoots
        agentsRevision += 1
    }

    private fun parseCodexThreadDetail(json: JSONObject, fallback: Agent): Agent {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val parsedMessages = buildList {
            for (index in 0 until messages.length()) {
                messages.optJSONObject(index)?.let { add(parseMessage(it)) }
            }
        }
        val summary = parseCodexThread(json)
        val detailMessages = buildList {
            addAll(parsedMessages)
            if (parsedMessages.none { it.itemId?.startsWith("queued_followups_") == true }) {
                queuedFollowUpMessage(summary.queuedFollowUps, summary.updatedAt)?.let { add(it) }
            }
        }
        val detailName = displayTaskNameForMobile(
            jsonNullableString(json, "name"),
            firstUserMessageTitle(parsedMessages) ?: summary.name,
        )
        return summary.copy(
            name = detailName,
            id = fallback.id,
            status = summary.status,
            messages = detailMessages.ifEmpty { summary.messages.ifEmpty { fallback.messages } },
            resumable = true,
            updatedAt = summary.updatedAt.takeIf { it > 0L } ?: fallback.updatedAt,
        )
    }

    private fun parseQueuedFollowUps(json: JSONObject): List<QueuedFollowUp> {
        val items = json.optJSONArray("queuedFollowUps") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val text = item.optString("text").takeIf { it.isNotBlank() } ?: continue
                add(
                    QueuedFollowUp(
                        id = item.optString("id"),
                        text = text,
                        cwd = item.optString("cwd"),
                        createdAt = jsonTimestamp(item, "createdAt", 0L),
                        pausedReason = item.optString("pausedReason").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun queuedFollowUpMessage(items: List<QueuedFollowUp>, fallbackTimestamp: Long): AgentMessage? {
        if (items.isEmpty()) return null
        val lines = items.mapIndexed { index, item -> "${index + 1}. ${item.text}" }
        val timestamp = items.maxOfOrNull { it.createdAt }?.takeIf { it > 0 } ?: fallbackTimestamp
        return AgentMessage(
            role = "agent",
            type = "status",
            text = "已排队 ${items.size} 个后续任务：\n${lines.joinToString("\n")}",
            timestamp = timestamp,
            itemId = "queued_followups_${items.size}_$timestamp",
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

    private fun parseHostHealth(json: JSONObject, checkedAt: Long): HostHealthState {
        return parseHostHealthState(json, checkedAt)
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
            defaultReasoningEffort = json.optString("defaultReasoningEffort", DEFAULT_REASONING_EFFORT)
                .ifBlank { DEFAULT_REASONING_EFFORT },
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

    private fun parseGitStatus(json: JSONObject): GitStatusSummary {
        return parseGitStatusSummary(json)
    }

    private fun gitStatusFileEntry(path: String): FileEntry {
        return FileEntry(
            name = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path },
            path = path,
            type = "file",
        )
    }

    private fun normalizeNotificationLevel(level: String): String {
        return when (level.trim().lowercase(Locale.ROOT)) {
            "errors", "errors_only", "error" -> "errors"
            "muted", "mute", "off" -> "muted"
            else -> "all"
        }
    }

    private fun defaultCommitMessage(agent: Agent): String {
        val name = agent.name.trim().take(56).ifBlank { projectNameFromCwd(agent.cwd) }
        return "chore: update $name"
    }

    private fun parseFileListing(json: JSONObject): FileListing {
        val entries = buildList {
            val array = json.optJSONArray("entries") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    FileEntry(
                        name = item.optString("name"),
                        path = item.optString("path"),
                        type = item.optString("type"),
                    ),
                )
            }
        }
        return FileListing(
            cwd = json.optString("cwd"),
            path = json.optString("path", "."),
            entries = entries,
        )
    }

    private fun parseMessage(json: JSONObject): AgentMessage {
        val rawType = json.optString("type", "agent")
        val role = json.optString("role", "agent")
        val text = capMobileMessageText(simplifyUserMessageForDisplay(json.optString("text"), role, rawType), rawType)
        val type = normalizedAgentMessageType(role, rawType, text)
        return AgentMessage(
            role = role,
            type = type,
            text = capMobileMessageText(text, type),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            itemId = json.optString("_itemId").ifBlank { json.optString("itemId") }.takeIf { it.isNotBlank() },
            durationMs = json.optLong("durationMs", -1L).takeIf { it > 0L }
                ?: json.optLong("duration_ms", -1L).takeIf { it > 0L },
            detailText = json.optString("detailText").takeIf { it.isNotBlank() }.orEmpty(),
            subAgentThreadId = json.optString("subAgentThreadId").takeIf { it.isNotBlank() }.orEmpty(),
            subAgentNickname = json.optString("subAgentNickname").takeIf { it.isNotBlank() }.orEmpty(),
            subAgentStatus = json.optString("subAgentStatus").takeIf { it.isNotBlank() }.orEmpty(),
            subAgentRole = json.optString("subAgentRole").takeIf { it.isNotBlank() }.orEmpty(),
            toolCallId = json.optString("toolCallId").takeIf { it.isNotBlank() }.orEmpty(),
        )
    }

    private fun upsertAgent(agent: Agent) {
        if (agent.isBusy()) removeCompletedAlertsFor(agent)
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
        val incoming = authoritativeSnapshot(agent)
        if (incoming.isBusy()) removeCompletedAlertsFor(incoming)
        if (index < 0) {
            agents.add(0, incoming)
            agentsRevision += 1
            return
        }
        val current = agents[index]
        val status = mergedStatus(current, incoming)
        val next = incoming.copy(
            status = status,
            messages = mergeMessagesForSnapshot(current.messages, incoming),
            activity = mergedActivity(current, incoming, status),
            updatedAt = mergedUpdatedAt(current, incoming),
        )
        if (current != next) {
            val projectChanged = current.projectOptionKey() != next.projectOptionKey()
            agents[index] = next
            if (projectChanged) agentsRevision += 1
            rememberLiveCodexThread(next)
        }
    }

    private fun removeCompletedAlertsFor(agent: Agent) {
        val ids = buildSet {
            add(agent.id)
            agent.codexThreadId?.takeIf { it.isNotBlank() }?.let { threadId ->
                add(threadId)
                add("codex_$threadId")
            }
        }
        alerts.removeAll { it.kind == AgentAlertKind.Completed && it.agentId in ids }
    }

    private fun mergeMessageLists(current: List<AgentMessage>, incoming: List<AgentMessage>): List<AgentMessage> {
        val normalizedIncoming = incoming.dedupeAdjacentUserEchoes()
        if (current.isEmpty()) return normalizedIncoming
        if (incoming.isEmpty()) return current
        if (current.size == 1 && current.first().type == "status" && normalizedIncoming.size > 1) return normalizedIncoming
        if (normalizedIncoming.size == 1 && normalizedIncoming.first().type == "status" && current.size > 1) return current
        val merged = current.toMutableList()
        val itemIdIndexes = merged
            .mapIndexedNotNull { index, message -> message.itemId?.takeIf { it.isNotBlank() }?.let { it to index } }
            .toMap()
            .toMutableMap()
        val contentIndexes = merged
            .mapIndexed { index, message -> message.contentKey() to index }
            .toMap()
            .toMutableMap()
        normalizedIncoming.forEach { message ->
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

    private fun List<AgentMessage>.dedupeAdjacentUserEchoes(): List<AgentMessage> {
        if (size < 2) return this
        val deduped = mutableListOf<AgentMessage>()
        for (message in this) {
            val previous = deduped.lastOrNull()
            if (previous != null && previous.isSameUserEcho(message)) {
                deduped[deduped.lastIndex] = mergeMessage(previous, message)
            } else {
                deduped.add(message)
            }
        }
        return if (deduped.size == size) this else deduped
    }

    private fun AgentMessage.isSameUserEcho(other: AgentMessage): Boolean {
        if ((role != "user" && type != "user") || (other.role != "user" && other.type != "user")) return false
        val currentText = simplifyUserMessageForDisplay(text, role, type).trim()
        val otherText = simplifyUserMessageForDisplay(other.text, other.role, other.type).trim()
        if (currentText.isBlank() || currentText != otherText) return false
        return kotlin.math.abs(timestamp - other.timestamp) <= 30_000L
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
        val cleanIncoming = incoming.copy(text = simplifyUserMessageForDisplay(incoming.text, incoming.role, incoming.type))
        val text = when {
            current.streaming && current.text.startsWith(cleanIncoming.text) -> current.text
            cleanIncoming.text.startsWith(current.text) -> cleanIncoming.text
            current.text.startsWith(cleanIncoming.text) -> cleanIncoming.text
            else -> mergeStreamingText(current.text, cleanIncoming.text, cleanIncoming.type)
        }
        val cappedText = capMobileMessageText(text, cleanIncoming.type)
        val keepCurrentStreaming = current.streaming && cleanIncoming.streaming && cappedText == current.text && cappedText.length >= cleanIncoming.text.length
        val timestamp = if (current.itemId.isNullOrBlank() && cleanIncoming.itemId.isNullOrBlank() && current.text == cleanIncoming.text) {
            current.timestamp
        } else {
            maxOf(current.timestamp, cleanIncoming.timestamp)
        }
        return current.copy(
            type = cleanIncoming.type,
            text = cappedText,
            timestamp = timestamp,
            itemId = current.itemId ?: cleanIncoming.itemId,
            streaming = keepCurrentStreaming,
            attachments = current.attachments.ifEmpty { cleanIncoming.attachments },
            durationMs = current.durationMs ?: cleanIncoming.durationMs,
            detailText = cleanIncoming.detailText.ifBlank { current.detailText },
            subAgentThreadId = cleanIncoming.subAgentThreadId.ifBlank { current.subAgentThreadId },
            subAgentNickname = cleanIncoming.subAgentNickname.ifBlank { current.subAgentNickname },
            subAgentStatus = cleanIncoming.subAgentStatus.ifBlank { current.subAgentStatus },
            subAgentRole = cleanIncoming.subAgentRole.ifBlank { current.subAgentRole },
            toolCallId = cleanIncoming.toolCallId.ifBlank { current.toolCallId },
        )
    }

    private fun List<AgentMessage>.indexOfLastCollapsibleDuplicate(incoming: AgentMessage): Int {
        if (!incoming.isCollapsibleDuplicateCandidate()) return -1
        val incomingKey = incoming.collapsibleDuplicateKey()
        return indexOfLast { existing ->
            existing.isCollapsibleDuplicateCandidate() && existing.collapsibleDuplicateKey() == incomingKey
        }
    }

    private fun List<AgentMessage>.indexOfLastUserEcho(incoming: AgentMessage): Int {
        if (incoming.type != "user" && incoming.role != "user") return -1
        val incomingText = simplifyUserMessageForDisplay(incoming.text, incoming.role, incoming.type).trim()
        if (incomingText.isBlank()) return -1
        return indexOfLast { existing ->
            val existingUser = existing.type == "user" || existing.role == "user"
            val replaceableEcho = existing.itemId?.startsWith("local_user_") == true || existing.itemId.isNullOrBlank()
            existingUser &&
                replaceableEcho &&
                simplifyUserMessageForDisplay(existing.text, existing.role, existing.type).trim() == incomingText &&
                kotlin.math.abs(existing.timestamp - incoming.timestamp) <= 30_000L
        }
    }

    private fun AgentMessage.isCollapsibleDuplicateCandidate(): Boolean {
        if (role != "agent") return false
        return type in setOf("command", "command_output", "thinking", "status") && text.collapsibleTextKey(type).isNotBlank()
    }

    private fun AgentMessage.collapsibleDuplicateKey(): Triple<String, String, String> {
        return Triple(role, type, text.collapsibleTextKey(type))
    }

    private fun String.collapsibleTextKey(type: String): String {
        val normalized = trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('。', '.', '!')
        return when {
            type == "command" && normalized in setOf("正在运行命令", "命令已开始执行", "命令执行完成") -> "command_placeholder"
            type == "command_output" && normalized.contains("命令") && normalized.contains("输出") && (normalized.contains("省略") || normalized.contains("返回")) -> "command_output_placeholder"
            type == "thinking" && normalized in setOf("正在思考中", "正在思考中...") -> "thinking_placeholder"
            else -> normalized
        }
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
        val cleanText = simplifyUserMessageForDisplay(message.text, message.role, message.type)
        val normalizedType = normalizedAgentMessageType(message.role, message.type, cleanText)
        val capped = message.copy(
            type = normalizedType,
            text = capMobileMessageText(cleanText, normalizedType),
        )
        updateAgent(agentId) { agent ->
            val nextMessages = agent.messages.toMutableList()
            val duplicateIndex = nextMessages.indexOfLastCollapsibleDuplicate(capped)
            if (duplicateIndex >= 0) {
                val existing = nextMessages[duplicateIndex]
                nextMessages[duplicateIndex] = mergeMessage(existing, capped)
            } else {
                nextMessages.add(capped)
            }
            agent.copy(messages = nextMessages, updatedAt = capped.timestamp)
        }
    }

    private fun clearPendingAgentStatus(agentId: String) {
        val itemId = pendingAgentStatusIds.remove(agentId)?.takeIf { it.isNotBlank() } ?: return
        removeMessageByItemId(agentId, itemId)
    }

    private fun removeMessageByItemId(agentId: String, itemId: String) {
        updateAgent(agentId) { agent ->
            val nextMessages = agent.messages.filterNot { it.itemId == itemId }
            if (nextMessages.size == agent.messages.size) agent else agent.copy(messages = nextMessages)
        }
    }

    private fun removeMessage(agentId: String, message: AgentMessage) {
        val itemId = message.itemId
        updateAgent(agentId) { agent ->
            val nextMessages = agent.messages.filterNot {
                itemId != null && it.itemId == itemId
            }
            if (nextMessages.size == agent.messages.size) agent else agent.copy(messages = nextMessages)
        }
    }

    private fun appendDelta(agentId: String, itemId: String, delta: String, type: String) {
        if (agentId.isBlank() || itemId.isBlank() || delta.isBlank()) return
        val safeDelta = when (type) {
            "command_output" -> commandOutputLiveText(agentId, itemId, delta)
            "file_change" -> fileChangeLiveText(agentId, itemId, delta)
            "sub_agent" -> "子代理正在工作。"
            "thinking" -> "正在思考中。"
            else -> delta
        }
        val key = streamDeltaKey(agentId, itemId)
        val pendingDelta = pendingStreamDeltas[key]
        if (pendingDelta == null) {
            pendingStreamDeltas[key] = PendingStreamDelta(agentId, itemId, type).also {
                appendCappedDelta(it.text, safeDelta, type)
            }
        } else {
            pendingDelta.type = type
            if (pendingDelta.text.toString() != safeDelta) appendCappedDelta(pendingDelta.text, safeDelta, type)
        }
        scheduleStreamDeltaFlush()
    }

    private fun applyDelta(agentId: String, itemId: String, delta: String, type: String) {
        updateAgent(agentId) { agent ->
            val index = agent.messages.indexOfLast { it.itemId == itemId }
            val nextMessages = agent.messages.toMutableList()
            if (index >= 0) {
                val existing = nextMessages[index]
                nextMessages[index] = existing.copy(text = mergeStreamingText(existing.text, delta, type), type = type, streaming = true)
            } else {
                nextMessages.add(AgentMessage("agent", type, capMobileMessageText(delta, type), System.currentTimeMillis(), itemId, true))
            }
            agent.copy(messages = nextMessages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun finalizeMessage(
        agentId: String,
        itemId: String,
        text: String,
        type: String,
        streaming: Boolean = false,
        metadata: AgentMessage = AgentMessage("agent", type, "", 0L),
    ) {
        flushPendingDelta(agentId, itemId)
        val normalizedType = normalizedAgentMessageType("agent", type, text)
        if (normalizedType == "thinking" && !streaming) {
            if (itemId.isNotBlank()) removeMessageByItemId(agentId, itemId)
            return
        }
        if (normalizedType == "file_change") fileChangeLiveStats.remove(streamDeltaKey(agentId, itemId))
        if (normalizedType == "command_output") commandOutputLiveStats.remove(streamDeltaKey(agentId, itemId))
        val cappedIncoming = capMobileMessageText(text, normalizedType)
        updateAgent(agentId) { agent ->
            val nextMessages = agent.messages.toMutableList()
            val incomingRole = if (normalizedType == "user") "user" else "agent"
            val incoming = AgentMessage(
                incomingRole,
                normalizedType,
                cappedIncoming,
                System.currentTimeMillis(),
                itemId,
                streaming,
                detailText = capMobileMessageText(metadata.detailText, normalizedType),
                subAgentThreadId = metadata.subAgentThreadId,
                subAgentNickname = metadata.subAgentNickname,
                subAgentStatus = metadata.subAgentStatus,
                subAgentRole = metadata.subAgentRole,
                toolCallId = metadata.toolCallId,
            )
            val itemIndex = nextMessages.indexOfLast { it.itemId == itemId }
            val localUserEchoIndex = nextMessages.indexOfLastUserEcho(incoming)
            val collapsibleIndex = nextMessages.indexOfLastCollapsibleDuplicate(incoming)
            val index = when {
                itemIndex >= 0 -> itemIndex
                localUserEchoIndex >= 0 -> localUserEchoIndex
                else -> collapsibleIndex
            }
            if (index >= 0) {
                val existing = nextMessages[index]
                val nextText = if (existing.type == normalizedType) {
                    mergeFinalText(existing.text, cappedIncoming, normalizedType, streaming)
                } else {
                    cappedIncoming
                }
                nextMessages[index] = existing.copy(
                    text = nextText,
                    type = normalizedType,
                    itemId = itemId.ifBlank { existing.itemId.orEmpty() },
                    streaming = streaming,
                    detailText = capMobileMessageText(metadata.detailText, normalizedType).ifBlank { existing.detailText },
                    subAgentThreadId = metadata.subAgentThreadId.ifBlank { existing.subAgentThreadId },
                    subAgentNickname = metadata.subAgentNickname.ifBlank { existing.subAgentNickname },
                    subAgentStatus = metadata.subAgentStatus.ifBlank { existing.subAgentStatus },
                    subAgentRole = metadata.subAgentRole.ifBlank { existing.subAgentRole },
                    toolCallId = metadata.toolCallId.ifBlank { existing.toolCallId },
                )
            } else {
                nextMessages.add(incoming)
            }
            agent.copy(messages = nextMessages, updatedAt = System.currentTimeMillis())
        }
        rememberPlanReviewCandidate(agentId, itemId, normalizedType, streaming)
    }

    private fun turnDurationMs(data: JSONObject, agentId: String, completedAt: Long): Long? {
        val direct = data.optLong("durationMs", -1L).takeIf { it > 0L }
            ?: data.optLong("duration_ms", -1L).takeIf { it > 0L }
        if (direct != null) return direct
        val turn = data.optJSONObject("turn") ?: data.optJSONObject("event")
        val nested = turn?.optLong("durationMs", -1L)?.takeIf { it > 0L }
            ?: turn?.optLong("duration_ms", -1L)?.takeIf { it > 0L }
        if (nested != null) return nested
        val nestedStarted = turn?.let { jsonTimestamp(it, "startedAt", 0L) }?.takeIf { it > 0L }
        val nestedCompleted = turn?.let { jsonTimestamp(it, "completedAt", completedAt) }?.takeIf { it > 0L } ?: completedAt
        if (nestedStarted != null && nestedCompleted > nestedStarted) return nestedCompleted - nestedStarted
        val startedAt = activeTurnStartedAt[agentId] ?: return null
        return (completedAt - startedAt).takeIf { it > 0L }
    }

    private fun annotateLatestAgentMessageDuration(agentId: String, durationMs: Long?) {
        if (durationMs == null || durationMs <= 0L) return
        updateAgent(agentId) { agent ->
            val nextMessages = agent.messages.toMutableList()
            val index = nextMessages.indexOfLast { it.role == "agent" && it.type == "agent" && it.text.isNotBlank() }
            if (index < 0) return@updateAgent agent
            val message = nextMessages[index]
            nextMessages[index] = message.copy(streaming = false, durationMs = durationMs)
            agent.copy(messages = nextMessages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun rememberPlanReviewCandidate(agentId: String, itemId: String, type: String, streaming: Boolean) {
        if (type != "plan" || streaming || itemId.isBlank()) return
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        val message = latestPlanMessage(agent, itemId) ?: return
        if (!isActionablePlanMessage(message)) return
        val turnSerial = activeTurnSerials[agentId] ?: completedTurnSerials[agentId] ?: 0L
        pendingPlanReviewCandidates[agentId] = PlanReviewCandidate(itemId, turnSerial)
        maybePromptCompletedPlanReview(agentId)
    }

    private fun maybePromptCompletedPlanReview(agentId: String) {
        if (!appInForeground || activeAgentId != agentId) return
        val agent = agents.firstOrNull { it.id == agentId } ?: return
        if (agent.isBusy()) return
        val candidate = pendingPlanReviewCandidates[agentId] ?: return
        val completedTurnSerial = completedTurnSerials[agentId] ?: return
        if (candidate.turnSerial <= 0L || completedTurnSerial < candidate.turnSerial) return
        val message = latestPlanMessage(agent, candidate.itemId) ?: return
        if (!isActionablePlanMessage(message)) return
        val key = "${agentId}:${message.itemId ?: message.stableKey()}"
        if (!autoPromptedPlanKeys.add(key)) return
        planReview = PlanReview(
            agentId = agentId,
            message = message,
            taskName = agent.displayTaskName(),
            projectPath = agent.displayProjectPath(),
        )
    }

    private fun latestPlanMessage(agent: Agent, itemId: String?): AgentMessage? {
        return if (!itemId.isNullOrBlank()) {
            agent.messages.lastOrNull { it.itemId == itemId && it.type == "plan" }
        } else {
            agent.messages.lastOrNull { it.type == "plan" }
        }
    }

    private fun Agent.displayTaskName(): String {
        return name.takeIf { it.isNotBlank() && !isPendingCodexThreadName(it) }
            ?: preview?.takeIf { it.isNotBlank() }
            ?: "未命名任务"
    }

    private fun Agent.displayProjectPath(): String {
        return (projectRoot ?: cwd).ifBlank { defaultCwd.ifBlank { DEFAULT_AGENT_CWD } }
    }

    private fun scheduleStreamDeltaFlush() {
        if (streamDeltaFlushRunnable != null) return
        streamDeltaFlushRunnable = Runnable {
            streamDeltaFlushRunnable = null
            flushStreamDeltas()
        }
        val delayMs = if (appInForeground) STREAM_DELTA_FLUSH_MS else BACKGROUND_STREAM_DELTA_FLUSH_MS
        main.postDelayed(streamDeltaFlushRunnable!!, delayMs)
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
        fileChangeLiveStats.clear()
        commandOutputLiveStats.clear()
    }

    private fun streamDeltaKey(agentId: String, itemId: String): String = "$agentId\u0000$itemId"

    private fun mergeStreamingText(current: String, incoming: String, type: String = "agent"): String {
        if (incoming.isBlank()) return current
        if (type == "file_change" && isLiveFileChangeText(incoming)) return incoming
        if (type == "command_output" && incoming.startsWith("正在运行命令")) return incoming
        if (current.isBlank() || isStreamingPlaceholder(current)) return incoming
        if (isStreamingPlaceholder(incoming)) return current
        if (incoming == current) return current
        if (incoming.startsWith(current)) return incoming
        if (current.startsWith(incoming)) return current
        if (isMobileTextTruncated(current, type)) return current
        val overlap = longestSuffixPrefixLength(current, incoming)
        return capMobileMessageText(current + incoming.drop(overlap), type)
    }

    private fun mergeFinalText(current: String, incoming: String, type: String, streaming: Boolean): String {
        if (streaming) return mergeStreamingText(current, incoming, type)
        if (incoming.isBlank()) return current
        if (current.isBlank() || isStreamingPlaceholder(current)) return incoming
        if (type == "file_change" && isLiveFileChangeText(current)) return incoming
        if (incoming == current) return current
        if (incoming.startsWith(current)) return incoming
        if (current.startsWith(incoming)) return incoming
        return mergeStreamingText(current, incoming, type)
    }

    private fun isLiveFileChangeText(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("正在修改文件") ||
            trimmed.startsWith("正在编辑文件") ||
            trimmed.startsWith("正在编辑 ")
    }

    private fun mobileTextLimit(type: String): Int {
        return when (type) {
            "command_output", "file_change", "sub_agent", "thinking" -> MAX_MOBILE_DETAIL_TEXT_CHARS
            else -> MAX_MOBILE_MESSAGE_TEXT_CHARS
        }
    }

    private fun simplifyUserMessageForDisplay(text: String, role: String, type: String): String {
        if (role != "user" && type != "user") return text
        val trimmed = stripInjectedContextForDisplay(text.trim())
        if (!trimmed.startsWith(PLAN_MODE_PREFIX_FOR_DISPLAY)) return trimmed
        val markerIndex = trimmed.lastIndexOf(PLAN_MODE_DEMAND_MARKER_FOR_DISPLAY)
        if (markerIndex < 0) return trimmed
        return trimmed.substring(markerIndex + PLAN_MODE_DEMAND_MARKER_FOR_DISPLAY.length).trim().ifBlank { trimmed }
    }

    private fun stripInjectedContextForDisplay(text: String): String {
        if (!looksLikeInjectedContext(text)) return text
        val markers = listOf("</environment_context>", "</INSTRUCTIONS>")
        val cursor = markers
            .map { marker -> text.lastIndexOf(marker).takeIf { it >= 0 }?.let { it + marker.length } ?: -1 }
            .maxOrNull()
            ?: -1
        if (cursor < 0) return ""
        val rest = text.substring(cursor).trim()
        if (rest.isBlank() || looksLikeInjectedContext(rest)) return ""
        return rest
    }

    private fun looksLikeInjectedContext(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("# AGENTS.md instructions for ") ||
            trimmed.startsWith("<INSTRUCTIONS>") ||
            trimmed.startsWith("<environment_context>") ||
            trimmed.contains("\n<INSTRUCTIONS>") ||
            trimmed.contains("\n<environment_context>")
    }

    private fun capMobileMessageText(text: String, type: String): String {
        val limit = mobileTextLimit(type)
        if (text.length <= limit || text.endsWith(MOBILE_TEXT_TRUNCATED_NOTICE)) return text
        return text.take(limit).trimEnd() + MOBILE_TEXT_TRUNCATED_NOTICE
    }

    private fun isMobileTextTruncated(text: String, type: String): Boolean {
        return text.length >= mobileTextLimit(type) && text.endsWith(MOBILE_TEXT_TRUNCATED_NOTICE)
    }

    private fun appendCappedDelta(builder: StringBuilder, delta: String, type: String) {
        if (delta.isBlank()) return
        if ((type == "file_change" && isLiveFileChangeText(delta)) ||
            (type == "command_output" && delta.startsWith("正在运行命令"))
        ) {
            builder.clear()
            builder.append(delta)
            return
        }
        val current = builder.toString()
        if (isMobileTextTruncated(current, type)) return
        builder.append(delta)
        val capped = capMobileMessageText(builder.toString(), type)
        if (capped.length != builder.length || capped.endsWith(MOBILE_TEXT_TRUNCATED_NOTICE)) {
            builder.clear()
            builder.append(capped)
        }
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
            "命令输出已省略。",
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
        val existing = prefs.getString(PREF_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val next = UUID.randomUUID().toString()
        prefs.edit().putString(PREF_CLIENT_ID, next).apply()
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
            val uri = java.net.URI(relayUrl)
            listOfNotNull(uri.host, uri.port.takeIf { it > 0 }?.toString()).joinToString(":")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: relayUrl.ifBlank { strings.endpointNotFilled }
    }
}

private fun formatCliDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val seconds = durationMs / 1000
    return if (seconds <= 0L) "，耗时 <1 秒" else "，耗时 ${seconds} 秒"
}

private data class PlanReviewCandidate(
    val itemId: String,
    val turnSerial: Long,
)

private data class PendingQuickReply(
    val agentId: String,
    val text: String,
    val createdAt: Long,
)

private data class PendingApprovalResponse(
    val agentId: String,
    val requestId: String,
    val approved: Boolean,
    val createdAt: Long,
)
