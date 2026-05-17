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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.sin
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
const val BACKGROUND_AGENTS_REFRESH_DEBOUNCE_MS = 5_000L
const val AGENT_ACTIVITY_UPDATE_THROTTLE_MS = 500L
const val STREAM_DELTA_FLUSH_MS = 64L
const val BACKGROUND_STREAM_DELTA_FLUSH_MS = 1_500L
const val CLI_OUTPUT_FLUSH_MS = 96L
const val RELAY_STATE_DETAIL_REFRESH_DEBOUNCE_MS = 250L
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
const val PLAN_START_PROMPT = "请开始任务"

private data class EasyCodexAdaptiveMetrics(
    val usePermanentDrawer: Boolean,
    val drawerWidth: androidx.compose.ui.unit.Dp,
    val contentMaxWidth: androidx.compose.ui.unit.Dp,
    val composerMaxWidth: androidx.compose.ui.unit.Dp,
)

private fun easyCodexAdaptiveMetrics(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
): EasyCodexAdaptiveMetrics {
    val landscapeWide = width >= 720.dp && width > height
    val expanded = width >= 840.dp
    val usePermanentDrawer = expanded || landscapeWide
    return EasyCodexAdaptiveMetrics(
        usePermanentDrawer = usePermanentDrawer,
        drawerWidth = when {
            width >= 1200.dp -> 372.dp
            width >= 1000.dp -> 344.dp
            else -> 316.dp
        },
        contentMaxWidth = when {
            width >= 1200.dp -> 980.dp
            width >= 840.dp -> 900.dp
            else -> androidx.compose.ui.unit.Dp.Unspecified
        },
        composerMaxWidth = when {
            width >= 1200.dp -> 940.dp
            width >= 840.dp -> 880.dp
            else -> androidx.compose.ui.unit.Dp.Unspecified
        },
    )
}

@Composable
private fun AdaptiveWidthFrame(
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = if (maxWidth == androidx.compose.ui.unit.Dp.Unspecified) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
            },
        ) {
            content()
        }
    }
}

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
private fun buildSystemVoiceInputIntent(context: Context, prompt: String): Intent {
    val baseIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
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

@Composable
private fun TopBarStatusPill(status: String, text: String) {
    val connected = status == "connected"
    val dotColor = when {
        connected -> MaterialTheme.colorScheme.primary
        status == "connecting" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val infiniteTransition = rememberInfiniteTransition(label = "status dot")
    val dotAlpha by if (status == "connecting") {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = dotColor.copy(alpha = dotAlpha),
                modifier = Modifier.size(8.dp)
            ) {}
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyCodexApp(importedConnection: Boolean = false, initialAgentId: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { EasyCodexControllerProvider.get(context.applicationContext) }
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
    var showCliMode by remember { mutableStateOf(false) }
    var planModeEnabled by remember { mutableStateOf(false) }
    var consumedInitialAgentId by remember(initialAgentId) { mutableStateOf(false) }
    var drawerAgentsSnapshot by remember { mutableStateOf<List<Agent>?>(null) }
    var drawerAlertsSnapshot by remember { mutableStateOf<List<AgentAlert>?>(null) }
    var drawerProjectOptionsSnapshot by remember { mutableStateOf<List<String>?>(null) }
    fun captureDrawerSnapshot() {
        drawerAgentsSnapshot = controller.agents.toList()
        drawerAlertsSnapshot = controller.alerts.toList()
        drawerProjectOptionsSnapshot = controller.projectOptions()
    }
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

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.setAppInForeground(true)
                Lifecycle.Event.ON_STOP -> controller.setAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        controller.setAppInForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(360)
        appContentReady = true
        delay(EasyCodexMotion.Exit.toLong())
        startupMaskVisible = false
    }

    LaunchedEffect(appContentReady) {
        if (!appContentReady) return@LaunchedEffect
        EasyCodexConnectionService.start(context)
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

    LaunchedEffect(showCliMode, controller.connectionStatus) {
        if (showCliMode && controller.connectionStatus == "connected") {
            controller.startCliConsole()
        }
    }

    BackHandler(enabled = showCliMode) {
        showCliMode = false
    }

    LaunchedEffect(drawerState.currentValue, drawerState.targetValue, controller.agentsRevision) {
        val drawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed
        if (drawerVisible) {
            captureDrawerSnapshot()
        } else {
            delay(120)
            drawerAgentsSnapshot = null
            drawerAlertsSnapshot = null
            drawerProjectOptionsSnapshot = null
        }
    }

    EasyCodexTheme(
        context = context,
        themeMode = controller.themeMode,
        themeColor = controller.themeColor,
        oledMode = controller.oledMode,
    ) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val adaptiveMetrics = easyCodexAdaptiveMetrics(maxWidth, maxHeight)
        Box(Modifier.fillMaxSize()) {
            if (appContentReady) {
                val mainScaffold: @Composable () -> Unit = {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        val titleText = when {
                                            showCliMode -> "Codex CLI"
                                            controller.draftAgent != null -> strings.homeQuestion
                                            controller.activeAgent != null -> ""
                                            else -> "EasyCodex"
                                        }
                                        if (titleText.isNotBlank()) {
                                            Text(
                                                titleText,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TopBarStatusPill(
                                            status = controller.connectionStatus,
                                            text = controller.statusText,
                                        )
                                    }
                                },
                                navigationIcon = {
                                    if (showCliMode) {
                                        IconButton(onClick = rememberHapticClick { showCliMode = false }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                                        }
                                    } else if (!adaptiveMetrics.usePermanentDrawer) {
                                        IconButton(onClick = rememberHapticClick {
                                            captureDrawerSnapshot()
                                            scope.launch { drawerState.open() }
                                        }) {
                                            Icon(Icons.Default.Menu, contentDescription = strings.agentsContentDescription)
                                        }
                                    }
                                },
                                actions = {
                                    if (!showCliMode) {
                                        IconButton(onClick = rememberHapticClick { showCliMode = true }) {
                                            Icon(Icons.Default.Terminal, contentDescription = "Codex CLI")
                                        }
                                    }
                                    if (adaptiveMetrics.usePermanentDrawer) {
                                        IconButton(onClick = rememberHapticClick { showTroubleshooting = true }) {
                                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = strings.connectionTroubleshooting)
                                        }
                                        IconButton(onClick = rememberHapticClick { openSettings() }) {
                                            Icon(Icons.Default.Settings, contentDescription = strings.settingsContentDescription)
                                        }
                                    } else {
                                        var moreMenuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { moreMenuExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = strings.collapse)
                                            }
                                            DropdownMenu(
                                                expanded = moreMenuExpanded,
                                                onDismissRequest = { moreMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.connectionTroubleshooting) },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                                                    onClick = {
                                                        moreMenuExpanded = false
                                                        showTroubleshooting = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(strings.settingsContentDescription) },
                                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                                    onClick = {
                                                        moreMenuExpanded = false
                                                        openSettings()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ),
                            )
                        },
                        bottomBar = {
                            if (!showCliMode) {
                                val draftAgent = controller.draftAgent
                                val activeAgent = controller.activeAgent
                                val composerAgent = draftAgent ?: activeAgent
                                val projectOptions = controller.projectOptions()
                                AdaptiveWidthFrame(maxWidth = adaptiveMetrics.composerMaxWidth) {
                                MessageComposer(
                                    textValue = controller.inputTextValue,
                                    attachments = controller.attachmentDrafts,
                                    queuedFollowUps = composerAgent?.queuedFollowUps.orEmpty(),
                                    enabled = controller.connectionStatus == "connected" && composerAgent != null,
                                    agent = composerAgent,
                                    projectOptions = projectOptions,
                                    canChangeProject = draftAgent != null && !controller.draftProjectLocked,
                                    modelOptions = controller.availableModelOptions(composerAgent),
                                    reasoningOptions = controller.reasoningOptionsFor(composerAgent),
                                    serviceTierOptions = controller.serviceTierOptionsFor(composerAgent),
                                    runtimeCapabilities = controller.runtimeCapabilities,
                                    planModeEnabled = planModeEnabled,
                                    pendingUserInputRequest = controller.userInputRequests.firstOrNull { it.agentId == composerAgent?.id },
                                    pendingPlanReview = controller.planReview?.takeIf { it.agentId == composerAgent?.id },
                                    onTextChange = { controller.inputTextValue = it },
                                    onPlanModeChange = { planModeEnabled = it },
                                    onSubmitUserInput = { request, answers -> controller.respondUserInputRequest(request, answers) },
                                    onDismissUserInput = { request -> controller.deferUserInputRequest(request) },
                                    onOptimizePlan = { review, adjustment -> controller.optimizePlan(review, adjustment) },
                                    onStartPlan = { review -> controller.startPlan(review) },
                                    onDismissPlan = { controller.dismissPlanReview() },
                                    onGuideQueuedFollowUp = { queued ->
                                        val nextText = if (controller.inputText.isBlank()) {
                                            queued.text
                                        } else {
                                            "${controller.inputText.trimEnd()}\n${queued.text}"
                                        }
                                        controller.inputText = nextText
                                    },
                                    onRemoveAttachment = { controller.removeAttachmentDraft(it) },
                                    onProjectChange = { controller.updateDraftProject(it) },
                                    onModelChange = { controller.updateActiveModel(it) },
                                    onReasoningEffortChange = { controller.updateActiveReasoningEffort(it) },
                                    onServiceTierChange = { controller.updateActiveServiceTier(it) },
                                    onPermissionModeChange = { controller.updateActivePermissionMode(it) },
                                    onBrowseDirectories = { path, callback -> controller.browseDirectories(path, callback) },
                                    onAttachFiles = { filePicker.launch("*/*") },
                                    onAttachImages = { imagePicker.launch("image/*") },
                                    onInsertEmoji = { controller.appendToInput(it) },
                                    onVoiceInput = {
                                        val intent = buildSystemVoiceInputIntent(context, strings.voiceInputPrompt)
                                        runCatching { voiceInput.launch(intent) }
                                            .onFailure { controller.statusText = strings.startVoiceInput }
                                    },
                                    onInterrupt = { controller.interruptActiveAgent() },
                                    onSend = { controller.sendActiveMessage(planModeEnabled) },
                                )
                                }
                            }
                        },
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            val draftAgent = controller.draftAgent
                            if (showCliMode) {
                                CliConsoleScreen(
                                    state = controller.cliConsole,
                                    connected = controller.connectionStatus == "connected",
                                    onCwdChange = { controller.updateCliCwd(it) },
                                    onModelChange = { controller.updateCliModel(it) },
                                    onReasoningEffortChange = { controller.updateCliReasoningEffort(it) },
                                    onSandboxModeChange = { controller.updateCliSandboxMode(it) },
                                    onSkipGitRepoCheckChange = { controller.updateCliSkipGitRepoCheck(it) },
                                    projectOptions = controller.projectOptions(),
                                    modelOptions = controller.availableModelOptions(controller.activeAgent),
                                    onBrowseDirectories = { path, callback -> controller.browseDirectories(path, callback) },
                                    onInputChange = { controller.updateCliInput(it) },
                                    onSend = { controller.sendCliCommand() },
                                    onStop = { controller.stopCliCommand() },
                                    onClearWindow = { controller.clearCliWindow() },
                                    onCreateWindow = { controller.createCliWindow() },
                                    onSelectWindow = { controller.selectCliWindow(it) },
                                )
                            } else if (draftAgent != null) {
                                val projectOptions = controller.projectOptions()
                                AdaptiveWidthFrame(
                                    maxWidth = adaptiveMetrics.contentMaxWidth,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    HomeTaskScreen(
                                        draftAgent = draftAgent,
                                        projectOptions = projectOptions,
                                        canChangeProject = !controller.draftProjectLocked,
                                        onProjectChange = { controller.updateDraftProject(it) },
                                        onBrowseDirectories = { path, callback -> controller.browseDirectories(path, callback) },
                                    )
                                }
                            } else {
                                AdaptiveWidthFrame(
                                    maxWidth = adaptiveMetrics.contentMaxWidth,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Conversation(
                                        agent = controller.activeAgent,
                                        layoutMode = controller.appLayout,
                                        emptyMessage = strings.emptyConversation,
                                        relayUrl = controller.relayUrl,
                                        apiKey = controller.apiKey,
                                        onOpenDiffReview = { controller.openDiffReview() },
                                        onOpenPlan = { message ->
                                            controller.activeAgent?.let { controller.showPlanReview(it.id, message) }
                                        },
                                        onOpenSubAgent = { message -> controller.openSubAgentThread(message) },
                                        onUndoFileChanges = { files -> controller.requestUndoFileChanges(files) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (adaptiveMetrics.usePermanentDrawer) {
                    Row(Modifier.fillMaxSize()) {
                        val drawerAgents = drawerAgentsSnapshot ?: controller.agents.toList()
                        val drawerAlerts = drawerAlertsSnapshot ?: controller.alerts.toList()
                        val drawerProjectOptions = drawerProjectOptionsSnapshot ?: controller.projectOptions()
                        AgentDrawer(
                            agents = drawerAgents,
                            alerts = drawerAlerts,
                            projectOptions = drawerProjectOptions,
                            activeAgentId = controller.activeAgentId,
                            modifier = Modifier
                                .width(adaptiveMetrics.drawerWidth)
                                .fillMaxHeight(),
                            permanent = true,
                            onHome = { controller.openHome() },
                            onSelect = { controller.selectAgent(it) },
                            onCreateInProject = { cwd -> controller.startProjectDraft(cwd) },
                            onDeleteAgent = { controller.deleteAgent(it) },
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ) {}
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            mainScaffold()
                        }
                    }
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = true,
                        drawerContent = {
                            val drawerAgents = drawerAgentsSnapshot ?: controller.agents.toList()
                            val drawerAlerts = drawerAlertsSnapshot ?: controller.alerts.toList()
                            val drawerProjectOptions = drawerProjectOptionsSnapshot ?: controller.projectOptions()
                            AgentDrawer(
                                agents = drawerAgents,
                                alerts = drawerAlerts,
                                projectOptions = drawerProjectOptions,
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
                                onDeleteAgent = { controller.deleteAgent(it) },
                            )
                        },
                    ) {
                        mainScaffold()
                    }
                }
            }
            AnimatedVisibility(
                visible = startupMaskVisible,
                enter = fadeIn(animationSpec = EasyCodexMotion.fastTween()),
                exit = fadeOut(animationSpec = EasyCodexMotion.exitTween()),
            ) {
                StartupMask()
            }
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
                onCreate = { name, model, cwd, reasoningEffort, permissionMode ->
                    controller.createAgent(name, model, cwd, reasoningEffort, permissionMode = permissionMode)
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
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "startup mask")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startup pulse",
    )
    val orbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "startup orbit",
    )
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startup shimmer",
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.surfaceContainerLowest,
                        colors.surfaceContainerLow,
                    ),
                ),
            )
            .drawBehind {
                drawCircle(
                    color = colors.primary.copy(alpha = 0.12f + pulse * 0.04f),
                    radius = size.minDimension * (0.36f + pulse * 0.05f),
                    center = Offset(size.width * 0.5f, size.height * 0.43f),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * 0.78f, size.height * 0.22f),
                )
            },
        color = Color.Transparent,
        contentColor = colors.onBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(156.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWide = 5.dp.toPx()
                        val strokeThin = 2.5.dp.toPx()
                        val ringInset = 13.dp.toPx()
                        drawCircle(
                            color = colors.primary.copy(alpha = 0.10f),
                            radius = size.minDimension / 2f - ringInset,
                            style = Stroke(width = strokeWide),
                        )
                        drawArc(
                            color = colors.primary.copy(alpha = 0.76f),
                            startAngle = orbit,
                            sweepAngle = 92f,
                            useCenter = false,
                            style = Stroke(width = strokeWide, cap = StrokeCap.Round),
                            topLeft = Offset(ringInset, ringInset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - ringInset * 2,
                                size.height - ringInset * 2,
                            ),
                        )
                        drawArc(
                            color = colors.tertiary.copy(alpha = 0.48f),
                            startAngle = -orbit * 0.72f,
                            sweepAngle = 54f,
                            useCenter = false,
                            style = Stroke(width = strokeThin, cap = StrokeCap.Round),
                            topLeft = Offset(ringInset * 1.75f, ringInset * 1.75f),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - ringInset * 3.5f,
                                size.height - ringInset * 3.5f,
                            ),
                        )
                        val angle = Math.toRadians(orbit.toDouble())
                        val dotRadius = size.minDimension / 2f - ringInset
                        drawCircle(
                            color = colors.primary,
                            radius = 4.5.dp.toPx(),
                            center = Offset(
                                x = center.x + cos(angle).toFloat() * dotRadius,
                                y = center.y + sin(angle).toFloat() * dotRadius,
                            ),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer {
                                scaleX = 0.98f + pulse * 0.03f
                                scaleY = 0.98f + pulse * 0.03f
                                shadowElevation = 18f + pulse * 8f
                            },
                        shape = RoundedCornerShape(28.dp),
                        color = colors.surface.copy(alpha = 0.90f),
                        tonalElevation = 6.dp,
                    ) {
                        Box(
                            modifier = Modifier.padding(9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            EasyCodexAppIcon(
                                modifier = Modifier.size(74.dp),
                                contentDescription = "EasyCodex",
                            )
                        }
                    }
                }
                Text(
                    "EasyCodex",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Text(
                    strings.preparingEasyCodex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Canvas(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(156.dp)
                        .height(4.dp),
                ) {
                    val radius = 2.dp.toPx()
                    drawRoundRect(
                        color = colors.outlineVariant.copy(alpha = 0.45f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                    val barWidth = size.width * 0.44f
                    val start = (size.width - barWidth) * shimmer
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.35f),
                                colors.primary,
                                colors.tertiary.copy(alpha = 0.70f),
                            ),
                        ),
                        topLeft = Offset(start, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
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
    if (isConversationProjectPath(cwd)) return "空项目"
    val cleaned = cleanNullablePath(cwd) ?: return "未命名项目"
    return cleaned
        .trimEnd('\\', '/')
        .split('\\', '/')
        .lastOrNull { it.isNotBlank() }
        ?: "未命名项目"
}

const val CONVERSATION_PROJECT_PATH = "__easycodex_conversations__"

fun isConversationProjectPath(path: String): Boolean {
    return normalizePathKey(path) == normalizePathKey(CONVERSATION_PROJECT_PATH)
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
    val guideIconColor = if (darkGuide) Color(0xFF242424) else Color(0xFFF1F1F1)
    val guideBorderColor = if (darkGuide) Color(0xFF343434) else Color(0xFFE4E4E4)
    val guidePrimaryTextColor = if (darkGuide) Color(0xFFEDEDED) else Color(0xFF1F1F1F)
    val guideSecondaryTextColor = if (darkGuide) Color(0xFFB8B8B8) else Color(0xFF666666)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 660.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = guideContainerColor,
        titleContentColor = guidePrimaryTextColor,
        textContentColor = guideSecondaryTextColor,
        tonalElevation = 0.dp,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    strings.usageGuideTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    strings.usageGuideIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = guideSecondaryTextColor,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UsageGuideStep(
                    icon = Icons.Default.Settings,
                    title = strings.usageGuideStepConnectionTitle,
                    body = strings.usageGuideStepConnectionBody,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.Folder,
                    title = strings.usageGuideStepProjectTitle,
                    body = strings.usageGuideStepProjectBody,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = strings.usageGuideStepTaskTitle,
                    body = strings.usageGuideStepTaskBody,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.AttachFile,
                    title = strings.usageGuideStepContextTitle,
                    body = strings.usageGuideStepContextBody,
                    iconContainerColor = guideIconColor,
                    borderColor = guideBorderColor,
                    primaryTextColor = guidePrimaryTextColor,
                    secondaryTextColor = guideSecondaryTextColor,
                )
                UsageGuideStep(
                    icon = Icons.Default.TaskAlt,
                    title = strings.usageGuideStepFollowTitle,
                    body = strings.usageGuideStepFollowBody,
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
    iconContainerColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            contentColor = secondaryTextColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

@Composable
fun MessageComposer(
    textValue: TextFieldValue,
    attachments: List<AttachmentDraft>,
    queuedFollowUps: List<QueuedFollowUp>,
    enabled: Boolean,
    agent: Agent?,
    projectOptions: List<String>,
    canChangeProject: Boolean,
    modelOptions: List<CodexModelOption>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    runtimeCapabilities: RuntimeCapabilities,
    planModeEnabled: Boolean,
    pendingUserInputRequest: AgentUserInputRequest? = null,
    pendingPlanReview: PlanReview? = null,
    onTextChange: (TextFieldValue) -> Unit,
    onPlanModeChange: (Boolean) -> Unit,
    onSubmitUserInput: (AgentUserInputRequest, Map<String, String>) -> Unit = { _, _ -> },
    onDismissUserInput: (AgentUserInputRequest) -> Unit = {},
    onOptimizePlan: (PlanReview, String) -> Unit = { _, _ -> },
    onStartPlan: (PlanReview) -> Unit = {},
    onDismissPlan: () -> Unit = {},
    onGuideQueuedFollowUp: (QueuedFollowUp) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onProjectChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onPermissionModeChange: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onAttachFiles: () -> Unit,
    onAttachImages: () -> Unit,
    onInsertEmoji: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onInterrupt: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val text = textValue.text
    var actionsExpanded by remember { mutableStateOf(false) }
    var quickRepliesExpanded by remember { mutableStateOf(false) }
    var runtimePicker by remember { mutableStateOf<RuntimePicker?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var sendAnimationKey by remember { mutableStateOf(0) }
    val sendButtonScale = remember { Animatable(1f) }
    val sendIconTravel = remember { Animatable(0f) }
    val quickReplyPrompts = remember(strings) {
        listOf(
            strings.quickReplyInvestigatePlan,
            strings.quickReplyFixAndTest,
            strings.quickReplyExplainLastError,
            strings.quickReplyContinueLastTask,
        )
    }
    val hasComposerPayload = text.isNotBlank() || attachments.isNotEmpty()
    val showInterruptButton = agent?.isBusy() == true && !hasComposerPayload
    val selectedModel = agent?.model.orEmpty()
    val displayModel = selectedModel.ifBlank { modelOptions.firstOrNull()?.model.orEmpty() }
    val selectedModelLabel = modelOptions.firstOrNull { it.model == displayModel }?.displayName
        ?: displayModel.ifBlank { strings.detectingModel }
    val displayReasoningEffort = agent?.reasoningEffort?.takeIf { it.isNotBlank() }
        ?: modelOptions.firstOrNull { it.model == displayModel }?.defaultReasoningEffort?.takeIf { it.isNotBlank() }
        ?: reasoningOptions.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: DEFAULT_REASONING_EFFORT
    val displayServiceTier = agent?.serviceTier?.takeIf { it.isNotBlank() }
        ?: serviceTierOptions.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: DEFAULT_SERVICE_TIER
    val displayPermissionMode = normalizePermissionMode(agent?.permissionMode)
    val selectedProject = agent?.cwd.orEmpty()
    val selectedProjectLabel = cleanNullablePath(selectedProject)?.let { projectNameFromCwd(it) } ?: strings.selectProject
    LaunchedEffect(sendAnimationKey) {
        if (sendAnimationKey == 0) return@LaunchedEffect
        launch {
            sendButtonScale.snapTo(0.88f)
            sendButtonScale.animateTo(
                targetValue = 1.03f,
                animationSpec = EasyCodexMotion.fastTween(),
            )
            sendButtonScale.animateTo(
                targetValue = 1f,
                animationSpec = EasyCodexMotion.PressSpring,
            )
        }
        launch {
            sendIconTravel.snapTo(0f)
            sendIconTravel.animateTo(
                targetValue = 14f,
                animationSpec = EasyCodexMotion.fastTween(),
            )
            sendIconTravel.animateTo(
                targetValue = 0f,
                animationSpec = EasyCodexMotion.PressSpring,
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            QueuedFollowUpsPanel(
                items = queuedFollowUps,
                enabled = enabled,
                onGuide = onGuideQueuedFollowUp,
            )
            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
                exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = EasyCodexMotion.normalTween(),
                    ),
                ) {
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
            }
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn(animationSpec = EasyCodexMotion.fastTween()) +
                    slideInVertically(animationSpec = EasyCodexMotion.normalTween()) { it / 3 },
                exit = fadeOut(animationSpec = EasyCodexMotion.fastTween()) +
                    slideOutVertically(animationSpec = EasyCodexMotion.exitTween()) { it / 4 },
            ) {
                ComposerFloatingActions(
                    enabled = enabled,
                    planModeEnabled = planModeEnabled,
                    modelLabel = compactModelLabel(selectedModelLabel, strings.model),
                    reasoningLabel = if (runtimeCapabilities.supportsReasoningEffort) {
                        reasoningLabel(displayReasoningEffort, strings)
                    } else {
                        ""
                    },
                    serviceTierLabel = serviceTierLabel(displayServiceTier, strings),
                    permissionModeLabel = permissionModeLabel(displayPermissionMode),
                    showServiceTier = runtimeCapabilities.supportsServiceTier && serviceTierOptions.isNotEmpty(),
                    showProject = canChangeProject && selectedProject.isNotBlank(),
                    projectLabel = selectedProjectLabel,
                    quickRepliesExpanded = quickRepliesExpanded,
                    quickReplyPrompts = quickReplyPrompts,
                    onModelClick = { runtimePicker = RuntimePicker.Model },
                    onReasoningClick = {
                        if (runtimeCapabilities.supportsReasoningEffort && reasoningOptions.isNotEmpty()) {
                            runtimePicker = RuntimePicker.Reasoning
                        } else {
                            runtimePicker = RuntimePicker.Model
                        }
                    },
                    onServiceTierClick = { runtimePicker = RuntimePicker.ServiceTier },
                    onPermissionModeClick = { runtimePicker = RuntimePicker.PermissionMode },
                    onProjectClick = { showProjectPicker = true },
                    onPlanModeChange = onPlanModeChange,
                    onQuickRepliesToggle = { quickRepliesExpanded = !quickRepliesExpanded },
                    onQuickReply = { prompt ->
                        val nextText = if (text.isBlank()) prompt else "${text.trimEnd()}\n$prompt"
                        onTextChange(TextFieldValue(text = nextText, selection = TextRange(nextText.length)))
                        quickRepliesExpanded = false
                        actionsExpanded = false
                    },
                    onAttachFiles = {
                        actionsExpanded = false
                        quickRepliesExpanded = false
                        onAttachFiles()
                    },
                    onAttachImages = {
                        actionsExpanded = false
                        quickRepliesExpanded = false
                        onAttachImages()
                    },
                )
            }
            if (pendingUserInputRequest != null) {
                UserInputComposerCard(
                    request = pendingUserInputRequest,
                    enabled = enabled,
                    onSubmit = { onSubmitUserInput(pendingUserInputRequest, it) },
                    onDismiss = { onDismissUserInput(pendingUserInputRequest) },
                )
            } else if (pendingPlanReview != null) {
                PlanReviewComposerCard(
                    review = pendingPlanReview,
                    enabled = enabled,
                    onStart = { onStartPlan(pendingPlanReview) },
                    onOptimize = { onOptimizePlan(pendingPlanReview, it) },
                    onDismiss = onDismissPlan,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = EasyCodexDesign.ComposerPanelShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AnimatedComposerIconButton(
                                selected = actionsExpanded,
                                onClick = rememberHapticClick {
                                    actionsExpanded = !actionsExpanded
                                    if (!actionsExpanded) quickRepliesExpanded = false
                                },
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
                                value = textValue,
                                onValueChange = onTextChange,
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(strings.sendToEasyCodex) },
                                trailingIcon = {
                                    AnimatedComposerIconButton(
                                        selected = false,
                                        onClick = rememberHapticClick(onVoiceInput),
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
                                shape = RoundedCornerShape(22.dp),
                            )
                            FilledTonalIconButton(
                                onClick = rememberHapticClick {
                                    actionsExpanded = false
                                    quickRepliesExpanded = false
                                    if (showInterruptButton) {
                                        onInterrupt()
                                    } else {
                                        sendAnimationKey += 1
                                        onSend()
                                    }
                                },
                                enabled = enabled && (showInterruptButton || hasComposerPayload),
                                colors = if (showInterruptButton) {
                                    IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                } else {
                                    IconButtonDefaults.filledTonalIconButtonColors()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .scale(sendButtonScale.value),
                            ) {
                                if (showInterruptButton) {
                                    Icon(Icons.Default.Stop, contentDescription = strings.interrupt)
                                } else {
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
                        }
                    }
                }
            }
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
                actionsExpanded = false
            },
            onDismiss = { showProjectPicker = false },
        )
    }

    when (runtimePicker) {
        RuntimePicker.Model -> RuntimeChoiceDialog(
            title = strings.chooseModel,
            options = modelOptions.map { RuntimeChoice(it.model, it.displayName, it.model) },
            selected = displayModel,
            onSelect = {
                onModelChange(it)
                runtimePicker = null
                actionsExpanded = false
            },
            onDismiss = { runtimePicker = null },
        )

        RuntimePicker.Reasoning -> RuntimeChoiceDialog(
            title = strings.chooseReasoning,
            options = reasoningOptions.map { RuntimeChoice(it, reasoningLabel(it, strings), reasoningDescription(it, strings)) },
            selected = displayReasoningEffort,
            onSelect = {
                onReasoningEffortChange(it)
                runtimePicker = null
                actionsExpanded = false
            },
            onDismiss = { runtimePicker = null },
        )

        RuntimePicker.ServiceTier -> RuntimeChoiceDialog(
            title = strings.chooseSpeed,
            options = serviceTierOptions.map { RuntimeChoice(it, serviceTierLabel(it, strings), serviceTierDescription(it, strings)) },
            selected = displayServiceTier,
            onSelect = {
                onServiceTierChange(it)
                runtimePicker = null
                actionsExpanded = false
            },
            onDismiss = { runtimePicker = null },
        )

        RuntimePicker.PermissionMode -> RuntimeChoiceDialog(
            title = "选择权限模式",
            options = permissionModeChoices(),
            selected = displayPermissionMode,
            onSelect = {
                onPermissionModeChange(it)
                runtimePicker = null
                actionsExpanded = false
            },
            onDismiss = { runtimePicker = null },
        )

        RuntimePicker.Project, null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserInputComposerCard(
    request: AgentUserInputRequest,
    enabled: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var answers by remember(request.id) { mutableStateOf(emptyMap<String, String>()) }
    var currentQuestionIndex by remember(request.id) { mutableStateOf(0) }
    val questions = request.questions
    val questionCount = questions.size
    val safeIndex = currentQuestionIndex.coerceIn(0, (questionCount - 1).coerceAtLeast(0))
    val currentQuestion = questions.getOrNull(safeIndex)
    val currentAnswer = currentQuestion?.let { answers[it.id].orEmpty() }.orEmpty()
    val canContinue = currentQuestion != null && currentAnswer.isNotBlank()
    val isLastQuestion = safeIndex >= questionCount - 1
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = EasyCodexDesign.ComposerPanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CodeX 等你回答",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (questionCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { currentQuestionIndex = (safeIndex - 1).coerceAtLeast(0) },
                            enabled = enabled && safeIndex > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一题")
                        }
                        Text(
                            "${safeIndex + 1} / $questionCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = { currentQuestionIndex = (safeIndex + 1).coerceAtMost(questionCount - 1) },
                            enabled = enabled && canContinue && !isLastQuestion,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一题")
                        }
                    }
                }
            }
            currentQuestion?.let { question ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        question.question.ifBlank { question.header.ifBlank { request.detail.ifBlank { "请确认下一步。" } } },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (question.header.isNotBlank() && question.question.isNotBlank()) {
                        Text(
                            question.header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (question.options.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            question.options.forEachIndexed { index, option ->
                                ComposerOptionRow(
                                    number = index + 1,
                                    label = option.label,
                                    description = option.description,
                                    selected = answers[question.id] == option.label,
                                    enabled = enabled,
                                    onClick = { answers = answers + (question.id to option.label) },
                                )
                            }
                        }
                    }
                    if (question.isOther || question.options.isEmpty()) {
                        val selectedOption = question.options.any { option -> option.label == answers[question.id] }
                        OutlinedTextField(
                            value = if (selectedOption) "" else answers[question.id].orEmpty(),
                            onValueChange = { answers = answers + (question.id to it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3,
                            label = { Text("没有我想要的答案，向 CodeX 提问") },
                            enabled = enabled,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = enabled) {
                    Text(LocalAppStrings.current.later)
                }
                Button(
                    enabled = enabled && canContinue,
                    onClick = {
                        if (!isLastQuestion) {
                            currentQuestionIndex = (safeIndex + 1).coerceAtMost(questionCount - 1)
                        } else {
                            onSubmit(answers.mapValues { it.value.trim() }.filterValues { it.isNotBlank() })
                        }
                    },
                ) {
                    Text(if (isLastQuestion) "提交" else "下一题")
                }
            }
        }
    }
}

@Composable
private fun PlanReviewComposerCard(
    review: PlanReview,
    enabled: Boolean,
    onStart: () -> Unit,
    onOptimize: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(review.agentId, review.message.stableKey()) { mutableStateOf("start") }
    var adjustmentText by remember(review.agentId, review.message.stableKey()) { mutableStateOf("") }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = EasyCodexDesign.ComposerPanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("实施此计划？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ComposerOptionRow(
                    number = 1,
                    label = "是，开始任务",
                    selected = selected == "start",
                    enabled = enabled,
                    onClick = { selected = "start" },
                )
                ComposerOptionRow(
                    number = 2,
                    label = "否，我有更多问题",
                    selected = selected == "adjust",
                    enabled = enabled,
                    onClick = { selected = "adjust" },
                )
            }
            AnimatedVisibility(
                visible = selected == "adjust",
                enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
                exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                OutlinedTextField(
                    value = adjustmentText,
                    onValueChange = { adjustmentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("告诉 Codex 如何调整") },
                    placeholder = { Text("例如：先问我更多问题，或把测试步骤写清楚") },
                    enabled = enabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = enabled) {
                    Text(LocalAppStrings.current.later)
                }
                Button(
                    enabled = enabled && (selected == "start" || adjustmentText.isNotBlank()),
                    onClick = {
                        if (selected == "start") onStart() else onOptimize(adjustmentText)
                    },
                ) {
                    Text("提交")
                }
            }
        }
    }
}

@Composable
private fun ComposerOptionRow(
    number: Int,
    label: String,
    description: String = "",
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f)
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hapticClickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$number.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ComposerFloatingActions(
    enabled: Boolean,
    planModeEnabled: Boolean,
    modelLabel: String,
    reasoningLabel: String,
    serviceTierLabel: String,
    permissionModeLabel: String,
    showServiceTier: Boolean,
    showProject: Boolean,
    projectLabel: String,
    quickRepliesExpanded: Boolean,
    quickReplyPrompts: List<String>,
    onModelClick: () -> Unit,
    onReasoningClick: () -> Unit,
    onServiceTierClick: () -> Unit,
    onPermissionModeClick: () -> Unit,
    onProjectClick: () -> Unit,
    onPlanModeChange: (Boolean) -> Unit,
    onQuickRepliesToggle: () -> Unit,
    onQuickReply: (String) -> Unit,
    onAttachFiles: () -> Unit,
    onAttachImages: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var runtimeSettingsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 10.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ComposerActionBubble(
                icon = Icons.Default.Settings,
                label = strings.runtimeSettings,
                detail = listOf(modelLabel, reasoningLabel, permissionModeLabel).filter { it.isNotBlank() }.joinToString(" "),
                selected = runtimeSettingsExpanded,
                enabled = enabled,
                onClick = { runtimeSettingsExpanded = !runtimeSettingsExpanded },
            )
            ComposerActionBubble(
                icon = Icons.Default.TaskAlt,
                label = if (planModeEnabled) strings.planFirst else strings.runDirectly,
                detail = if (planModeEnabled) strings.runDirectly else strings.planFirst,
                selected = planModeEnabled,
                enabled = enabled,
                onClick = { onPlanModeChange(!planModeEnabled) },
            )
            ComposerActionBubble(
                icon = Icons.Default.Search,
                label = strings.quickReplies,
                detail = if (quickRepliesExpanded) strings.quickRepliesCollapsed else strings.commonPrompts,
                selected = quickRepliesExpanded,
                enabled = enabled,
                onClick = onQuickRepliesToggle,
            )
            ComposerActionBubble(
                icon = Icons.Default.AttachFile,
                label = strings.file,
                enabled = enabled,
                onClick = onAttachFiles,
            )
            ComposerActionBubble(
                icon = Icons.Default.Image,
                label = strings.image,
                enabled = enabled,
                onClick = onAttachImages,
            )
        }
        AnimatedVisibility(
            visible = runtimeSettingsExpanded,
            enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
            exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ComposerActionBubble(
                    icon = Icons.Default.Settings,
                    label = strings.chooseModel,
                    detail = modelLabel,
                    enabled = enabled,
                    onClick = onModelClick,
                )
                if (reasoningLabel.isNotBlank()) {
                    ComposerActionBubble(
                        icon = Icons.Default.Check,
                        label = strings.chooseReasoning,
                        detail = reasoningLabel,
                        enabled = enabled,
                        onClick = onReasoningClick,
                    )
                }
                if (showServiceTier) {
                    ComposerActionBubble(
                        icon = Icons.Default.KeyboardArrowDown,
                        label = strings.chooseSpeed,
                        detail = serviceTierLabel,
                        enabled = enabled,
                        onClick = onServiceTierClick,
                    )
                }
                ComposerActionBubble(
                    icon = Icons.Default.Security,
                    label = "权限模式",
                    detail = permissionModeLabel,
                    enabled = enabled,
                    onClick = onPermissionModeClick,
                )
                if (showProject) {
                    ComposerActionBubble(
                        icon = Icons.Default.Folder,
                        label = strings.project,
                        detail = projectLabel,
                        enabled = enabled,
                        onClick = onProjectClick,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = quickRepliesExpanded,
            enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
            exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickReplyPrompts.forEach { prompt ->
                    AssistChip(
                        onClick = { onQuickReply(prompt) },
                        enabled = enabled,
                        label = {
                            Text(
                                prompt,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerActionBubble(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    detail: String = "",
    selected: Boolean = false,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (selected) 0.48f else 0.34f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .hapticClickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 9.dp, top = 7.dp, end = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedFollowUpsPanel(
    items: List<QueuedFollowUp>,
    enabled: Boolean,
    onGuide: (QueuedFollowUp) -> Unit,
) {
    val strings = LocalAppStrings.current
    var selectedItem by remember { mutableStateOf<QueuedFollowUp?>(null) }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    selectedItem?.let { item ->
        QueuedFollowUpDetailDialog(
            item = item,
            enabled = enabled,
            onDismiss = { selectedItem = null },
            onCopy = {
                clipboardScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex queued task", item.text)))
                }
            },
            onGuide = {
                selectedItem = null
                onGuide(item)
            },
        )
    }

    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = easyCodexExpandVertically(expandFrom = Alignment.Bottom),
        exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column {
                items.take(5).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                    QueuedFollowUpRow(
                        item = item,
                        enabled = enabled,
                        onOpen = { selectedItem = item },
                        onGuide = { onGuide(item) },
                    )
                }
                if (items.size > 5) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                    Text(
                        strings.queuedFollowUpsMore(items.size - 5),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedFollowUpRow(
    item: QueuedFollowUp,
    enabled: Boolean,
    onOpen: () -> Unit,
    onGuide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hapticClickable(enabled = enabled, role = Role.Button, onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        Text(
            item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = onGuide,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val strings = LocalAppStrings.current
            Text(
                strings.guideAction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueuedFollowUpDetailDialog(
    item: QueuedFollowUp,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onGuide: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.queuedFollowUpDetail) },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    item.text.ifBlank { strings.queuedFollowUpEmpty },
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGuide,
                enabled = enabled,
            ) {
                Text(strings.guideAction)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy, enabled = item.text.isNotBlank()) {
                    Text(strings.copyContent)
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.close)
                }
            }
        },
    )
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
    val displayModel = selectedModel.ifBlank { modelOptions.firstOrNull()?.model.orEmpty() }
    val selectedModelLabel = modelOptions.firstOrNull { it.model == displayModel }?.displayName
        ?: displayModel.ifBlank { strings.detectingModel }
    val displayReasoningEffort = agent?.reasoningEffort?.takeIf { it.isNotBlank() }
        ?: modelOptions.firstOrNull { it.model == displayModel }?.defaultReasoningEffort?.takeIf { it.isNotBlank() }
        ?: reasoningOptions.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: DEFAULT_REASONING_EFFORT
    val displayServiceTier = agent?.serviceTier?.takeIf { it.isNotBlank() }
        ?: serviceTierOptions.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: DEFAULT_SERVICE_TIER
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
            RuntimeCombinedChip(
                enabled = enabled && (modelOptions.isNotEmpty() || reasoningOptions.isNotEmpty()),
                modelLabel = compactModelLabel(selectedModelLabel, strings.model),
                reasoningLabel = if (runtimeCapabilities.supportsReasoningEffort) {
                    reasoningLabel(displayReasoningEffort, strings)
                } else {
                    ""
                },
                modelOptionsAvailable = modelOptions.isNotEmpty(),
                reasoningOptions = if (runtimeCapabilities.supportsReasoningEffort) reasoningOptions else emptyList(),
                selectedReasoning = displayReasoningEffort,
                selectedModelLabel = selectedModelLabel,
                onReasoningSelect = onReasoningEffortChange,
                onModelSelect = { picker = RuntimePicker.Model },
            )
        }
        if (showRuntime && runtimeCapabilities.supportsServiceTier && serviceTierOptions.isNotEmpty()) {
            FilterChip(
                selected = false,
                enabled = enabled,
                onClick = { picker = RuntimePicker.ServiceTier },
                label = { Text(serviceTierLabel(displayServiceTier, strings)) },
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
            selected = displayModel,
            onSelect = {
                onModelChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Reasoning -> RuntimeChoiceDialog(
            title = strings.chooseReasoning,
            options = reasoningOptions.map { RuntimeChoice(it, reasoningLabel(it, strings), reasoningDescription(it, strings)) },
            selected = displayReasoningEffort,
            onSelect = {
                onReasoningEffortChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.ServiceTier -> RuntimeChoiceDialog(
            title = strings.chooseSpeed,
            options = serviceTierOptions.map { RuntimeChoice(it, serviceTierLabel(it, strings), serviceTierDescription(it, strings)) },
            selected = displayServiceTier,
            onSelect = {
                onServiceTierChange(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Project, RuntimePicker.PermissionMode, null -> Unit
    }
}

@Composable
private fun RuntimeCombinedChip(
    enabled: Boolean,
    modelLabel: String,
    reasoningLabel: String,
    modelOptionsAvailable: Boolean,
    reasoningOptions: List<String>,
    selectedReasoning: String,
    selectedModelLabel: String,
    onReasoningSelect: (String) -> Unit,
    onModelSelect: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            enabled = enabled,
            onClick = { expanded = true },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        listOf(modelLabel, reasoningLabel).filter { it.isNotBlank() }.joinToString(" "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(228.dp),
        ) {
            if (reasoningOptions.isNotEmpty()) {
                Text(
                    strings.reasoningEffort,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reasoningOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                reasoningLabel(option, strings),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        trailingIcon = {
                            if (option == selectedReasoning) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        },
                        onClick = {
                            onReasoningSelect(option)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            DropdownMenuItem(
                text = {
                    Text(
                        selectedModelLabel.ifBlank { strings.detectingModel },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                enabled = modelOptionsAvailable,
                onClick = {
                    expanded = false
                    onModelSelect()
                },
            )
        }
    }
}

private enum class RuntimePicker {
    Project,
    Model,
    Reasoning,
    ServiceTier,
    PermissionMode,
}

private data class RuntimeChoice(val value: String, val label: String, val description: String = "")

private fun compactModelLabel(label: String, fallback: String): String {
    val normalized = label
        .removePrefix("GPT-")
        .removePrefix("gpt-")
        .removePrefix("Codex ")
        .trim()
    return normalized.ifBlank { label.ifBlank { fallback } }
}

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
                            .hapticClickable { onSelect(option.value) },
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

private fun permissionModeChoices(): List<RuntimeChoice> = listOf(
    RuntimeChoice(
        PERMISSION_MODE_DEFAULT_REVIEW,
        "默认审核",
        "工作区内自动执行，越界、联网或高权限请求由手机确认。",
    ),
    RuntimeChoice(
        PERMISSION_MODE_AUTO_REVIEW,
        "自动审核",
        "越界请求交给 Codex 自动审核，必要时才打断你。",
    ),
    RuntimeChoice(
        PERMISSION_MODE_FULL_ACCESS,
        "完全开放",
        "不再审核，适合完全信任的仓库和任务。",
    ),
)

fun permissionModeLabel(value: String): String {
    return when (normalizePermissionMode(value)) {
        PERMISSION_MODE_AUTO_REVIEW -> "自动审核"
        PERMISSION_MODE_FULL_ACCESS -> "完全开放"
        else -> "默认审核"
    }
}

fun normalizeServiceTier(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when {
        normalized.isBlank() -> DEFAULT_SERVICE_TIER
        normalized == "auto" -> DEFAULT_SERVICE_TIER
        normalized == "standard" -> DEFAULT_SERVICE_TIER
        normalized == "default" -> DEFAULT_SERVICE_TIER
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
        animationSpec = EasyCodexMotion.fastTween(),
        label = "composer icon container color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = EasyCodexMotion.fastTween(),
        label = "composer icon content color",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = EasyCodexMotion.SelectionSpring,
        label = "composer icon scale",
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (selected) rotationWhenSelected else 0f,
        animationSpec = EasyCodexMotion.normalTween(),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComposerToolItem(
            icon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
            label = strings.file,
            enabled = enabled,
            onClick = onAttachFiles,
            modifier = Modifier.weight(1f),
        )
        ComposerToolItem(
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            label = strings.image,
            enabled = enabled,
            onClick = onAttachImages,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ComposerToolItem(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .hapticClickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                animationSpec = EasyCodexMotion.fastTween(),
            )
            emojiScale.animateTo(
                targetValue = 1f,
                animationSpec = EasyCodexMotion.SelectionSpring,
            )
        }
        launch {
            highlightAlpha.snapTo(0.58f)
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = EasyCodexMotion.spatialTween(),
            )
        }
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .hapticClickable(enabled = enabled && item != null, role = Role.Button) {
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
    onCreate: (String, String, String, String, String) -> Unit,
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("EasyCodex") }
    var model by remember { mutableStateOf(initialModel.ifBlank { DEFAULT_AGENT_MODEL }) }
    val initialReasoningEffort = modelOptions.firstOrNull { it.model == initialModel }?.defaultReasoningEffort
        ?.ifBlank { DEFAULT_REASONING_EFFORT }
        ?: DEFAULT_REASONING_EFFORT
    var reasoningEffort by remember { mutableStateOf(initialReasoningEffort) }
    var permissionMode by remember { mutableStateOf(DEFAULT_PERMISSION_MODE) }
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
                FilterChip(
                    selected = false,
                    onClick = { picker = RuntimePicker.PermissionMode },
                    label = { Text("权限模式 ${permissionModeLabel(permissionMode)}") },
                )
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
            Button(onClick = { onCreate(name, model, cwd, reasoningEffort, permissionMode) }, enabled = !busy) {
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

        RuntimePicker.PermissionMode -> RuntimeChoiceDialog(
            title = "选择权限模式",
            options = permissionModeChoices(),
            selected = permissionMode,
            onSelect = {
                permissionMode = normalizePermissionMode(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        RuntimePicker.Project, RuntimePicker.ServiceTier, null -> Unit
    }
}
