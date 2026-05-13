package com.easycodex.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
    private var lastStreamSessionId = prefs.getString(PREF_LAST_STREAM_SESSION_ID, "") ?: ""
    private var lastStreamSeq = prefs.getLong(PREF_LAST_STREAM_SEQ, 0L)
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
    private val pendingTimeoutRunnables = ConcurrentHashMap<String, Runnable>()
    private var diffReviewRequestCounter = 0

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
    var diffReview by mutableStateOf<DiffReviewState?>(null)
        private set
    var gitCommitDraft by mutableStateOf(GitCommitDraft())
        private set
    var notificationLevelState by mutableStateOf<NotificationLevelState?>(null)
        private set
    val agents = mutableStateListOf<Agent>()
    val codexModels = mutableStateListOf<CodexModelOption>()
    val alerts = mutableStateListOf<AgentAlert>()
    val approvalRequests = mutableStateListOf<AgentApprovalRequest>()
    val attachmentDrafts = mutableStateListOf<AttachmentDraft>()
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
        val relaySecurityError = validateRelayEndpoint(relayUrl)
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
            .apply()
        relayUrl = DEFAULT_RELAY_URL
        apiKey = ""
        statusText = strings.connectionConfigCleared
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
        notificationLevelState = null
        draftProjectLocked = false
        if (draftProjectCwd.isNullOrBlank()) {
            draftProjectCwd = defaultCwd.ifBlank { DEFAULT_AGENT_CWD }
        }
    }

    fun selectAgent(agentId: String) {
        draftProjectCwd = null
        draftProjectLocked = false
        activeAgentId = agentId
        loadNotificationLevel(agentId)
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
        val typedText = inputText.trim()
        val attachmentText = attachmentPrompt()
        val text = listOf(typedText, attachmentText).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isBlank()) return
        val transportText = if (planMode) "$PLAN_MODE_PROMPT$text" else text
        val displayText = listOf(typedText.ifBlank { "处理已上传附件" }, attachmentText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        draftAgent?.let { draft ->
            inputText = ""
            attachmentDrafts.clear()
            createAgent(
                name = taskNameFromPrompt(typedText.ifBlank { text }),
                model = draft.model,
                cwd = draft.cwd,
                reasoningEffort = draft.reasoningEffort,
                serviceTier = draft.serviceTier,
                firstMessage = transportText,
                firstDisplayMessage = displayText,
            )
            return
        }
        val agent = activeAgent ?: return
        if (agent.resumable) {
            inputText = ""
            attachmentDrafts.clear()
            resumeCodexThread(agent, transportText, displayText)
            return
        }
        inputText = ""
        attachmentDrafts.clear()
        sendMessageToAgent(agent.id, transportText, displayText)
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

    fun removeAttachmentDraft(path: String) {
        attachmentDrafts.removeAll { it.path == path }
    }

    private fun attachmentPrompt(): String {
        if (attachmentDrafts.isEmpty()) return ""
        val lines = attachmentDrafts.map { "- ${it.name}: ${it.path}" }
        return "已上传附件：\n${lines.joinToString("\n")}\n请结合这些附件继续处理。"
    }

    fun uploadActiveAttachments(uris: List<Uri>) {
        val cwd = draftProjectCwd ?: activeAgent?.cwd ?: return
        if (uris.isEmpty()) return
        val files = uris.take(12).mapNotNull { uri -> attachmentPayload(uri) }
        if (files.isEmpty()) {
            statusText = "没有读取到可上传的附件"
            return
        }
        val oversized = files.firstOrNull { (it["size"] as? Int ?: 0) > MAX_ATTACHMENT_BYTES }
        if (oversized != null) {
            statusText = strings.attachmentTooLarge((oversized["name"] as? String) ?: strings.attachmentFallbackName)
            return
        }
        val totalBytes = files.sumOf { it["size"] as? Int ?: 0 }
        if (totalBytes > MAX_ATTACHMENT_BATCH_BYTES) {
            statusText = "本次附件总大小超过 48 MB，未上传任何附件"
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
                if (path.isNotBlank()) uploadedDrafts.add(AttachmentDraft(name = name, path = path))
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
            gitCommitDraft = gitCommitDraft.copy(files = changedFiles, error = null)
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

    private fun handleIncoming(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
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

    private fun parseGitStatus(json: JSONObject): GitStatusSummary {
        fun readArray(name: String): List<String> {
            val array = json.optJSONArray(name) ?: JSONArray()
            return buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).takeIf { it.isNotBlank() } ?: continue
                    add(value)
                }
            }
        }
        val files = listOf("modified", "created", "deleted", "renamed", "notAdded", "conflicted")
            .flatMap(::readArray)
            .distinct()
        return GitStatusSummary(
            branch = json.optString("branch"),
            isClean = json.optBoolean("isClean", files.isEmpty()),
            files = files,
        )
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
            val uri = URI(relayUrl)
            listOfNotNull(uri.host, uri.port.takeIf { it > 0 }?.toString()).joinToString(":")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: relayUrl.ifBlank { strings.endpointNotFilled }
    }

    private fun validateRelayEndpoint(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return strings.invalidRelayUrl
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme == "wss") return null
        if (scheme != "ws") return "Relay 地址必须使用 ws:// 或 wss://"
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val privateOrLocal = host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "10.0.2.2" ||
            host.startsWith("10.") ||
            Regex("^192\\.168\\.\\d{1,3}\\.\\d{1,3}$").matches(host) ||
            Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}$").matches(host)
        return if (privateOrLocal) null else "出于安全考虑，ws:// 只允许连接 localhost、模拟器或局域网地址；公网地址请使用 wss://。"
    }
}

