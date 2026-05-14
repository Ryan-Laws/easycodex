package com.easycodex.mobile

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

const val PREF_APP_LANGUAGE = "app_language"
const val DEFAULT_APP_LANGUAGE = "system"

data class AppLanguageOption(
    val value: String,
    val label: String,
)

class AppStrings {
    lateinit var settings: String
    lateinit var back: String
    lateinit var appLanguage: String
    lateinit var appLanguageSubtitle: String
    lateinit var languageSystem: String
    lateinit var languageChinese: String
    lateinit var languageEnglish: String
    lateinit var connectionSettings: String
    lateinit var connectionSettingsSubtitle: String
    lateinit var websocketUrl: String
    lateinit var apiKey: String
    lateinit var showApiKey: String
    lateinit var hideApiKey: String
    lateinit var connectionTarget: String
    lateinit var connectionSecurity: String
    lateinit var connectionSecurityDetail: String
    lateinit var clearConnectionConfig: String
    lateinit var connectionConfigCleared: String
    lateinit var scanQrCode: String
    lateinit var saveSettings: String
    lateinit var saved: String
    lateinit var importedFromQr: String
    lateinit var testingConnection: String
    lateinit var connectionTestSucceeded: String
    lateinit var connectionTestFailed: (String) -> String
    lateinit var scanUnavailable: String
    lateinit var invalidQrCode: String
    lateinit var scanCanceled: String
    lateinit var sessionDefaults: String
    lateinit var sessionDefaultsSubtitle: String
    lateinit var defaultProjectPath: String
    lateinit var defaultProjectPathHelp: String
    lateinit var modelAndRuntime: String
    lateinit var modelAndRuntimeDetail: String
    lateinit var notifications: String
    lateinit var notificationsSubtitle: String
    lateinit var syncNotifications: String
    lateinit var testNotification: String
    lateinit var agentPreferences: String
    lateinit var agentNotificationPreferences: String
    lateinit var agentNotificationPreferencesDetail: String
    lateinit var recentNotifications: String
    lateinit var noNotifications: String
    lateinit var notification: String
    lateinit var noBody: String
    lateinit var app: String
    lateinit var appSubtitle: String
    lateinit var version: String
    lateinit var updateChannel: String
    lateinit var updateChannelSubtitle: String
    lateinit var stableChannel: String
    lateinit var betaChannel: String
    lateinit var checkForUpdates: String
    lateinit var checkingForUpdates: String
    lateinit var appUpToDate: (String) -> String
    lateinit var updateAvailable: (String) -> String
    lateinit var updateCheckFailed: (String) -> String
    lateinit var downloadStarted: (String) -> String
    lateinit var noApkFound: (String) -> String
    lateinit var downloadManagerUnavailable: String
    lateinit var updateInstallPromptTitle: (String) -> String
    lateinit var updateInstallPromptBody: (String) -> String
    lateinit var downloadUpdate: String
    lateinit var connectionInstructions: String
    lateinit var connectionInstructionsDetail: String
    lateinit var themeMode: String
    lateinit var themeSubtitle: String
    lateinit var followSystem: String
    lateinit var light: String
    lateinit var dark: String
    lateinit var color: String
    lateinit var chatLayout: String
    lateinit var chatLayoutSubtitle: String
    lateinit var compact: String
    lateinit var standard: String
    lateinit var spacious: String
    lateinit var oledBlack: String
    lateinit var oledBlackDetail: String
    lateinit var dataAndSecurity: String
    lateinit var dataAndSecurityDetail: String
    lateinit var clearLocalApiKey: String
    lateinit var apiKeyCleared: String
    lateinit var unsynced: String
    lateinit var syncRelayNotifications: String
    lateinit var allowNotificationPermission: String
    lateinit var notificationsDisabled: String
    lateinit var notificationServiceUnavailable: String
    lateinit var testNotificationChannel: String
    lateinit var testNotificationChannelDescription: String
    lateinit var testNotificationTitle: String
    lateinit var testNotificationBody: String
    lateinit var testNotificationSent: String
    lateinit var testNotificationFailed: String
    lateinit var notificationAgents: String
    lateinit var notificationRecords: String
    lateinit var all: String
    lateinit var errorsOnly: String
    lateinit var muted: String
    lateinit var notificationLevel: String
    lateinit var reviewChanges: String
    lateinit var scrollToBottom: String
    lateinit var filterAllMessages: String
    lateinit var filterResults: String
    lateinit var filterChanges: String
    lateinit var noMessagesForFilter: String
    lateinit var noChangesToCommit: String
    lateinit var gitCommitComplete: String
    lateinit var homeSubtitle: String
    lateinit var agentsContentDescription: String
    lateinit var stopContentDescription: String
    lateinit var helpContentDescription: String
    lateinit var settingsContentDescription: String
    lateinit var androidNativeConsole: String
    lateinit var createAgentContentDescription: String
    lateinit var emptyConversation: String
    lateinit var preparingEasyCodex: String
    lateinit var easyCodexAgents: String
    lateinit var home: String
    lateinit var noAgents: String
    lateinit var searchTasksOrProjects: String
    lateinit var noMatchingTasks: String
    lateinit var taskActionsContentDescription: String
    lateinit var archiveTask: String
    lateinit var archiveTaskTitle: String
    lateinit var archiveTaskBody: String
    lateinit var archiveRunningTaskBody: String
    lateinit var confirmArchiveTask: String
    lateinit var taskArchived: String
    lateinit var taskArchiveFailed: (String) -> String
    lateinit var runDirectly: String
    lateinit var planFirst: String
    lateinit var copyContent: String
    lateinit var copyFullText: String
    lateinit var expandMore: String
    lateinit var collapse: String
    lateinit var viewFullDiff: String
    lateinit var interrupt: String
    lateinit var interruptFailed: (String) -> String
    lateinit var diffReview: String
    lateinit var readingGitStatusAndDiff: String
    lateinit var fileDiff: String
    lateinit var fullDiff: String
    lateinit var singleFileDiff: String
    lateinit var noDiff: String
    lateinit var diffTruncated: String
    lateinit var filePreview: String
    lateinit var readingFile: String
    lateinit var fileEmptyOrUnavailable: String
    lateinit var copyPath: String
    lateinit var copyDiff: String
    lateinit var commitPreview: String
    lateinit var commitMessage: String
    lateinit var commitChanges: String
    lateinit var confirmCommitChanges: String
    lateinit var confirmCommit: String
    lateinit var commitFilesCount: (Int) -> String
    lateinit var moreFilesCount: (Int) -> String
    lateinit var attachmentFallbackName: String
    lateinit var attachmentTooLarge: (String) -> String
    lateinit var attachmentUploadFailed: (String) -> String
    lateinit var attachmentNoPath: String
    lateinit var pinned: String
    lateinit var projects: String
    lateinit var expandAll: String
    lateinit var collapseAll: String
    lateinit var projectTaskCollapse: String
    lateinit var projectTaskExpand: String
    lateinit var createInProject: String
    lateinit var tasks: (Int) -> String
    lateinit var homeQuestion: String
    lateinit var homePrompt: String
    lateinit var project: String
    lateinit var model: String
    lateinit var reasoningEffort: String
    lateinit var speed: String
    lateinit var detectingRuntime: String
    lateinit var recentTasks: String
    lateinit var chooseProjectDirectory: String
    lateinit var directoryUnreadable: String
    lateinit var readingDirectory: String
    lateinit var useThisDirectory: String
    lateinit var cancel: String
    lateinit var unnamedProject: String
    lateinit var justNow: String
    lateinit var minuteShort: (Long) -> String
    lateinit var hourShort: (Long) -> String
    lateinit var dayShort: (Long) -> String
    lateinit var weekShort: (Long) -> String
    lateinit var fillIn: String
    lateinit var connectionTroubleshooting: String
    lateinit var connected: String
    lateinit var connecting: String
    lateinit var disconnected: String
    lateinit var currentStatus: (String) -> String
    lateinit var messageDetail: (String) -> String
    lateinit var relayAddress: (String) -> String
    lateinit var noDetail: String
    lateinit var notFilled: String
    lateinit var troubleshootingChecklist: String
    lateinit var troubleshootingStepRelay: String
    lateinit var troubleshootingStepNetwork: String
    lateinit var troubleshootingStepPort: String
    lateinit var troubleshootingStepApiKey: String
    lateinit var openSettings: String
    lateinit var close: String
    lateinit var usageGuideTitle: String
    lateinit var usageGuideIntro: String
    lateinit var usageGuideStepConnectionTitle: String
    lateinit var usageGuideStepConnectionBody: String
    lateinit var usageGuideStepProjectTitle: String
    lateinit var usageGuideStepProjectBody: String
    lateinit var usageGuideStepTaskTitle: String
    lateinit var usageGuideStepTaskBody: String
    lateinit var usageGuideStepContextTitle: String
    lateinit var usageGuideStepContextBody: String
    lateinit var usageGuideStepFollowTitle: String
    lateinit var usageGuideStepFollowBody: String
    lateinit var startConfiguration: String
    lateinit var startUsing: String
    lateinit var later: String
    lateinit var sendToEasyCodex: String
    lateinit var openAttachmentPanel: String
    lateinit var openEmojiPanel: String
    lateinit var openVoicePanel: String
    lateinit var send: String
    lateinit var selectProject: String
    lateinit var detectingModel: String
    lateinit var chooseModel: String
    lateinit var chooseReasoning: String
    lateinit var chooseSpeed: String
    lateinit var file: String
    lateinit var image: String
    lateinit var previousPage: String
    lateinit var nextPage: String
    lateinit var startVoiceInput: String
    lateinit var tapToStartVoiceInput: String
    lateinit var createEasyCodexSession: String
    lateinit var name: String
    lateinit var projectPathDesktop: String
    lateinit var creating: String
    lateinit var create: String
    lateinit var low: String
    lateinit var medium: String
    lateinit var high: String
    lateinit var xhigh: String
    lateinit var reasoningLowDescription: String
    lateinit var reasoningMediumDescription: String
    lateinit var reasoningHighDescription: String
    lateinit var reasoningXHighDescription: String
    lateinit var fast: String
    lateinit var flex: String
    lateinit var serviceDefault: String
    lateinit var fastDescription: String
    lateinit var flexDescription: String
    lateinit var defaultSpeedDescription: String
    lateinit var connectionFailed: String
    lateinit var connectionClosed: String
    lateinit var connectionDisconnected: String
    lateinit var connectionDisconnectedNotificationBody: String
    lateinit var failedToConnect: (String) -> String
    lateinit var connectionRefused: (String) -> String
    lateinit var connectionTimeout: (String) -> String
    lateinit var reconnectingIn: (Long) -> String
    lateinit var unauthorized: String
    lateinit var forbidden: String
    lateinit var missingConnection: String
    lateinit var connectingRelay: String
    lateinit var invalidRelayUrl: String
    lateinit var endpointNotFilled: String
}

val LocalAppStrings = staticCompositionLocalOf { appStringsFor(DEFAULT_APP_LANGUAGE) }

fun appLanguageOptions(): List<AppLanguageOption> = listOf(
    AppLanguageOption(DEFAULT_APP_LANGUAGE, "跟随系统 / System"),
    AppLanguageOption("zh", "简体中文"),
    AppLanguageOption("zh-Hant", "繁體中文"),
    AppLanguageOption("en", "English"),
    AppLanguageOption("ja", "日本語"),
    AppLanguageOption("ko", "한국어"),
    AppLanguageOption("es", "Español"),
    AppLanguageOption("fr", "Français"),
    AppLanguageOption("de", "Deutsch"),
)

fun resolvedAppLanguage(value: String?): String {
    val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when (normalized) {
        "zh", "zh-cn", "zh-hans", "chinese" -> "zh"
        "zh-hant", "zh-tw", "zh-hk", "zh-mo", "traditional-chinese" -> "zh-Hant"
        "en", "en-us", "english" -> "en"
        "ja", "ja-jp", "japanese" -> "ja"
        "ko", "ko-kr", "korean" -> "ko"
        "es", "es-es", "es-mx", "spanish" -> "es"
        "fr", "fr-fr", "french" -> "fr"
        "de", "de-de", "german" -> "de"
        else -> resolvedSystemAppLanguage()
    }
}

fun appStringsFor(language: String?): AppStrings {
    return when (resolvedAppLanguage(language)) {
        "zh-Hant" -> TraditionalChineseAppStrings
        "en" -> EnglishAppStrings
        "ja" -> JapaneseAppStrings
        "ko" -> KoreanAppStrings
        "es" -> SpanishAppStrings
        "fr" -> FrenchAppStrings
        "de" -> GermanAppStrings
        else -> ChineseAppStrings
    }
}

private fun resolvedSystemAppLanguage(): String {
    val locale = Locale.getDefault()
    val language = locale.language.lowercase(Locale.ROOT)
    val script = locale.script.lowercase(Locale.ROOT)
    val country = locale.country.uppercase(Locale.ROOT)
    return when (language) {
        "zh" -> if (script == "hant" || country in setOf("TW", "HK", "MO")) "zh-Hant" else "zh"
        "ja" -> "ja"
        "ko" -> "ko"
        "es" -> "es"
        "fr" -> "fr"
        "de" -> "de"
        else -> "en"
    }
}

private val ChineseAppStrings: AppStrings by lazy(LazyThreadSafetyMode.NONE) {
    AppStrings().apply {
    settings = "设置"
    back = "返回"
    appLanguage = "语言"
    appLanguageSubtitle = "选择 EasyCodex App 的显示语言。"
    languageSystem = "跟随系统"
    languageChinese = "简体中文"
    languageEnglish = "English"
    connectionSettings = "连接设置"
    connectionSettingsSubtitle = "配置手机连接本机本地中继所需的信息。"
    websocketUrl = "WebSocket 地址"
    apiKey = "API Key"
    showApiKey = "显示 API Key"
    hideApiKey = "隐藏 API Key"
    connectionTarget = "当前连接目标"
    connectionSecurity = "连接安全"
    connectionSecurityDetail = "ws:// 仅允许本机、模拟器或局域网地址；公网中继请使用 wss://。"
    clearConnectionConfig = "清除连接配置"
    connectionConfigCleared = "已清除连接配置"
    scanQrCode = "扫描二维码"
    saveSettings = "保存设置"
    saved = "已保存"
    importedFromQr = "已从二维码导入"
    testingConnection = "正在检测连接..."
    connectionTestSucceeded = "连接检测成功，设置已保存"
    connectionTestFailed = { "设置已保存，但连接检测失败：$it" }
    scanUnavailable = "当前无法打开扫码"
    invalidQrCode = "二维码不是 EasyCodex 连接配置"
    scanCanceled = "已取消扫码"
    sessionDefaults = "新建会话默认值"
    sessionDefaultsSubtitle = "这里只保存电脑端项目路径；模型和运行参数会在聊天窗口按 relay 检测结果选择。"
    defaultProjectPath = "默认项目路径（电脑端）"
    defaultProjectPathHelp = "`.` 表示 agent-relay 的启动目录；也可以填仓库的绝对路径。"
    modelAndRuntime = "模型与运行参数"
    modelAndRuntimeDetail = "连接 relay 后，在聊天输入框上方按当前电脑端 Codex/API 能力自动显示。"
    notifications = "通知"
    notificationsSubtitle = "快速检查本机通知，并同步中继端偏好和历史。"
    syncNotifications = "同步通知"
    testNotification = "测试通知"
    agentPreferences = "智能体偏好"
    agentNotificationPreferences = "智能体通知偏好"
    agentNotificationPreferencesDetail = "连接本地中继后会显示每个智能体的通知级别。"
    recentNotifications = "最近通知"
    noNotifications = "暂无通知记录。"
    notification = "通知"
    noBody = "没有正文"
    app = "应用"
    appSubtitle = "版本、连接说明和本机数据管理。"
    version = "版本"
    updateChannel = "更新通道"
    updateChannelSubtitle = "正式版只接收稳定发布；Beta 版会检测 GitHub 预发布。"
    stableChannel = "正式版"
    betaChannel = "Beta 版"
    checkForUpdates = "检查更新"
    checkingForUpdates = "正在检查更新"
    appUpToDate = { "当前已是最新版本：$it" }
    updateAvailable = { "发现新版本 $it，可选择下载 APK；不更新也可以继续使用。" }
    updateCheckFailed = { "检查更新失败：${it.ifBlank { "网络或服务异常" }}" }
    downloadStarted = { "已开始下载 EasyCodex $it，完成后可在系统下载通知中安装。" }
    noApkFound = { "已找到版本 $it，但没有可下载的 Android APK。" }
    downloadManagerUnavailable = "系统下载服务不可用"
    updateInstallPromptTitle = { "下载 EasyCodex $it 更新？" }
    updateInstallPromptBody = { "这是可选更新。Android 会通过系统安装界面完成 APK 升级，安装开始后当前 EasyCodex 可能会被系统关闭。" }
    downloadUpdate = "下载更新"
    connectionInstructions = "连接说明"
    connectionInstructionsDetail = "手机相机扫描终端二维码会自动保存地址和 API Key，也可在这里手动修改。"
    themeMode = "主题模式"
    themeSubtitle = "单独设置明暗模式、主题色和 OLED 黑色背景。"
    followSystem = "跟随系统"
    light = "浅色"
    dark = "深色"
    color = "颜色"
    chatLayout = "聊天布局"
    chatLayoutSubtitle = "调整对话列表的密度和留白。"
    compact = "紧凑"
    standard = "标准"
    spacious = "宽松"
    oledBlack = "全 OLED 黑色"
    oledBlackDetail = "深色模式下把主要背景压到纯黑，适合 OLED 屏幕和夜间使用。"
    dataAndSecurity = "数据与安全"
    dataAndSecurityDetail = "中继地址、API Key 和默认会话参数保存在 Android 应用私有存储。"
    clearLocalApiKey = "清除本机 API Key"
    apiKeyCleared = "API Key 已清除"
    unsynced = "未同步"
    syncRelayNotifications = "正在同步中继通知设置"
    allowNotificationPermission = "请先允许通知权限"
    notificationsDisabled = "通知权限未开启，无法发送测试通知"
    notificationServiceUnavailable = "系统通知服务不可用"
    testNotificationChannel = "EasyCodex 测试通知"
    testNotificationChannelDescription = "用于设置页发送本机测试通知"
    testNotificationTitle = "EasyCodex 测试通知"
    testNotificationBody = "通知权限和通知栏展示正常。"
    testNotificationSent = "已发送本机测试通知"
    testNotificationFailed = "发送测试通知失败"
    notificationAgents = "智能体"
    notificationRecords = "记录"
    all = "全部"
    errorsOnly = "仅错误"
    muted = "静音"
    notificationLevel = "通知级别"
    reviewChanges = "验收改动"
    scrollToBottom = "跳到底部"
    filterAllMessages = "全部"
    filterResults = "只看结果"
    filterChanges = "只看改动"
    noMessagesForFilter = "当前筛选没有可显示的消息"
    noChangesToCommit = "没有可提交的改动文件"
    gitCommitComplete = "Git 提交完成"
    homeSubtitle = "需要处理什么任务"
    agentsContentDescription = "智能体"
    stopContentDescription = "停止"
    helpContentDescription = "使用引导"
    settingsContentDescription = "设置"
    androidNativeConsole = "Android 原生控制台"
    createAgentContentDescription = "新建会话"
    emptyConversation = "创建或选择一个智能体开始。"
    preparingEasyCodex = "正在准备 EasyCodex"
    easyCodexAgents = "手机远程控制 Codex"
    home = "总首页"
    noAgents = "还没有智能体"
    searchTasksOrProjects = "搜索任务或项目"
    noMatchingTasks = "没有匹配的任务"
    taskActionsContentDescription = "任务操作"
    archiveTask = "归档任务"
    archiveTaskTitle = "归档任务？"
    archiveTaskBody = "归档后，此任务会从手机端和 Codex 任务列表中移除。"
    archiveRunningTaskBody = "此任务仍在运行。归档会先停止任务，然后从手机端和 Codex 任务列表中移除。"
    confirmArchiveTask = "归档"
    taskArchived = "任务已归档"
    taskArchiveFailed = { "归档任务失败：$it" }
    runDirectly = "直接执行"
    planFirst = "先计划"
    copyContent = "复制内容"
    copyFullText = "复制全文"
    expandMore = "展开更多"
    collapse = "收起"
    viewFullDiff = "查看完整 diff"
    interrupt = "中断"
    interruptFailed = { "中断失败：$it" }
    diffReview = "改动验收"
    readingGitStatusAndDiff = "正在读取 Git 状态和 diff"
    fileDiff = "文件 diff"
    fullDiff = "完整 diff"
    singleFileDiff = "单文件 diff"
    noDiff = "当前没有 diff。"
    diffTruncated = "diff 已截断，可复制全文查看。"
    filePreview = "文件预览"
    readingFile = "正在读取文件..."
    fileEmptyOrUnavailable = "文件为空或暂不可预览。"
    copyPath = "复制路径"
    copyDiff = "复制 diff"
    commitPreview = "提交预览"
    commitMessage = "提交信息"
    commitChanges = "提交改动"
    confirmCommitChanges = "确认提交改动？"
    confirmCommit = "确认提交"
    commitFilesCount = { "将提交 $it 个文件" }
    moreFilesCount = { "+$it 个文件" }
    attachmentFallbackName = "附件"
    attachmentTooLarge = { "$it 超过 12 MB，未上传任何附件" }
    attachmentUploadFailed = { "附件上传失败：$it" }
    attachmentNoPath = "附件已上传，但没有返回文件路径"
    pinned = "置顶"
    projects = "项目"
    expandAll = "展开全部"
    collapseAll = "收起全部"
    projectTaskCollapse = "收起项目任务"
    projectTaskExpand = "展开项目任务"
    createInProject = "在此项目中新建会话"
    tasks = { "$it 个任务" }
    homeQuestion = "要在 EasyCodex 中构建什么？"
    homePrompt = "今天要什么任务？"
    project = "项目"
    model = "模型"
    reasoningEffort = "思考力度"
    speed = "速度"
    detectingRuntime = "参数检测中"
    recentTasks = "最近任务"
    chooseProjectDirectory = "选择项目目录"
    directoryUnreadable = "目录无法读取"
    readingDirectory = "正在读取目录"
    useThisDirectory = "使用此目录"
    cancel = "取消"
    unnamedProject = "未命名项目"
    justNow = "刚刚"
    minuteShort = { "$it 分" }
    hourShort = { "$it 小时" }
    dayShort = { "$it 天" }
    weekShort = { "$it 周" }
    fillIn = "去填写"
    connectionTroubleshooting = "连接排障"
    connected = "已连接"
    connecting = "正在连接"
    disconnected = "未连接"
    currentStatus = { "当前状态：$it" }
    messageDetail = { "提示信息：$it" }
    relayAddress = { "中继地址：$it" }
    noDetail = "暂无详细信息"
    notFilled = "未填写"
    troubleshootingChecklist = "请按顺序检查："
    troubleshootingStepRelay = "1. 电脑上的 agent-relay 是否已经启动。"
    troubleshootingStepNetwork = "2. 手机和电脑是否在同一网络；Android 模拟器可用 10.0.2.2 访问宿主机。"
    troubleshootingStepPort = "3. 端口是否和 agent-relay 实际监听端口一致。"
    troubleshootingStepApiKey = "4. API Key 是否和本地中继配置一致。"
    openSettings = "打开设置"
    close = "关闭"
    usageGuideTitle = "手机使用引导"
    usageGuideIntro = "第一次使用时，先把手机连到电脑上的 EasyCodex 本地中继，然后就可以在手机上创建任务、发送指令和查看结果。"
    usageGuideStepConnectionTitle = "1. 配置连接"
    usageGuideStepConnectionBody = "在设置中填写中继地址和 API Key；如果电脑端提供二维码，可以扫码导入。"
    usageGuideStepProjectTitle = "2. 选择项目"
    usageGuideStepProjectBody = "首页项目卡片用于选择工作目录，也可以从左上角菜单切换最近任务。"
    usageGuideStepTaskTitle = "3. 发送任务"
    usageGuideStepTaskBody = "底部输入框写需求后点击发送；上方标签可调整模型、思考力度和处理速度。"
    usageGuideStepContextTitle = "4. 补充上下文"
    usageGuideStepContextBody = "用附件、图片、语音或表情补充信息，适合在手机上快速描述问题。"
    usageGuideStepFollowTitle = "5. 跟进执行"
    usageGuideStepFollowBody = "任务运行时可在聊天区看实时输出，完成后查看结果即可。"
    startConfiguration = "开始配置"
    startUsing = "开始使用"
    later = "稍后"
    sendToEasyCodex = "发送给电脑中的 Codex CLI"
    openAttachmentPanel = "打开附件面板"
    openEmojiPanel = "打开表情面板"
    openVoicePanel = "打开语音面板"
    send = "发送"
    selectProject = "选择项目"
    detectingModel = "检测模型中"
    chooseModel = "选择模型"
    chooseReasoning = "选择思考强度"
    chooseSpeed = "选择处理速度 / 费用"
    file = "文件"
    image = "图片"
    previousPage = "上一页"
    nextPage = "下一页"
    startVoiceInput = "开始语音输入"
    tapToStartVoiceInput = "点击开始语音输入"
    createEasyCodexSession = "新建 EasyCodex 会话"
    name = "名称"
    projectPathDesktop = "项目路径（电脑端）"
    creating = "创建中"
    create = "创建"
    low = "低"
    medium = "中"
    high = "高"
    xhigh = "极高"
    reasoningLowDescription = "更快，适合简单修改。"
    reasoningMediumDescription = "平衡速度和质量。"
    reasoningHighDescription = "更深入，适合复杂任务。"
    reasoningXHighDescription = "最深入，耗时通常更长。"
    fast = "快速"
    flex = "弹性"
    serviceDefault = "默认"
    fastDescription = "使用当前 Codex 支持的快速档。"
    flexDescription = "通常成本更低，但响应可能更慢或暂时不可用。"
    defaultSpeedDescription = "不指定速度档，交给电脑端 Codex 使用默认处理方式。"
    connectionFailed = "连接本地中继失败，请检查地址、端口和服务状态"
    connectionClosed = "连接已关闭"
    connectionDisconnected = "连接已断开"
    connectionDisconnectedNotificationBody = "正在尝试自动重连。"
    failedToConnect = { "无法连接到本地中继（$it）" }
    connectionRefused = { "本地中继拒绝连接（$it），请确认服务已启动" }
    connectionTimeout = { "连接本地中继超时（$it）" }
    reconnectingIn = { "${it} 秒后自动重连" }
    unauthorized = "认证失败，请检查 API Key"
    forbidden = "认证被拒绝，请检查 API Key 权限"
    missingConnection = "请先填写中继地址和 API key"
    connectingRelay = "正在连接本地中继"
    invalidRelayUrl = "中继地址格式不正确，请填写 ws://地址:端口"
    endpointNotFilled = "未填写地址"
    }
}

private val EnglishAppStrings: AppStrings by lazy(LazyThreadSafetyMode.NONE) {
    AppStrings().apply {
    settings = "Settings"
    back = "Back"
    appLanguage = "Language"
    appLanguageSubtitle = "Choose the display language for the EasyCodex app."
    languageSystem = "System"
    languageChinese = "简体中文"
    languageEnglish = "English"
    connectionSettings = "Connection"
    connectionSettingsSubtitle = "Configure how this phone connects to your local agent relay."
    websocketUrl = "WebSocket URL"
    apiKey = "API Key"
    showApiKey = "Show API Key"
    hideApiKey = "Hide API Key"
    connectionTarget = "Current target"
    connectionSecurity = "Connection security"
    connectionSecurityDetail = "ws:// is allowed only for localhost, emulator, or LAN addresses. Use wss:// for public relays."
    clearConnectionConfig = "Clear connection"
    connectionConfigCleared = "Connection config cleared"
    scanQrCode = "Scan QR code"
    saveSettings = "Save settings"
    saved = "Saved"
    importedFromQr = "Imported from QR code"
    testingConnection = "Testing connection..."
    connectionTestSucceeded = "Connection test passed. Settings saved."
    connectionTestFailed = { "Settings saved, but the connection test failed: $it" }
    scanUnavailable = "Scanner is unavailable right now"
    invalidQrCode = "This QR code is not an EasyCodex connection config"
    scanCanceled = "Scan canceled"
    sessionDefaults = "New Session Defaults"
    sessionDefaultsSubtitle = "Only the desktop project path is saved here. Model and runtime options are detected by the relay in chat."
    defaultProjectPath = "Default project path (desktop)"
    defaultProjectPathHelp = "`.` means the agent-relay startup directory. You can also enter a repository's absolute path."
    modelAndRuntime = "Model and runtime"
    modelAndRuntimeDetail = "After connecting to the relay, options are shown above the composer based on the desktop Codex/API capabilities."
    notifications = "Notifications"
    notificationsSubtitle = "Test local notifications and sync relay preferences and history."
    syncNotifications = "Sync notifications"
    testNotification = "Test notification"
    agentPreferences = "Agent preferences"
    agentNotificationPreferences = "Agent notification preferences"
    agentNotificationPreferencesDetail = "After connecting to the relay, each running agent's notification level will appear here."
    recentNotifications = "Recent notifications"
    noNotifications = "No notification records yet."
    notification = "Notification"
    noBody = "No body"
    app = "App"
    appSubtitle = "Version, connection help, and local data."
    version = "Version"
    updateChannel = "Update channel"
    updateChannelSubtitle = "Stable receives public releases only. Beta checks GitHub pre-releases."
    stableChannel = "Stable"
    betaChannel = "Beta"
    checkForUpdates = "Check for updates"
    checkingForUpdates = "Checking for updates"
    appUpToDate = { "You are on the latest version: $it" }
    updateAvailable = { "Found version $it. You can download the APK now or keep using the app." }
    updateCheckFailed = { "Update check failed: ${it.ifBlank { "network or service error" }}" }
    downloadStarted = { "Started downloading EasyCodex $it. Install it from the system download notification when it finishes." }
    noApkFound = { "Found version $it, but no Android APK is attached." }
    downloadManagerUnavailable = "System download service is unavailable"
    updateInstallPromptTitle = { "Download EasyCodex $it update?" }
    updateInstallPromptBody = { "This update is optional. Android installs APK updates through the system installer, and may close EasyCodex once installation starts." }
    downloadUpdate = "Download update"
    connectionInstructions = "Connection help"
    connectionInstructionsDetail = "Scan the terminal QR code with your phone camera to save the address and API Key, or edit them manually here."
    themeMode = "Theme mode"
    themeSubtitle = "Set light or dark mode, accent color, and OLED black separately."
    followSystem = "System"
    light = "Light"
    dark = "Dark"
    color = "Color"
    chatLayout = "Chat layout"
    chatLayoutSubtitle = "Adjust the message list density and spacing."
    compact = "Compact"
    standard = "Standard"
    spacious = "Spacious"
    oledBlack = "Pure black OLED"
    oledBlackDetail = "Use pure black for major backgrounds in dark mode, useful for OLED screens and night use."
    dataAndSecurity = "Data and security"
    dataAndSecurityDetail = "Relay URL, API Key, and default session settings are stored in Android app-private storage."
    clearLocalApiKey = "Clear local API Key"
    apiKeyCleared = "API Key cleared"
    unsynced = "Not synced"
    syncRelayNotifications = "Syncing relay notification settings"
    allowNotificationPermission = "Allow notification permission first"
    notificationsDisabled = "Notification permission is disabled, so a test notification cannot be sent"
    notificationServiceUnavailable = "System notification service is unavailable"
    testNotificationChannel = "EasyCodex test notification"
    testNotificationChannelDescription = "Used by settings to send a local test notification"
    testNotificationTitle = "EasyCodex test notification"
    testNotificationBody = "Notification permission and notification shade display are working."
    testNotificationSent = "Local test notification sent"
    testNotificationFailed = "Failed to send test notification"
    notificationAgents = "Agents"
    notificationRecords = "Records"
    all = "All"
    errorsOnly = "Errors only"
    muted = "Muted"
    notificationLevel = "Notification level"
    reviewChanges = "Review changes"
    scrollToBottom = "Scroll to bottom"
    filterAllMessages = "All"
    filterResults = "Results"
    filterChanges = "Changes"
    noMessagesForFilter = "No messages match this filter"
    noChangesToCommit = "No changed files to commit"
    gitCommitComplete = "Git commit complete"
    homeSubtitle = "What needs to be done?"
    agentsContentDescription = "Agents"
    stopContentDescription = "Stop"
    helpContentDescription = "Usage guide"
    settingsContentDescription = "Settings"
    androidNativeConsole = "Android native console"
    createAgentContentDescription = "Create session"
    emptyConversation = "Create or select an agent to start."
    preparingEasyCodex = "Preparing EasyCodex"
    easyCodexAgents = "Android remote control for Codex"
    home = "Home"
    noAgents = "No agents yet"
    searchTasksOrProjects = "Search tasks or projects"
    noMatchingTasks = "No matching tasks"
    taskActionsContentDescription = "Task actions"
    archiveTask = "Archive task"
    archiveTaskTitle = "Archive task?"
    archiveTaskBody = "This task will be removed from the mobile and Codex task lists."
    archiveRunningTaskBody = "This task is still running. Archiving it will stop the task and remove it from the mobile and Codex task lists."
    confirmArchiveTask = "Archive"
    taskArchived = "Task archived"
    taskArchiveFailed = { "Failed to archive task: $it" }
    runDirectly = "Run now"
    planFirst = "Plan first"
    copyContent = "Copy content"
    copyFullText = "Copy full text"
    expandMore = "Show more"
    collapse = "Collapse"
    viewFullDiff = "View full diff"
    interrupt = "Interrupt"
    interruptFailed = { "Interrupt failed: $it" }
    diffReview = "Review changes"
    readingGitStatusAndDiff = "Reading Git status and diff"
    fileDiff = "File diff"
    fullDiff = "Full diff"
    singleFileDiff = "Single-file diff"
    noDiff = "No diff right now."
    diffTruncated = "Diff truncated. Copy the full text to inspect it."
    filePreview = "File preview"
    readingFile = "Reading file..."
    fileEmptyOrUnavailable = "File is empty or cannot be previewed."
    copyPath = "Copy path"
    copyDiff = "Copy diff"
    commitPreview = "Commit preview"
    commitMessage = "Commit message"
    commitChanges = "Commit changes"
    confirmCommitChanges = "Commit these changes?"
    confirmCommit = "Confirm commit"
    commitFilesCount = { if (it == 1) "Will commit 1 file" else "Will commit $it files" }
    moreFilesCount = { if (it == 1) "+1 file" else "+$it files" }
    attachmentFallbackName = "Attachment"
    attachmentTooLarge = { "$it is over 12 MB. No attachments were uploaded." }
    attachmentUploadFailed = { "Attachment upload failed: $it" }
    attachmentNoPath = "Attachment uploaded, but no file path was returned"
    pinned = "Pinned"
    projects = "Projects"
    expandAll = "Expand all"
    collapseAll = "Collapse all"
    projectTaskCollapse = "Collapse project tasks"
    projectTaskExpand = "Expand project tasks"
    createInProject = "Create a session in this project"
    tasks = { if (it == 1) "1 task" else "$it tasks" }
    homeQuestion = "What do you want to build in EasyCodex?"
    homePrompt = "What are we doing today?"
    project = "Project"
    model = "Model"
    reasoningEffort = "Reasoning"
    speed = "Speed"
    detectingRuntime = "Detecting options"
    recentTasks = "Recent tasks"
    chooseProjectDirectory = "Choose project directory"
    directoryUnreadable = "Directory could not be read"
    readingDirectory = "Reading directory"
    useThisDirectory = "Use this directory"
    cancel = "Cancel"
    unnamedProject = "Untitled project"
    justNow = "Just now"
    minuteShort = { "${it}m" }
    hourShort = { "${it}h" }
    dayShort = { "${it}d" }
    weekShort = { "${it}w" }
    fillIn = "Fill in"
    connectionTroubleshooting = "Connection troubleshooting"
    connected = "Connected"
    connecting = "Connecting"
    disconnected = "Disconnected"
    currentStatus = { "Current status: $it" }
    messageDetail = { "Message: $it" }
    relayAddress = { "Relay address: $it" }
    noDetail = "No details"
    notFilled = "Not filled in"
    troubleshootingChecklist = "Check these in order:"
    troubleshootingStepRelay = "1. Make sure agent-relay is running on your computer."
    troubleshootingStepNetwork = "2. Make sure phone and computer are on the same network. Android Emulator can use 10.0.2.2 for the host."
    troubleshootingStepPort = "3. Make sure the port matches the agent-relay listener."
    troubleshootingStepApiKey = "4. Make sure the API Key matches the agent relay configuration."
    openSettings = "Open settings"
    close = "Close"
    usageGuideTitle = "Phone usage guide"
    usageGuideIntro = "First connect your phone to the EasyCodex relay running on your computer. Then you can create tasks, send instructions, and review results from your phone."
    usageGuideStepConnectionTitle = "1. Configure connection"
    usageGuideStepConnectionBody = "Enter the relay address and API Key in Settings. If the desktop provides a QR code, scan it to import them."
    usageGuideStepProjectTitle = "2. Choose a project"
    usageGuideStepProjectBody = "Use the project card on Home to choose a working directory, or switch recent tasks from the menu."
    usageGuideStepTaskTitle = "3. Send a task"
    usageGuideStepTaskBody = "Type your request in the composer and send it. The chips above it adjust model, reasoning, and speed."
    usageGuideStepContextTitle = "4. Add context"
    usageGuideStepContextBody = "Use files, images, voice, or emoji to add detail when describing issues from your phone."
    usageGuideStepFollowTitle = "5. Follow execution"
    usageGuideStepFollowBody = "Watch live output in chat while tasks run, then review the result when it finishes."
    startConfiguration = "Start setup"
    startUsing = "Start using"
    later = "Later"
    sendToEasyCodex = "Send to the Codex CLI on your computer"
    openAttachmentPanel = "Open attachment panel"
    openEmojiPanel = "Open emoji panel"
    openVoicePanel = "Open voice panel"
    send = "Send"
    selectProject = "Select project"
    detectingModel = "Detecting model"
    chooseModel = "Choose model"
    chooseReasoning = "Choose reasoning effort"
    chooseSpeed = "Choose speed / cost"
    file = "File"
    image = "Image"
    previousPage = "Previous"
    nextPage = "Next"
    startVoiceInput = "Start voice input"
    tapToStartVoiceInput = "Tap to start voice input"
    createEasyCodexSession = "New EasyCodex session"
    name = "Name"
    projectPathDesktop = "Project path (desktop)"
    creating = "Creating"
    create = "Create"
    low = "Low"
    medium = "Medium"
    high = "High"
    xhigh = "Extra high"
    reasoningLowDescription = "Faster, good for simple edits."
    reasoningMediumDescription = "Balances speed and quality."
    reasoningHighDescription = "Deeper, better for complex tasks."
    reasoningXHighDescription = "Deepest reasoning, usually takes longer."
    fast = "Fast"
    flex = "Flex"
    serviceDefault = "Default"
    fastDescription = "Use the fast tier supported by the current Codex runtime."
    flexDescription = "Usually cheaper, but may be slower or temporarily unavailable."
    defaultSpeedDescription = "Do not specify a speed tier; let desktop Codex use its default behavior."
    connectionFailed = "Failed to connect to the relay. Check address, port, and service status."
    connectionClosed = "Connection closed"
    connectionDisconnected = "Connection disconnected"
    connectionDisconnectedNotificationBody = "EasyCodex is trying to reconnect automatically."
    failedToConnect = { "Could not connect to relay ($it)" }
    connectionRefused = { "Relay refused the connection ($it). Make sure it is running." }
    connectionTimeout = { "Timed out connecting to relay ($it)" }
    reconnectingIn = { "Reconnecting in ${it}s" }
    unauthorized = "Authentication failed. Check the API Key."
    forbidden = "Authentication was rejected. Check API Key permissions."
    missingConnection = "Enter relay address and API Key first"
    connectingRelay = "Connecting to agent relay"
    invalidRelayUrl = "Relay address format is invalid. Use ws://address:port"
    endpointNotFilled = "No address"
    }
}

private val TraditionalChineseAppStrings: AppStrings
    get() = ChineseAppStrings

private val JapaneseAppStrings: AppStrings
    get() = EnglishAppStrings

private val KoreanAppStrings: AppStrings
    get() = EnglishAppStrings

private val SpanishAppStrings: AppStrings
    get() = EnglishAppStrings

private val FrenchAppStrings: AppStrings
    get() = EnglishAppStrings

private val GermanAppStrings: AppStrings
    get() = EnglishAppStrings
