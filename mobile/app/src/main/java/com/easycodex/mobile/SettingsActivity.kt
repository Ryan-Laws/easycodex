package com.easycodex.mobile

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val EASY_CODEX_PREFS = "easycodex"
const val PREF_RELAY_URL = "relay_url"
const val PREF_API_KEY = "api_key"
const val PREF_LAST_STREAM_SESSION_ID = "last_stream_session_id"
const val PREF_LAST_STREAM_SEQ = "last_stream_seq"
const val PREF_CLIENT_ID = "client_id"
const val PREF_DEFAULT_MODEL = "default_model"
const val PREF_DEFAULT_CWD = "default_cwd"
const val PREF_DEFAULT_REASONING_EFFORT = "default_reasoning_effort"
const val PREF_DEFAULT_SERVICE_TIER = "default_service_tier"
const val PREF_THEME_MODE = "theme_mode"
const val PREF_THEME_COLOR = "theme_color"
const val PREF_APP_LAYOUT = "app_layout"
const val PREF_OLED_MODE = "oled_mode"
const val PREF_USAGE_GUIDE_SEEN = "usage_guide_seen"
const val PREF_DAYLIGHT_THEME_DEFAULT_APPLIED = "daylight_theme_default_applied"

const val DEFAULT_RELAY_URL = "ws://10.0.2.2:3001"
const val DEFAULT_AGENT_MODEL = "gpt-5.5"
const val DEFAULT_AGENT_CWD = "."
const val DEFAULT_REASONING_EFFORT = "medium"
const val DEFAULT_SERVICE_TIER = "default"
const val DEFAULT_THEME_MODE = "light"
const val DEFAULT_THEME_COLOR = "codex"
const val DEFAULT_APP_LAYOUT = "standard"
private const val TEST_NOTIFICATION_CHANNEL_ID = "easycodex-test"
private const val TEST_NOTIFICATION_ID = 71801
private const val SETTINGS_RELAY_REQUEST_TIMEOUT_MS = 30_000L
private const val EASY_CODEX_APP_VERSION = "0.1.0"
private const val EASY_CODEX_RELEASE_API_URL = "https://api.github.com/repos/Ryan-Laws/easycodex/releases/latest"

private fun normalizeDefaultServiceTier(value: String): String {
    return when (value.trim().lowercase()) {
        "", "auto", "standard", "default" -> DEFAULT_SERVICE_TIER
        else -> value.trim().lowercase()
    }
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val diff = (leftParts.getOrNull(index) ?: 0) - (rightParts.getOrNull(index) ?: 0)
        if (diff != 0) return diff
    }
    return 0
}

fun applyDaylightThemeDefault(prefs: android.content.SharedPreferences) {
    if (prefs.getBoolean(PREF_DAYLIGHT_THEME_DEFAULT_APPLIED, false)) return
    prefs.edit()
        .putString(PREF_THEME_MODE, DEFAULT_THEME_MODE)
        .putBoolean(PREF_OLED_MODE, false)
        .putBoolean(PREF_DAYLIGHT_THEME_DEFAULT_APPLIED, true)
        .apply()
}

data class EasyCodexConnectionConfig(
    val relayUrl: String,
    val apiKey: String,
)

data class NotificationAgentPreference(
    val id: String,
    val name: String,
    val level: String,
)

data class NotificationHistoryItem(
    val title: String,
    val body: String,
    val status: String,
    val deliveredCount: Int,
)

fun parseEasyCodexConnectionUri(uri: Uri?): EasyCodexConnectionConfig? {
    if (uri == null) return null

    val isDeepLink = uri.scheme?.equals("easycodex", ignoreCase = true) == true &&
        uri.host?.equals("connect", ignoreCase = true) == true
    val isHttpConnect = (uri.scheme?.equals("http", ignoreCase = true) == true ||
        uri.scheme?.equals("https", ignoreCase = true) == true) &&
        (uri.path.equals("/c", ignoreCase = true) || uri.path.equals("/connect", ignoreCase = true))

    if (!isDeepLink && !isHttpConnect) return null

    val relayUrl = firstQueryParameter(uri, "relayUrl", "webSocketUrl", "wsUrl", "url")
        ?: inferRelayUrlFromHttpConnectUri(uri)
        ?: return null
    val apiKey = firstQueryParameter(uri, "apiKey", "key", "k") ?: return null
    if (!relayUrl.startsWith("ws://", ignoreCase = true) && !relayUrl.startsWith("wss://", ignoreCase = true)) {
        return null
    }

    return EasyCodexConnectionConfig(relayUrl = relayUrl, apiKey = apiKey)
}

private fun inferRelayUrlFromHttpConnectUri(uri: Uri): String? {
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val scheme = when {
        uri.scheme.equals("https", ignoreCase = true) -> "wss"
        uri.scheme.equals("http", ignoreCase = true) -> "ws"
        else -> return null
    }
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return "$scheme://$host$port"
}

fun applyEasyCodexConnectionUri(context: Context, uri: Uri?): Boolean {
    val config = parseEasyCodexConnectionUri(uri) ?: return false
    context.getSharedPreferences(EASY_CODEX_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_RELAY_URL, config.relayUrl)
        .putString(PREF_API_KEY, config.apiKey)
        .apply()
    return true
}

private fun firstQueryParameter(uri: Uri, vararg names: String): String? {
    for (name in names) {
        val value = uri.getQueryParameter(name)?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsApp(onClose = { finish() })
        }
    }
}

private enum class SettingsDestination {
    Connection,
    SessionDefaults,
    Notifications,
    Language,
    Theme,
    ChatLayout,
    App,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsApp(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(EASY_CODEX_PREFS, android.content.Context.MODE_PRIVATE)
            .also(::applyDaylightThemeDefault)
    }
    var relayUrl by remember { mutableStateOf(prefs.getString(PREF_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL) }
    var apiKey by remember { mutableStateOf(prefs.getString(PREF_API_KEY, "") ?: "") }
    var defaultModel by remember { mutableStateOf(prefs.getString(PREF_DEFAULT_MODEL, DEFAULT_AGENT_MODEL) ?: DEFAULT_AGENT_MODEL) }
    var defaultCwd by remember { mutableStateOf(prefs.getString(PREF_DEFAULT_CWD, DEFAULT_AGENT_CWD) ?: DEFAULT_AGENT_CWD) }
    var reasoningEffort by remember {
        mutableStateOf(prefs.getString(PREF_DEFAULT_REASONING_EFFORT, DEFAULT_REASONING_EFFORT) ?: DEFAULT_REASONING_EFFORT)
    }
    var serviceTier by remember {
        mutableStateOf(normalizeDefaultServiceTier(prefs.getString(PREF_DEFAULT_SERVICE_TIER, DEFAULT_SERVICE_TIER) ?: DEFAULT_SERVICE_TIER))
    }
    var themeMode by remember { mutableStateOf(prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE) }
    var themeColor by remember { mutableStateOf(prefs.getString(PREF_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR) }
    var appLayout by remember { mutableStateOf(prefs.getString(PREF_APP_LAYOUT, DEFAULT_APP_LAYOUT) ?: DEFAULT_APP_LAYOUT) }
    var appLanguage by remember { mutableStateOf(prefs.getString(PREF_APP_LANGUAGE, DEFAULT_APP_LANGUAGE) ?: DEFAULT_APP_LANGUAGE) }
    var oledMode by remember { mutableStateOf(prefs.getBoolean(PREF_OLED_MODE, false)) }
    var saveState by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    val strings = appStringsFor(appLanguage)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val updateClient = remember { OkHttpClient() }
    var destination by remember { mutableStateOf<SettingsDestination?>(null) }
    var notificationAgents by remember { mutableStateOf<List<NotificationAgentPreference>>(emptyList()) }
    var notificationHistory by remember { mutableStateOf<List<NotificationHistoryItem>>(emptyList()) }
    var notificationStatus by remember { mutableStateOf("") }
    var notificationSyncing by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var relayClient by remember { mutableStateOf<SettingsRelayClient?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        saveState = if (granted) sendLocalTestNotification(context, strings) else strings.notificationsDisabled
    }

    DisposableEffect(Unit) {
        onDispose {
            relayClient?.close()
        }
    }

    fun save() {
        prefs.edit()
            .putString(PREF_RELAY_URL, relayUrl.trim())
            .putString(PREF_API_KEY, apiKey.trim())
            .putString(PREF_DEFAULT_MODEL, defaultModel.ifBlank { DEFAULT_AGENT_MODEL })
            .putString(PREF_DEFAULT_CWD, defaultCwd.trim().ifBlank { DEFAULT_AGENT_CWD })
            .putString(PREF_DEFAULT_REASONING_EFFORT, reasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT })
            .putString(PREF_DEFAULT_SERVICE_TIER, normalizeDefaultServiceTier(serviceTier.ifBlank { DEFAULT_SERVICE_TIER }))
            .putString(PREF_THEME_MODE, themeMode.ifBlank { DEFAULT_THEME_MODE })
            .putString(PREF_THEME_COLOR, themeColor.ifBlank { DEFAULT_THEME_COLOR })
            .putString(PREF_APP_LAYOUT, appLayout.ifBlank { DEFAULT_APP_LAYOUT })
            .putString(PREF_APP_LANGUAGE, appLanguage.ifBlank { DEFAULT_APP_LANGUAGE })
            .putBoolean(PREF_OLED_MODE, oledMode)
            .apply()
        (context as? Activity)?.setResult(Activity.RESULT_OK)
        saveState = strings.saved
    }

    fun clearConnectionConfig() {
        relayUrl = DEFAULT_RELAY_URL
        apiKey = ""
        relayClient?.close()
        relayClient = null
        notificationAgents = emptyList()
        notificationHistory = emptyList()
        prefs.edit()
            .remove(PREF_RELAY_URL)
            .remove(PREF_API_KEY)
            .apply()
        (context as? Activity)?.setResult(Activity.RESULT_OK)
        saveState = strings.connectionConfigCleared
    }

    fun importConnectionConfig(config: EasyCodexConnectionConfig) {
        relayUrl = config.relayUrl
        apiKey = config.apiKey
        prefs.edit()
            .putString(PREF_RELAY_URL, config.relayUrl)
            .putString(PREF_API_KEY, config.apiKey)
            .putString(PREF_DEFAULT_MODEL, defaultModel.ifBlank { DEFAULT_AGENT_MODEL })
            .putString(PREF_DEFAULT_CWD, defaultCwd.trim().ifBlank { DEFAULT_AGENT_CWD })
            .putString(PREF_DEFAULT_REASONING_EFFORT, reasoningEffort.ifBlank { DEFAULT_REASONING_EFFORT })
            .putString(PREF_DEFAULT_SERVICE_TIER, normalizeDefaultServiceTier(serviceTier.ifBlank { DEFAULT_SERVICE_TIER }))
            .putString(PREF_THEME_MODE, themeMode.ifBlank { DEFAULT_THEME_MODE })
            .putString(PREF_THEME_COLOR, themeColor.ifBlank { DEFAULT_THEME_COLOR })
            .putString(PREF_APP_LAYOUT, appLayout.ifBlank { DEFAULT_APP_LAYOUT })
            .putString(PREF_APP_LANGUAGE, appLanguage.ifBlank { DEFAULT_APP_LANGUAGE })
            .putBoolean(PREF_OLED_MODE, oledMode)
            .apply()
        (context as? Activity)?.setResult(Activity.RESULT_OK)
        saveState = strings.importedFromQr
    }

    fun scanRelayQr() {
        val activity = context as? Activity
        if (activity == null) {
            saveState = strings.scanUnavailable
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(activity, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue.orEmpty()
                val config = parseEasyCodexConnectionUri(Uri.parse(rawValue))
                if (config == null) {
                    saveState = strings.invalidQrCode
                    return@addOnSuccessListener
                }
                importConnectionConfig(config)
            }
            .addOnCanceledListener {
                saveState = strings.scanCanceled
            }
            .addOnFailureListener { error ->
                saveState = error.message ?: "扫码失败"
            }
    }

    fun testLocalNotification() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        saveState = sendLocalTestNotification(context, strings)
    }

    fun syncRelayNotifications() {
        if (notificationSyncing) return
        notificationSyncing = true
        notificationStatus = strings.syncRelayNotifications
        relayClient?.close()
        val nextClient = SettingsRelayClient(relayUrl.trim().ifBlank { DEFAULT_RELAY_URL }, apiKey.trim())
        relayClient = nextClient
        nextClient.connect { connectError ->
            if (connectError != null) {
                notificationSyncing = false
                notificationStatus = connectError
                return@connect
            }
            nextClient.loadNotifications(
                onLoaded = { agents, history ->
                    notificationAgents = agents
                    notificationHistory = history
                    notificationStatus = strings.saved
                    notificationSyncing = false
                },
                onError = { error ->
                    notificationStatus = error
                    notificationSyncing = false
                },
            )
        }
    }

    fun updateRelayNotification(agent: NotificationAgentPreference, level: String) {
        val activeClient = relayClient
        if (activeClient == null) {
            notificationStatus = strings.unsynced
            return
        }
        notificationAgents = notificationAgents.map {
            if (it.id == agent.id) it.copy(level = level) else it
        }
        activeClient.updateNotificationLevel(agent.id, level) { error ->
            notificationStatus = error ?: strings.saved
        }
    }

    fun startApkDownload(version: String, url: String) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            saveState = strings.downloadManagerUnavailable
            return
        }
        val fileName = "EasyCodex.Mobile.$version.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription(strings.downloadStarted(version))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        manager.enqueue(request)
        saveState = strings.downloadStarted(version)
    }

    fun checkForUpdates() {
        if (updateChecking) return
        updateChecking = true
        saveState = strings.checkingForUpdates
        val request = Request.Builder()
            .url(EASY_CODEX_RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        updateClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post {
                    updateChecking = false
                    saveState = strings.updateCheckFailed(e.message ?: "")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body.string()
                mainHandler.post {
                    updateChecking = false
                    if (!response.isSuccessful) {
                        saveState = strings.updateCheckFailed("HTTP ${response.code}")
                        return@post
                    }
                    val release = runCatching { JSONObject(body) }.getOrNull()
                    val tag = release?.optString("tag_name").orEmpty().ifBlank {
                        release?.optString("name").orEmpty()
                    }
                    val latestVersion = tag.trim().removePrefix("v").ifBlank { EASY_CODEX_APP_VERSION }
                    if (compareVersions(latestVersion, EASY_CODEX_APP_VERSION) <= 0) {
                        saveState = strings.appUpToDate(EASY_CODEX_APP_VERSION)
                        return@post
                    }
                    val assets = release?.optJSONArray("assets") ?: JSONArray()
                    var apkUrl = ""
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        val name = asset.optString("name")
                        if (name.contains("Mobile", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                    if (apkUrl.isBlank()) {
                        saveState = strings.noApkFound(latestVersion)
                        return@post
                    }
                    saveState = strings.updateAvailable(latestVersion)
                    startApkDownload(latestVersion, apkUrl)
                }
            }
        })
    }

    BackHandler(enabled = destination != null) {
        destination = null
    }

    EasyCodexTheme(context = context, themeMode = themeMode, themeColor = themeColor, oledMode = oledMode) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                when (destination) {
                                    SettingsDestination.Connection -> strings.connectionSettings
                                    SettingsDestination.SessionDefaults -> strings.sessionDefaults
                                    SettingsDestination.Notifications -> strings.notifications
                                    SettingsDestination.Language -> strings.appLanguage
                                    SettingsDestination.Theme -> strings.themeMode
                                    SettingsDestination.ChatLayout -> strings.chatLayout
                                    SettingsDestination.App -> strings.app
                                    null -> strings.settings
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (destination == null) onClose() else destination = null
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { padding ->
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val forward = initialState == null && targetState != null
                        val offset = if (forward) 72 else -72
                        (slideInHorizontally { offset } + fadeIn())
                            .togetherWith(slideOutHorizontally { -offset } + fadeOut())
                    },
                    label = "settingsDestination",
                ) { activeDestination ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    when (activeDestination) {
                        null -> {
                            item {
                                SettingsMenuItem(
                                    title = strings.connectionSettings,
                                    subtitle = strings.connectionSettingsSubtitle,
                                    icon = Icons.Default.Wifi,
                                    onClick = { destination = SettingsDestination.Connection },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.sessionDefaults,
                                    subtitle = strings.sessionDefaultsSubtitle,
                                    icon = Icons.Default.SmartToy,
                                    onClick = { destination = SettingsDestination.SessionDefaults },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.notifications,
                                    subtitle = strings.notificationsSubtitle,
                                    icon = Icons.Default.Notifications,
                                    onClick = { destination = SettingsDestination.Notifications },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.appLanguage,
                                    subtitle = strings.appLanguageSubtitle,
                                    icon = Icons.Default.Translate,
                                    onClick = { destination = SettingsDestination.Language },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.themeMode,
                                    subtitle = strings.themeSubtitle,
                                    icon = Icons.Default.Palette,
                                    onClick = { destination = SettingsDestination.Theme },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.chatLayout,
                                    subtitle = strings.chatLayoutSubtitle,
                                    icon = Icons.Default.ViewAgenda,
                                    onClick = { destination = SettingsDestination.ChatLayout },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = strings.app,
                                    subtitle = strings.appSubtitle,
                                    icon = Icons.Default.Settings,
                                    onClick = { destination = SettingsDestination.App },
                                )
                            }
                        }

                        SettingsDestination.Connection -> item {
                            SettingsSection(
                                title = strings.connectionSettings,
                                subtitle = strings.connectionSettingsSubtitle,
                                icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                            ) {
                                OutlinedTextField(
                                    value = relayUrl,
                                    onValueChange = { relayUrl = it },
                                    label = { Text(strings.websocketUrl) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = { Text(strings.apiKey) },
                                    singleLine = true,
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            Icon(
                                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (apiKeyVisible) strings.hideApiKey else strings.showApiKey,
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                InfoRow(
                                    title = strings.connectionTarget,
                                    detail = relayUrl.ifBlank { DEFAULT_RELAY_URL },
                                )
                                InfoRow(
                                    title = strings.connectionSecurity,
                                    detail = strings.connectionSecurityDetail,
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(onClick = ::scanRelayQr) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                        Text(strings.scanQrCode)
                                    }
                                    Button(onClick = ::save) {
                                        Text(strings.saveSettings)
                                    }
                                    TextButton(onClick = ::clearConnectionConfig) {
                                        Icon(Icons.Default.Security, contentDescription = null)
                                        Text(strings.clearConnectionConfig)
                                    }
                                    if (saveState.isNotBlank()) {
                                        Text(
                                            saveState,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }

                        SettingsDestination.SessionDefaults -> item {
                            SettingsSection(
                                title = strings.sessionDefaults,
                                subtitle = strings.sessionDefaultsSubtitle,
                                icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                            ) {
                                OutlinedTextField(
                                    value = defaultCwd,
                                    onValueChange = { defaultCwd = it },
                                    label = { Text(strings.defaultProjectPath) },
                                    supportingText = { Text(strings.defaultProjectPathHelp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                InfoRow(title = strings.modelAndRuntime, detail = strings.modelAndRuntimeDetail)
                            }
                        }

                        SettingsDestination.Notifications -> item {
                            SettingsSection(
                                title = strings.notifications,
                                subtitle = strings.notificationsSubtitle,
                                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(onClick = ::testLocalNotification) {
                                        Icon(Icons.Default.Notifications, contentDescription = null)
                                        Text(strings.testNotification)
                                    }
                                    Button(onClick = ::syncRelayNotifications, enabled = !notificationSyncing) {
                                        Text(if (notificationSyncing) strings.syncRelayNotifications else strings.syncNotifications)
                                    }
                                }
                                if (saveState.isNotBlank()) {
                                    Text(
                                        saveState,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (notificationStatus.isNotBlank()) {
                                    Text(
                                        notificationStatus,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                HorizontalDivider()
                                Text(strings.agentNotificationPreferences, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    strings.agentNotificationPreferencesDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (notificationAgents.isEmpty()) {
                                    Text(
                                        strings.unsynced,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    notificationAgents.forEach { agent ->
                                        NotificationAgentPreferenceRow(
                                            agent = agent,
                                            strings = strings,
                                            onLevelChange = { level -> updateRelayNotification(agent, level) },
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Text(strings.recentNotifications, style = MaterialTheme.typography.labelLarge)
                                if (notificationHistory.isEmpty()) {
                                    Text(
                                        strings.noNotifications,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    notificationHistory.forEach { item ->
                                        NotificationHistoryRow(item = item, strings = strings)
                                    }
                                }
                            }
                        }

                        SettingsDestination.Language -> item {
                            SettingsSection(
                                title = strings.appLanguage,
                                subtitle = strings.appLanguageSubtitle,
                                icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                            ) {
                                ChipOptionRow(
                                    options = appLanguageOptions().map { ChipOption(it.value, it.label) },
                                    selected = appLanguage,
                                    onSelect = {
                                        appLanguage = it
                                        prefs.edit().putString(PREF_APP_LANGUAGE, it.ifBlank { DEFAULT_APP_LANGUAGE }).apply()
                                        (context as? Activity)?.setResult(Activity.RESULT_OK)
                                        saveState = appStringsFor(it).saved
                                    },
                                )
                                if (saveState.isNotBlank()) {
                                    Text(
                                        saveState,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        SettingsDestination.Theme -> item {
                            SettingsSection(
                                title = strings.themeMode,
                                subtitle = strings.themeSubtitle,
                                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            ) {
                                Text(strings.themeMode, style = MaterialTheme.typography.labelLarge)
                                ChipOptionRow(
                                    options = listOf(
                                        ChipOption("system", strings.followSystem),
                                        ChipOption("light", strings.light),
                                        ChipOption("dark", strings.dark),
                                    ),
                                    selected = themeMode,
                                    onSelect = {
                                        themeMode = it
                                        save()
                                    },
                                )
                                Text(strings.color, style = MaterialTheme.typography.labelLarge)
                                ChipOptionRow(
                                    options = themeAccentOptions().map { accent ->
                                        ChipOption(
                                            value = accent.value,
                                            label = localizedThemeAccentLabel(accent, strings),
                                            color = accent.previewColor(
                                                dark = when (themeMode) {
                                                    "dark" -> true
                                                    "light" -> false
                                                    else -> isSystemInDarkTheme()
                                                },
                                            ),
                                        )
                                    },
                                    selected = themeColor,
                                    onSelect = {
                                        themeColor = it
                                        save()
                                    },
                                )
                                SwitchSettingRow(
                                    title = strings.oledBlack,
                                    detail = strings.oledBlackDetail,
                                    checked = oledMode,
                                    onCheckedChange = {
                                        oledMode = it
                                        save()
                                    },
                                )
                            }
                        }

                        SettingsDestination.ChatLayout -> item {
                            SettingsSection(
                                title = strings.chatLayout,
                                subtitle = strings.chatLayoutSubtitle,
                                icon = { Icon(Icons.Default.ViewAgenda, contentDescription = null) },
                            ) {
                                ChipOptionRow(
                                    options = listOf(
                                        ChipOption("compact", strings.compact),
                                        ChipOption("standard", strings.standard),
                                        ChipOption("spacious", strings.spacious),
                                    ),
                                    selected = appLayout,
                                    onSelect = {
                                        appLayout = it
                                        save()
                                    },
                                )
                            }
                        }

                        SettingsDestination.App -> item {
                            SettingsSection(
                                title = strings.app,
                                subtitle = strings.appSubtitle,
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            ) {
                                InfoRow(title = strings.version, detail = EASY_CODEX_APP_VERSION)
                                Button(onClick = ::checkForUpdates, enabled = !updateChecking) {
                                    Text(if (updateChecking) strings.checkingForUpdates else strings.checkForUpdates)
                                }
                                if (saveState.isNotBlank()) {
                                    Text(
                                        saveState,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                InfoRow(title = strings.connectionInstructions, detail = strings.connectionInstructionsDetail)
                                HorizontalDivider()
                                InfoRow(
                                    title = strings.dataAndSecurity,
                                    detail = strings.dataAndSecurityDetail,
                                )
                                TextButton(
                                    onClick = {
                                        apiKey = ""
                                        prefs.edit().remove(PREF_API_KEY).apply()
                                        (context as? Activity)?.setResult(Activity.RESULT_OK)
                                        saveState = strings.apiKeyCleared
                                    },
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                    Text(strings.clearLocalApiKey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun EasyCodexTheme(
    context: Context,
    themeMode: String,
    themeColor: String = DEFAULT_THEME_COLOR,
    oledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val dynamicColor = themeColor == "dynamic" && android.os.Build.VERSION.SDK_INT >= 31
    val baseScheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> darkSchemeForAccent(themeColor)
        else -> lightSchemeForAccent(themeColor)
    }
    val colorScheme = if (dark && oledMode) baseScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF121212),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF1A1A1A),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF080808),
        surfaceContainerHighest = Color(0xFF101010),
        outlineVariant = Color(0xFF333333),
    ) else baseScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private data class ThemeAccent(
    val value: String,
    val label: String,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightTertiary: Color,
    val lightTertiaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkTertiary: Color,
    val darkTertiaryContainer: Color,
) {
    fun previewColor(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary
}

private fun themeAccentOptions(): List<ThemeAccent> {
    return listOf(
        ThemeAccent(
            value = "codex",
            label = "沉静绿",
            lightPrimary = Color(0xFF0B6B58),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFD7F2E8),
            lightOnPrimaryContainer = Color(0xFF062B23),
            lightSecondary = Color(0xFF4C5F68),
            lightSecondaryContainer = Color(0xFFDAEAF0),
            lightTertiary = Color(0xFF8A5A2B),
            lightTertiaryContainer = Color(0xFFF2E0C9),
            darkPrimary = Color(0xFF7DD8BE),
            darkOnPrimary = Color(0xFF00382D),
            darkPrimaryContainer = Color(0xFF0F4F42),
            darkOnPrimaryContainer = Color(0xFFC8F4E6),
            darkSecondary = Color(0xFFB8CBD3),
            darkSecondaryContainer = Color(0xFF344952),
            darkTertiary = Color(0xFFE3C19D),
            darkTertiaryContainer = Color(0xFF5B4228),
        ),
        ThemeAccent(
            value = "dynamic",
            label = "系统动态色",
            lightPrimary = Color(0xFF0B6B58),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFD7F2E8),
            lightOnPrimaryContainer = Color(0xFF062B23),
            lightSecondary = Color(0xFF4C5F68),
            lightSecondaryContainer = Color(0xFFDAEAF0),
            lightTertiary = Color(0xFF8A5A2B),
            lightTertiaryContainer = Color(0xFFF2E0C9),
            darkPrimary = Color(0xFF7DD8BE),
            darkOnPrimary = Color(0xFF00382D),
            darkPrimaryContainer = Color(0xFF0F4F42),
            darkOnPrimaryContainer = Color(0xFFC8F4E6),
            darkSecondary = Color(0xFFB8CBD3),
            darkSecondaryContainer = Color(0xFF344952),
            darkTertiary = Color(0xFFE3C19D),
            darkTertiaryContainer = Color(0xFF5B4228),
        ),
        ThemeAccent(
            value = "blue",
            label = "深海蓝",
            lightPrimary = Color(0xFF0061A4),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFD1E4FF),
            lightOnPrimaryContainer = Color(0xFF001D36),
            lightSecondary = Color(0xFF535F70),
            lightSecondaryContainer = Color(0xFFD7E3F7),
            lightTertiary = Color(0xFF6B5778),
            lightTertiaryContainer = Color(0xFFF3DAFF),
            darkPrimary = Color(0xFF9ECAFF),
            darkOnPrimary = Color(0xFF003258),
            darkPrimaryContainer = Color(0xFF00497D),
            darkOnPrimaryContainer = Color(0xFFD1E4FF),
            darkSecondary = Color(0xFFBBC7DB),
            darkSecondaryContainer = Color(0xFF3B4858),
            darkTertiary = Color(0xFFD7BDE4),
            darkTertiaryContainer = Color(0xFF523F5F),
        ),
        ThemeAccent(
            value = "green",
            label = "松石绿",
            lightPrimary = Color(0xFF006C4F),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFF89F8C9),
            lightOnPrimaryContainer = Color(0xFF002115),
            lightSecondary = Color(0xFF4D6357),
            lightSecondaryContainer = Color(0xFFD0E8D9),
            lightTertiary = Color(0xFF3D6373),
            lightTertiaryContainer = Color(0xFFC1E8FB),
            darkPrimary = Color(0xFF6DDBAE),
            darkOnPrimary = Color(0xFF003828),
            darkPrimaryContainer = Color(0xFF00513B),
            darkOnPrimaryContainer = Color(0xFF89F8C9),
            darkSecondary = Color(0xFFB4CCBD),
            darkSecondaryContainer = Color(0xFF354B40),
            darkTertiary = Color(0xFFA5CCDE),
            darkTertiaryContainer = Color(0xFF244C5A),
        ),
        ThemeAccent(
            value = "violet",
            label = "紫罗兰",
            lightPrimary = Color(0xFF6750A4),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFEADDFF),
            lightOnPrimaryContainer = Color(0xFF21005D),
            lightSecondary = Color(0xFF625B71),
            lightSecondaryContainer = Color(0xFFE8DEF8),
            lightTertiary = Color(0xFF7D5260),
            lightTertiaryContainer = Color(0xFFFFD8E4),
            darkPrimary = Color(0xFFD0BCFF),
            darkOnPrimary = Color(0xFF381E72),
            darkPrimaryContainer = Color(0xFF4F378B),
            darkOnPrimaryContainer = Color(0xFFEADDFF),
            darkSecondary = Color(0xFFCCC2DC),
            darkSecondaryContainer = Color(0xFF4A4458),
            darkTertiary = Color(0xFFEFB8C8),
            darkTertiaryContainer = Color(0xFF633B48),
        ),
        ThemeAccent(
            value = "amber",
            label = "琥珀",
            lightPrimary = Color(0xFF7A5800),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFFFDEA4),
            lightOnPrimaryContainer = Color(0xFF261900),
            lightSecondary = Color(0xFF6D5D3F),
            lightSecondaryContainer = Color(0xFFF7E1BB),
            lightTertiary = Color(0xFF4B6545),
            lightTertiaryContainer = Color(0xFFCDEABC),
            darkPrimary = Color(0xFFF8BD2E),
            darkOnPrimary = Color(0xFF402D00),
            darkPrimaryContainer = Color(0xFF5C4200),
            darkOnPrimaryContainer = Color(0xFFFFDEA4),
            darkSecondary = Color(0xFFD9C5A0),
            darkSecondaryContainer = Color(0xFF54462A),
            darkTertiary = Color(0xFFB2CEA2),
            darkTertiaryContainer = Color(0xFF344D30),
        ),
        ThemeAccent(
            value = "rose",
            label = "玫瑰",
            lightPrimary = Color(0xFFBA1A1A),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = Color(0xFFFFDAD6),
            lightOnPrimaryContainer = Color(0xFF410002),
            lightSecondary = Color(0xFF775651),
            lightSecondaryContainer = Color(0xFFFFDAD4),
            lightTertiary = Color(0xFF705C2E),
            lightTertiaryContainer = Color(0xFFFBDFA6),
            darkPrimary = Color(0xFFFFB4AB),
            darkOnPrimary = Color(0xFF690005),
            darkPrimaryContainer = Color(0xFF93000A),
            darkOnPrimaryContainer = Color(0xFFFFDAD6),
            darkSecondary = Color(0xFFE7BDB6),
            darkSecondaryContainer = Color(0xFF5D3F3B),
            darkTertiary = Color(0xFFDEC48C),
            darkTertiaryContainer = Color(0xFF574419),
        ),
    )
}

private fun accentFor(value: String): ThemeAccent {
    return themeAccentOptions().firstOrNull { it.value == value }
        ?: themeAccentOptions().first { it.value == DEFAULT_THEME_COLOR }
}

private fun localizedThemeAccentLabel(accent: ThemeAccent, strings: AppStrings): String {
    if (strings.settings == "设置") return accent.label
    if (strings.settings == "設定") {
        return when (accent.value) {
            "codex" -> "沉靜綠"
            "dynamic" -> "系統動態色"
            "blue" -> "深海藍"
            "green" -> "松石綠"
            "violet" -> "紫羅蘭"
            "amber" -> "琥珀"
            "rose" -> "玫瑰"
            else -> accent.label
        }
    }
    return when (accent.value) {
        "codex" -> "Quiet green"
        "dynamic" -> "Dynamic color"
        "blue" -> "Ocean blue"
        "green" -> "Turquoise green"
        "violet" -> "Violet"
        "amber" -> "Amber"
        "rose" -> "Rose"
        else -> accent.label
    }
}

private fun lightSchemeForAccent(value: String) = accentFor(value).let { accent ->
    lightColorScheme(
        primary = accent.lightPrimary,
        onPrimary = accent.lightOnPrimary,
        primaryContainer = accent.lightPrimaryContainer,
        onPrimaryContainer = accent.lightOnPrimaryContainer,
        secondary = accent.lightSecondary,
        secondaryContainer = accent.lightSecondaryContainer,
        tertiary = accent.lightTertiary,
        tertiaryContainer = accent.lightTertiaryContainer,
        background = Color(0xFFFBFCFD),
        onBackground = Color(0xFF1F201D),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F201D),
        surfaceVariant = Color(0xFFECEFF1),
        onSurfaceVariant = Color(0xFF616661),
        surfaceDim = Color(0xFFE5E8EA),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F9FA),
        surfaceContainer = Color(0xFFF2F4F5),
        surfaceContainerHigh = Color(0xFFECEFF1),
        surfaceContainerHighest = Color(0xFFE3E7E9),
        outline = Color(0xFF777A72),
        outlineVariant = Color(0xFFDDE1E3),
        inverseSurface = Color(0xFF30312E),
        inverseOnSurface = Color(0xFFF5F5EF),
        scrim = Color.Black,
    )
}

private fun darkSchemeForAccent(value: String) = accentFor(value).let { accent ->
    darkColorScheme(
        primary = accent.darkPrimary,
        onPrimary = accent.darkOnPrimary,
        primaryContainer = accent.darkPrimaryContainer,
        onPrimaryContainer = accent.darkOnPrimaryContainer,
        secondary = accent.darkSecondary,
        secondaryContainer = accent.darkSecondaryContainer,
        tertiary = accent.darkTertiary,
        tertiaryContainer = accent.darkTertiaryContainer,
        background = Color(0xFF10110F),
        onBackground = Color(0xFFEDEDE8),
        surface = Color(0xFF191A17),
        onSurface = Color(0xFFEDEDE8),
        surfaceVariant = Color(0xFF2B2D28),
        onSurfaceVariant = Color(0xFFC7CAC1),
        surfaceDim = Color(0xFF10110F),
        surfaceBright = Color(0xFF363832),
        surfaceContainerLowest = Color(0xFF0B0C0A),
        surfaceContainerLow = Color(0xFF161714),
        surfaceContainer = Color(0xFF1E201C),
        surfaceContainerHigh = Color(0xFF272923),
        surfaceContainerHighest = Color(0xFF30332D),
        outline = Color(0xFF92968D),
        outlineVariant = Color(0xFF42463E),
        inverseSurface = Color(0xFFF5F5EF),
        inverseOnSurface = Color(0xFF10110F),
        scrim = Color.Black,
    )
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = EasyCodexDesign.PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EasyCodexIconBubble(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = EasyCodexDesign.PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        icon()
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            content()
        }
    }
}

private data class ChipOption(val value: String, val label: String, val color: Color? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipOptionRow(options: List<ChipOption>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val optionColor = option.color
            FilterChip(
                selected = selected == option.value,
                onClick = { onSelect(option.value) },
                leadingIcon = if (optionColor != null) {
                    {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = optionColor,
                            modifier = Modifier.size(14.dp),
                        ) {
                            Box(Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
                label = {
                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmoothSettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SmoothSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
        label = "settingsSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
        label = "settingsSwitchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.78f),
        label = "settingsSwitchThumbOffset",
    )
    Surface(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = trackColor,
        tonalElevation = if (checked) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(28.dp),
                shape = CircleShape,
                color = thumbColor,
                shadowElevation = 2.dp,
            ) {}
        }
    }
}

@Composable
private fun NotificationAgentPreferenceRow(
    agent: NotificationAgentPreference,
    strings: AppStrings,
    onLevelChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(agent.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        ChipOptionRow(
            options = notificationLevelOptions(strings),
            selected = agent.level,
            onSelect = onLevelChange,
        )
    }
}

@Composable
private fun NotificationHistoryRow(item: NotificationHistoryItem, strings: AppStrings) {
    InfoRow(
        title = item.title.ifBlank { strings.notification },
        detail = "${item.body.ifBlank { strings.noBody }} · ${item.statusLabel(strings)}",
    )
}

private fun notificationLevelOptions(strings: AppStrings): List<ChipOption> {
    return listOf(
        ChipOption("all", strings.all),
        ChipOption("errors", strings.errorsOnly),
        ChipOption("muted", strings.muted),
    )
}

@Composable
private fun InfoRow(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun NotificationHistoryItem.statusLabel(strings: AppStrings): String {
    val traditional = strings.settings == "設定"
    val chinese = strings.settings == "设置"
    return when (status) {
        "sent" -> when {
            traditional -> "已傳送 $deliveredCount 台裝置"
            chinese -> "已发送 $deliveredCount 台设备"
            else -> "Sent to $deliveredCount device${if (deliveredCount == 1) "" else "s"}"
        }
        "muted" -> when {
            traditional -> "已按偏好靜音"
            chinese -> "已按偏好静音"
            else -> "Muted by preference"
        }
        "no_tokens" -> when {
            traditional -> "沒有已註冊遠端通知裝置"
            chinese -> "没有已注册远程通知设备"
            else -> "No registered remote notification devices"
        }
        "error" -> when {
            traditional -> "傳送失敗"
            chinese -> "发送失败"
            else -> "Failed to send"
        }
        else -> status
    }
}

private fun sendLocalTestNotification(context: Context, strings: AppStrings): String {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return strings.notificationsDisabled
    }

    return runCatching {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return strings.notificationServiceUnavailable
        val channel = NotificationChannel(
            TEST_NOTIFICATION_CHANNEL_ID,
            strings.testNotificationChannel,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = strings.testNotificationChannelDescription
        }
        manager.createNotificationChannel(channel)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val smallIcon = if (context.applicationInfo.icon != 0) context.applicationInfo.icon else R.mipmap.ic_launcher
        val notification = Notification.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(strings.testNotificationTitle)
            .setContentText(strings.testNotificationBody)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        manager.notify(TEST_NOTIFICATION_ID, notification)
        strings.testNotificationSent
    }.getOrElse { error ->
        error.message ?: strings.testNotificationFailed
    }
}

private class SettingsRelayClient(
    private val relayUrl: String,
    private val apiKey: String,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, (JSONObject?, String?) -> Unit>()
    private var socket: WebSocket? = null
    private var requestCounter = 0

    fun connect(callback: (String?) -> Unit) {
        val request = try {
            Request.Builder().url(relayUrl).build()
        } catch (_: IllegalArgumentException) {
            callback("中继地址格式不正确")
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendRaw("auth", mapOf("key" to apiKey, "clientId" to "settings")) { _, error ->
                    callback(error)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { handleMessage(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post {
                    failPending(t.message ?: "连接本地中继失败")
                    callback(t.message ?: "连接本地中继失败")
                }
            }
        })
    }

    fun loadNotifications(
        onLoaded: (List<NotificationAgentPreference>, List<NotificationHistoryItem>) -> Unit,
        onError: (String) -> Unit,
    ) {
        sendRaw("list_agents") { agentsResponse, agentsError ->
            if (agentsError != null) {
                onError(agentsError)
                return@sendRaw
            }
            val agents = agentsResponse?.optJSONArray("data") ?: JSONArray()
            sendRaw("get_notification_prefs") { prefsResponse, prefsError ->
                if (prefsError != null) {
                    onError(prefsError)
                    return@sendRaw
                }
                val prefs = prefsResponse?.optJSONObject("data") ?: JSONObject()
                sendRaw("list_notification_history", mapOf("limit" to 20)) { historyResponse, historyError ->
                    if (historyError != null) {
                        onError(historyError)
                        return@sendRaw
                    }
                    onLoaded(parseAgents(agents, prefs), parseHistory(historyResponse?.optJSONArray("data") ?: JSONArray()))
                }
            }
        }
    }

    fun updateNotificationLevel(agentId: String, level: String, callback: (String?) -> Unit) {
        sendRaw("update_notification_prefs", mapOf("agentId" to agentId, "level" to level)) { _, error ->
            callback(error)
        }
    }

    fun close() {
        socket?.close(1000, "Settings closed")
        socket = null
        failPending("连接已关闭")
    }

    private fun sendRaw(
        action: String,
        params: Map<String, Any?> = emptyMap(),
        callback: (JSONObject?, String?) -> Unit,
    ) {
        val activeSocket = socket
        if (activeSocket == null) {
            callback(null, "WebSocket 不可用")
            return
        }
        val requestId = "settings_${++requestCounter}"
        pending[requestId] = callback
        val sent = activeSocket.send(
            JSONObject()
                .put("action", action)
                .put("requestId", requestId)
                .put("params", JSONObject(params))
                .toString(),
        )
        if (!sent) {
            pending.remove(requestId)
            callback(null, "连接已关闭")
            return
        }
        main.postDelayed({
            val timeoutCallback = pending.remove(requestId)
            timeoutCallback?.invoke(null, "请求本地中继超时")
        }, SETTINGS_RELAY_REQUEST_TIMEOUT_MS)
    }

    private fun handleMessage(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = msg.optString("requestId")
        if (requestId.isBlank()) return
        val callback = pending.remove(requestId) ?: return
        if (msg.optString("type") == "error") callback(null, msg.optString("error", "请求失败"))
        else callback(msg, null)
    }

    private fun failPending(error: String) {
        val callbacks = pending.values.toList()
        pending.clear()
        callbacks.forEach { it(null, error) }
    }

    private fun parseAgents(agents: JSONArray, prefs: JSONObject): List<NotificationAgentPreference> {
        return buildList {
            for (index in 0 until agents.length()) {
                val agent = agents.optJSONObject(index) ?: continue
                val id = agent.optString("id")
                if (id.isBlank()) continue
                add(
                    NotificationAgentPreference(
                        id = id,
                        name = agent.optString("name", "EasyCodex"),
                        level = prefs.optString(id, "all"),
                    ),
                )
            }
        }
    }

    private fun parseHistory(history: JSONArray): List<NotificationHistoryItem> {
        return buildList {
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                add(
                    NotificationHistoryItem(
                        title = item.optString("title"),
                        body = item.optString("body"),
                        status = item.optString("status"),
                        deliveredCount = item.optInt("deliveredCount"),
                    ),
                )
            }
        }
    }
}
