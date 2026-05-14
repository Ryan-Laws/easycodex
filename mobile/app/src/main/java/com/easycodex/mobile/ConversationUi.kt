package com.easycodex.mobile

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class ConversationLayoutMetrics(
    val listPadding: PaddingValues,
    val itemSpacing: androidx.compose.ui.unit.Dp,
    val bubblePadding: androidx.compose.ui.unit.Dp,
    val bubbleShape: androidx.compose.ui.unit.Dp,
    val userBubbleWidth: Float,
    val assistantBubbleWidth: Float,
)

private enum class ConversationFilter {
    All,
    Results,
    Changes,
}

private const val LONG_DETAIL_TEXT_LIMIT = 6_000
private const val LONG_MESSAGE_TEXT_LIMIT = 1_600
private const val MESSAGE_PREVIEW_TEXT_LIMIT = 900
private const val MESSAGE_PREVIEW_LINE_LIMIT = 14
private const val LONG_CODE_TEXT_LIMIT = 1_200

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
    notificationLevelState: NotificationLevelState? = null,
    onInterrupt: () -> Unit = {},
    onOpenDiffReview: () -> Unit = {},
    onNotificationLevelChange: (String, String) -> Unit = { _, _ -> },
    onOpenPlan: (AgentMessage) -> Unit = {},
) {
    val metrics = conversationLayoutMetrics(layoutMode)
    val strings = LocalAppStrings.current
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var filter by remember(agent?.id) { mutableStateOf(ConversationFilter.All) }
    var initializedAtBottom by remember(agent?.id, filter) { mutableStateOf(false) }

    if (agent == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val filteredMessages by remember(agent.messages, filter) {
        derivedStateOf {
        val visibleMessages = agent.messages.filter { it.isPrimaryConversationVisible() }
        when (filter) {
            ConversationFilter.All -> visibleMessages
            ConversationFilter.Results -> visibleMessages.filter { message ->
                message.type in setOf("agent", "plan", "status")
            }
            ConversationFilter.Changes -> visibleMessages.filter { it.type == "file_change" }
        }
        }
    }
    val filteredMessageKeys = remember(filteredMessages) { filteredMessages.uniqueLazyKeys() }
    val lastListIndex = filteredMessages.size
    val lastMessageIndex = (lastListIndex - 1).coerceAtLeast(0)
    val lastMessage = filteredMessages.lastOrNull()
    val lastMessageStreamMarker = lastMessage?.let { "${it.stableKey()}:${it.text.length}:${it.streaming}" }
    val isAtBottom by remember(listState, lastListIndex) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            visibleItems.isEmpty() || (visibleItems.lastOrNull()?.index ?: 0) >= lastMessageIndex
        }
    }
    val shouldFollowOutput by remember(listState, lastListIndex) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            visibleItems.isEmpty() || (visibleItems.lastOrNull()?.index ?: 0) >= lastMessageIndex
        }
    }
    LaunchedEffect(agent.id, filter, lastListIndex) {
        if (lastListIndex > 0 && !initializedAtBottom) {
            listState.animateScrollToItem(lastMessageIndex)
            initializedAtBottom = true
        }
    }
    LaunchedEffect(agent.id, filter, lastListIndex, lastMessageStreamMarker) {
        if (lastListIndex > 0 && shouldFollowOutput) listState.animateScrollToItem(lastMessageIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ConversationStatusHeader(
                    agent = agent,
                    filter = filter,
                    notificationLevelState = notificationLevelState,
                    onFilterChange = { filter = it },
                    onInterrupt = onInterrupt,
                    onOpenDiffReview = onOpenDiffReview,
                    onNotificationLevelChange = onNotificationLevelChange,
                )
                AnimatedVisibility(
                    visible = !agent.activity.isNullOrBlank(),
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(agent.activity.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (filter != ConversationFilter.All && filteredMessages.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(strings.noMessagesForFilter, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
                contentPadding = metrics.listPadding,
            ) {
            itemsIndexed(
                filteredMessages,
                key = { index, message -> filteredMessageKeys.getOrNull(index) ?: "${message.stableKey()}#$index" },
            ) { _, message ->
                MessageBubble(
                    message = message,
                    metrics = metrics,
                    onOpenPlan = { onOpenPlan(message) },
                    onOpenDiffReview = onOpenDiffReview,
                )
            }
            }
        }

        AnimatedVisibility(
            visible = lastListIndex > 0 && !isAtBottom,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    scrollScope.launch {
                        listState.animateScrollToItem(lastMessageIndex)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = strings.scrollToBottom)
            }
        }
    }
}

@Composable
private fun ConversationStatusHeader(
    agent: Agent,
    filter: ConversationFilter,
    notificationLevelState: NotificationLevelState?,
    onFilterChange: (ConversationFilter) -> Unit,
    onInterrupt: () -> Unit,
    onOpenDiffReview: () -> Unit,
    onNotificationLevelChange: (String, String) -> Unit,
) {
    val strings = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ConversationFilterChip(strings.filterAllMessages, ConversationFilter.All, filter, onFilterChange)
                ConversationFilterChip(strings.filterResults, ConversationFilter.Results, filter, onFilterChange)
                ConversationFilterChip(strings.filterChanges, ConversationFilter.Changes, filter, onFilterChange)
                if (agent.messages.any { it.type == "file_change" }) {
                    AssistChip(
                        onClick = onOpenDiffReview,
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(strings.reviewChanges) },
                    )
                }
                if (agent.isBusy()) {
                    AssistChip(
                        onClick = onInterrupt,
                        leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(strings.interrupt) },
                    )
                }
            }
            NotificationLevelRow(
                agentId = agent.id,
                state = notificationLevelState,
                onLevelChange = onNotificationLevelChange,
            )
        }
    }
}

@Composable
private fun NotificationLevelRow(
    agentId: String,
    state: NotificationLevelState?,
    onLevelChange: (String, String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val activeLevel = state?.takeIf { it.agentId == agentId }?.level ?: "all"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            strings.notificationLevel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            "all" to strings.all,
            "errors" to strings.errorsOnly,
            "muted" to strings.muted,
        ).forEach { (level, label) ->
            FilterChip(
                selected = activeLevel == level,
                enabled = state?.loading != true,
                onClick = { onLevelChange(agentId, level) },
                label = { Text(label) },
            )
        }
        if (state?.loading == true) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
    state?.error?.takeIf { it.isNotBlank() }?.let { error ->
        Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ConversationFilterChip(
    label: String,
    value: ConversationFilter,
    selected: ConversationFilter,
    onSelect: (ConversationFilter) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

fun agentStatusLabel(agent: Agent): String {
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

fun AgentMessage.stableKey(): String {
    return itemId ?: "${timestamp}_${role}_${type}"
}

private fun List<AgentMessage>.uniqueLazyKeys(): List<String> {
    val totals = groupingBy { it.stableKey() }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return map { message ->
        val baseKey = message.stableKey()
        if ((totals[baseKey] ?: 0) <= 1) {
            baseKey
        } else {
            val occurrence = seen.getOrDefault(baseKey, 0)
            seen[baseKey] = occurrence + 1
            "$baseKey#$occurrence"
        }
    }
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
    val fileEntries: List<FileChangeEntry> = emptyList(),
)

private data class FileChangeStats(
    val files: List<String>,
    val additions: Int,
    val deletions: Int,
    val entries: List<FileChangeEntry>,
)

private data class FileChangeEntry(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
)

private fun AgentMessage.isDetailMessage(): Boolean {
    return type == "command" ||
        type == "command_output" ||
        type == "file_change" ||
        type == "sub_agent" ||
        type == "tool" ||
        type == "tool_call"
}

private fun AgentMessage.isInternalStatusMessage(): Boolean {
    if (type != "status") return false
    val normalizedText = text.trimStart()
    return itemId?.startsWith("tokens_") == true ||
        itemId?.startsWith("queued_followups_") == true ||
        normalizedText.startsWith("Token usage") ||
        normalizedText == "Token usage updated." ||
        normalizedText.startsWith("已排队 ") && normalizedText.contains("个后续任务")
}

private fun AgentMessage.isPrimaryConversationVisible(): Boolean {
    if (role == "user" || type == "user") return false
    if (text.isBlank()) return false
    if (text.trim() == "已加载项目上下文。") return false
    if (isInternalStatusMessage()) return false
    return true
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
    var status = ""
    var exit = ""
    var duration = ""
    var command = ""
    raw.lineSequence().take(80).forEach { line ->
        val trimmed = line.trim()
        when {
            status.isBlank() && trimmed.startsWith("status:", ignoreCase = true) -> {
                status = trimmed.substringAfter(':').trim()
            }

            exit.isBlank() && trimmed.startsWith("exit:", ignoreCase = true) -> {
                exit = trimmed.substringAfter(':').trim()
            }

            duration.isBlank() && trimmed.startsWith("duration:", ignoreCase = true) -> {
                duration = formatDurationToken(trimmed.substringAfter(':').trim())
            }

            command.isBlank() &&
                trimmed.isNotBlank() &&
                trimmed !in setOf("运行命令", "命令已完成", "正在运行命令。", "命令执行完成。", "命令已完成，输出已省略。") &&
                !trimmed.startsWith("cwd:", ignoreCase = true) &&
                !trimmed.startsWith("status:", ignoreCase = true) &&
                !trimmed.startsWith("exit:", ignoreCase = true) &&
                !trimmed.startsWith("duration:", ignoreCase = true) -> {
                command = trimmed
            }
        }
    }
    val title = when {
        isOutput && duration.isNotBlank() -> "已处理 $duration"
        isOutput -> status.ifBlank { "已处理" }
        command.isNotBlank() -> "已运行 $command"
        else -> "命令"
    }.compactDetailTitle()
    val subtitleParts = if (isOutput) {
        listOf(command, exit.takeIf { it.isNotBlank() }?.let { "exit $it" }.orEmpty(), status)
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
    }.compactDetailTitle()
    val subtitle = listOf(
        status.ifBlank { "已处理" },
        if (stats.additions + stats.deletions > 0) "+${stats.additions} -${stats.deletions}" else "",
        paths.firstOrNull().orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return DetailDisplay("文件改动", title, subtitle, raw, stats.additions, stats.deletions, paths, stats.entries)
}

private fun fileChangeStats(raw: String): FileChangeStats {
    val paths = linkedSetOf<String>()
    val entryStats = linkedMapOf<String, FileChangeEntry>()
    var additions = 0
    var deletions = 0
    val rawLooksLikeDiff = raw.lineSequence().any { line ->
        line.startsWith("diff --git ") || line.startsWith("@@")
    }
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val summaryEntry = parseFileSummaryLine(trimmed)
        if (summaryEntry != null) {
            paths.add(summaryEntry.path)
            entryStats[summaryEntry.path] = summaryEntry
            return@forEach
        }
        when {
            rawLooksLikeDiff && line.startsWith("+") && !line.startsWith("+++") -> additions += 1
            rawLooksLikeDiff && line.startsWith("-") && !line.startsWith("---") -> deletions += 1
        }
        val diffPath = Regex("^diff --git a/(.+?) b/(.+)$").find(trimmed)?.groupValues?.getOrNull(2)
        val newPath = Regex("^\\+\\+\\+ b/(.+)$").find(trimmed)?.groupValues?.getOrNull(1)
        val oldPath = Regex("^--- a/(.+)$").find(trimmed)?.groupValues?.getOrNull(1)
        val plainPath = trimmed.takeIf { value ->
            value.isNotBlank() &&
                value != "文件改动" &&
                value != "Files:" &&
                !value.startsWith("Files:", ignoreCase = true) &&
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
    val entries = if (entryStats.isNotEmpty()) {
        entryStats.values.toList()
    } else {
        paths.map { FileChangeEntry(it) }
    }
    val summaryAdditions = entries.sumOf { it.additions }
    val summaryDeletions = entries.sumOf { it.deletions }
    return FileChangeStats(
        files = paths.toList(),
        additions = if (summaryAdditions > 0 || summaryDeletions > 0) summaryAdditions else additions,
        deletions = if (summaryAdditions > 0 || summaryDeletions > 0) summaryDeletions else deletions,
        entries = entries,
    )
}

private fun parseFileSummaryLine(trimmed: String): FileChangeEntry? {
    val bullet = Regex("^[-•]\\s+(.+?)(?:\\s+\\+(\\d+)\\s+-(\\d+))?$").find(trimmed) ?: return null
    val path = bullet.groupValues.getOrNull(1)?.trim().orEmpty()
    if (path.isBlank() || (!path.contains("/") && !path.contains("\\") && !path.contains("."))) return null
    return FileChangeEntry(
        path = path.removePrefix("a/").removePrefix("b/"),
        additions = bullet.groupValues.getOrNull(2)?.toIntOrNull() ?: 0,
        deletions = bullet.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
    )
}

private fun String.compactDetailTitle(limit: Int = 96): String {
    val singleLine = trim().replace(Regex("\\s+"), " ")
    if (singleLine.length <= limit) return singleLine
    return singleLine.take(limit).trimEnd() + "..."
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
    onOpenDiffReview: () -> Unit = {},
) {
    val isUser = message.role == "user"
    val isPlainAssistant = !isUser && message.type != "plan" && !message.isDetailMessage()
    val isDetail = message.isDetailMessage()
    if (isDetail) {
        DetailMessageCard(message, onOpenDiffReview)
        return
    }
    val container = when {
        isUser -> MaterialTheme.colorScheme.surfaceContainerHigh
        message.type == "thinking" -> MaterialTheme.colorScheme.surfaceContainer
        message.type == "plan" -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(
                when {
                    isUser -> metrics.userBubbleWidth
                    else -> metrics.assistantBubbleWidth
                },
            ),
            color = container,
            border = if (isUser || message.type == "plan") {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
            } else {
                null
            },
            shape = RoundedCornerShape(if (isPlainAssistant) 12.dp else metrics.bubbleShape),
        ) {
            Column(Modifier.padding(metrics.bubblePadding)) {
                when {
                    message.type == "plan" -> PlanMessageCard(message, onOpenPlan)
                    else -> MarkdownMessageContent(message.text.ifBlank { "..." }, previewLongContent = true)
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
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
            Text("查看、优化或开始计划")
        }
    }
}

@Composable
private fun DetailMessageCard(message: AgentMessage, onOpenDiffReview: () -> Unit = {}) {
    val strings = LocalAppStrings.current
    var expanded by remember(message.stableKey()) { mutableStateOf(false) }
    var textExpanded by remember(message.stableKey()) { mutableStateOf(false) }
    val detail = remember(message.text, message.type) { message.detailDisplay() }
    val body = detail.body.ifBlank { "..." }
    val bodyIsLong = body.length > LONG_DETAIL_TEXT_LIMIT
    val visibleBody = if (bodyIsLong && !textExpanded) body.take(LONG_DETAIL_TEXT_LIMIT) else body
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    if (message.type == "file_change") {
        FileChangeCard(
            detail = detail,
            expanded = expanded,
            textExpanded = textExpanded,
            body = body,
            visibleBody = visibleBody,
            bodyIsLong = bodyIsLong,
            onToggleExpanded = { expanded = !expanded },
            onToggleTextExpanded = { textExpanded = !textExpanded },
            onCopyText = { copyText(detail.body.ifBlank { message.text }) },
            onOpenDiffReview = onOpenDiffReview,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                detail.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.subtitle.isNotBlank()) {
                Text(
                    detail.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起细节" else "展开细节",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)),
        ) {
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { copyText(detail.body.ifBlank { message.text }) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(if (bodyIsLong) strings.copyFullText else strings.copyContent) },
                    )
                    if (message.type == "file_change") {
                        AssistChip(
                            onClick = onOpenDiffReview,
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(strings.viewFullDiff) },
                        )
                        detail.files.take(3).forEach { path ->
                            AssistChip(
                                onClick = { copyText(path) },
                                label = { Text(path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        visibleBody,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (bodyIsLong) {
                    OutlinedButton(
                        onClick = { textExpanded = !textExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (textExpanded) strings.collapse else strings.expandMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeCard(
    detail: DetailDisplay,
    expanded: Boolean,
    textExpanded: Boolean,
    body: String,
    visibleBody: String,
    bodyIsLong: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleTextExpanded: () -> Unit,
    onCopyText: () -> Unit,
    onOpenDiffReview: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${detail.files.size.coerceAtLeast(1)} 个文件已更改",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FileChangeStatText(detail.additions, detail.deletions)
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起细节" else "展开细节",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            val entries = detail.fileEntries.ifEmpty { detail.files.map { FileChangeEntry(it) } }
            entries.take(8).forEach { entry ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                FileChangeRow(entry)
            }
            if (entries.size > 8) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                Text(
                    "另有 ${entries.size - 8} 个文件",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = onOpenDiffReview,
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(strings.viewFullDiff) },
                        )
                        AssistChip(
                            onClick = onCopyText,
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(if (bodyIsLong) strings.copyFullText else strings.copyContent) },
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            visibleBody,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (bodyIsLong) {
                        OutlinedButton(
                            onClick = onToggleTextExpanded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (textExpanded) strings.collapse else strings.expandMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeRow(entry: FileChangeEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            entry.path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FileChangeStatText(entry.additions, entry.deletions)
    }
}

@Composable
private fun FileChangeStatText(additions: Int, deletions: Int) {
    if (additions <= 0 && deletions <= 0) return
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF159447))) {
                append("+$additions")
            }
            append(" ")
            withStyle(SpanStyle(color = Color(0xFFE11D2E))) {
                append("-$deletions")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
fun MarkdownMessageContent(text: String, previewLongContent: Boolean = false) {
    var expanded by remember(text) { mutableStateOf(false) }
    val isLong = previewLongContent && textNeedsPreview(text)
    val visibleText = remember(text, expanded, previewLongContent) {
        if (isLong && !expanded) messagePreviewText(text) else text
    }
    val blocks = remember(visibleText) { markdownBlocks(visibleText) }
    Column(
        modifier = Modifier.animateContentSize(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            if (block.isCode) {
                MarkdownCodeBlock(block)
            } else {
                block.text.lines().forEach { line ->
                    MarkdownTextLine(line)
                }
            }
        }
        if (isLong) {
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (expanded) LocalAppStrings.current.collapse else LocalAppStrings.current.expandMore)
            }
        }
    }
}

private fun textNeedsPreview(text: String): Boolean {
    return text.length > LONG_MESSAGE_TEXT_LIMIT || text.lineSequence().take(MESSAGE_PREVIEW_LINE_LIMIT + 1).count() > MESSAGE_PREVIEW_LINE_LIMIT
}

private fun messagePreviewText(text: String): String {
    val lineLimited = text.lineSequence()
        .take(MESSAGE_PREVIEW_LINE_LIMIT)
        .joinToString("\n")
    val charLimited = if (lineLimited.length > MESSAGE_PREVIEW_TEXT_LIMIT) {
        lineLimited.take(MESSAGE_PREVIEW_TEXT_LIMIT).trimEnd()
    } else {
        lineLimited.trimEnd()
    }
    return "$charLimited\n..."
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock) {
    val strings = LocalAppStrings.current
    var expanded by remember(block.text) { mutableStateOf(false) }
    val isLong = block.text.length > LONG_CODE_TEXT_LIMIT
    val visibleText = if (isLong && !expanded) block.text.take(LONG_CODE_TEXT_LIMIT) else block.text
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.language ?: "code",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { copyText(block.text) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制代码", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                visibleText.ifBlank { "..." },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (isLong) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (expanded) strings.collapse else strings.expandMore)
                }
            }
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
            else -> MaterialTheme.typography.bodyLarge
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

