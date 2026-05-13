package com.easycodex.mobile

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
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

private const val EMOJI_COLUMNS = 8
private const val EMOJI_ROWS = 5
private const val EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS
const val AGENTS_REFRESH_DEBOUNCE_MS = 500L
const val AGENT_ACTIVITY_UPDATE_THROTTLE_MS = 500L
const val STREAM_DELTA_FLUSH_MS = 48L
const val CODEX_THREAD_DETAIL_PREFETCH_LIMIT = 10
const val CODEX_THREAD_DETAIL_MAX_RETRIES = 3
const val CODEX_THREAD_DETAIL_RETRY_BASE_MS = 1_200L
const val CODEX_DETAIL_LOADING_LABEL = "正在加载具体细节"
const val CODEX_DETAIL_RETRY_LABEL = "正在重新同步任务详情"
const val AGENT_ALERT_CHANNEL_ID = "easycodex-agent-alerts"
const val AGENT_ALERT_NOTIFICATION_BASE_ID = 73000
const val RELAY_REQUEST_TIMEOUT_MS = 30_000L
const val MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024
const val MAX_ATTACHMENT_BATCH_BYTES = 48 * 1024 * 1024
const val PLAN_MODE_PROMPT = """
请先进入计划模式处理下面的需求。

要求：
1. 先优化并整理一个可执行计划。
2. 计划可以很详细，包含关键步骤、风险点、验证方式和需要确认的问题。
3. 不要开始执行、不要修改文件、不要运行命令。
4. 计划最后请明确询问我是否要开始这个计划。

需求：
"""
const val PLAN_OPTIMIZE_PROMPT = "请优化上一条计划，让步骤更清晰、风险更完整、执行顺序更可靠。仍然不要开始执行，最后继续询问我是否要开始这个计划。"
const val PLAN_START_PROMPT = "开始执行上一条计划。"

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
            EasyCodexApp(
                importedConnection = importedConnection,
                initialAgentId = intent?.getStringExtra("agentId"),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyEasyCodexConnectionUri(this, intent.data) || !intent.getStringExtra("agentId").isNullOrBlank()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyCodexApp(importedConnection: Boolean = false, initialAgentId: String? = null) {
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
    var consumedInitialAgentId by remember(initialAgentId) { mutableStateOf(false) }
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

    LaunchedEffect(initialAgentId, appContentReady, controller.agentsRevision) {
        val target = initialAgentId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!appContentReady || consumedInitialAgentId) return@LaunchedEffect
        if (controller.agents.any { it.id == target }) {
            controller.selectAgent(target)
            consumedInitialAgentId = true
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
                                attachments = controller.attachmentDrafts,
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
                                onRemoveAttachment = { controller.removeAttachmentDraft(it) },
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
                                    notificationLevelState = controller.notificationLevelState,
                                    onInterrupt = { controller.interruptActiveAgent() },
                                    onOpenDiffReview = { controller.openDiffReview() },
                                    onNotificationLevelChange = { agentId, level ->
                                        controller.updateNotificationLevel(agentId, level)
                                    },
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

    controller.diffReview?.let { review ->
        DiffReviewDialog(
            review = review,
            commitDraft = controller.gitCommitDraft,
            onSelectFile = { controller.selectDiffReviewFile(it) },
            onCommitMessageChange = { controller.updateGitCommitMessage(it) },
            onCommit = { controller.commitDiffReviewDraft() },
            onDismiss = { controller.dismissDiffReview() },
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
fun EasyCodexAppIcon(
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

fun projectNameFromCwd(cwd: String): String {
    val cleaned = cleanNullablePath(cwd) ?: return "未命名项目"
    return cleaned
        .trimEnd('\\', '/')
        .split('\\', '/')
        .lastOrNull { it.isNotBlank() }
        ?: "未命名项目"
}

fun normalizePathKey(path: String): String {
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

fun cleanNullablePath(value: String?): String? {
    val cleaned = value?.trim().orEmpty()
    return cleaned.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}

fun jsonTimestamp(json: JSONObject, key: String, fallback: Long = 0L): Long {
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

fun codexThreadStatus(json: JSONObject): String {
    val raw = json.opt("status")
    return when (raw) {
        is JSONObject -> raw.optString("type")
        is String -> raw
        else -> ""
    }.trim().takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) } ?: "可恢复"
}

fun Agent.isBusy(): Boolean {
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

@Composable
fun MessageComposer(
    text: String,
    attachments: List<AttachmentDraft>,
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
    onRemoveAttachment: (String) -> Unit,
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = !planModeEnabled,
                    onClick = { onPlanModeChange(false) },
                    enabled = enabled,
                    label = { Text(strings.runDirectly) },
                )
                FilterChip(
                    selected = planModeEnabled,
                    onClick = { onPlanModeChange(true) },
                    enabled = enabled,
                    label = { Text(strings.planFirst) },
                )
                listOf(
                    "先调查再给计划",
                    "修复并测试",
                    "解释这段报错",
                    "继续上次任务",
                ).forEach { prompt ->
                    AssistChip(
                        onClick = { onTextChange(if (text.isBlank()) prompt else "${text.trimEnd()}\n$prompt") },
                        enabled = enabled,
                        label = { Text(prompt) },
                    )
                }
            }
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEach { attachment ->
                        AssistChip(
                            onClick = { onRemoveAttachment(attachment.path) },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("${attachment.name} ×", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
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
                    enabled = enabled && (text.isNotBlank() || attachments.isNotEmpty()),
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

fun normalizeServiceTier(value: String): String {
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
