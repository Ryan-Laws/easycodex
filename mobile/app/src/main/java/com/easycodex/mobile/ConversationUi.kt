package com.easycodex.mobile

import android.content.ClipData
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder

private data class ConversationLayoutMetrics(
    val listPadding: PaddingValues,
    val itemSpacing: androidx.compose.ui.unit.Dp,
    val bubblePadding: androidx.compose.ui.unit.Dp,
    val bubbleShape: androidx.compose.ui.unit.Dp,
    val userBubbleWidth: Float,
    val assistantBubbleWidth: Float,
    val detailBubbleWidth: Float,
)

private const val LONG_DETAIL_TEXT_LIMIT = 6_000
private const val LONG_MESSAGE_TEXT_LIMIT = 1_600
private const val MESSAGE_PREVIEW_TEXT_LIMIT = 900
private const val MESSAGE_PREVIEW_LINE_LIMIT = 14

private fun AttachmentDraft.isPreviewImage(): Boolean {
    return !previewUri.isNullOrBlank() && mimeType?.startsWith("image/") == true
}

private suspend fun LazyListState.scrollToBottomAnchor(bottomIndex: Int) {
    scrollToItem(bottomIndex)
    withFrameNanos { }
    if (canScrollForward) scrollToItem(bottomIndex)
}

private fun conversationLayoutMetrics(layoutMode: String): ConversationLayoutMetrics {
    return when (layoutMode) {
        "compact" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            itemSpacing = 6.dp,
            bubblePadding = 10.dp,
            bubbleShape = 14.dp,
            userBubbleWidth = 0.86f,
            assistantBubbleWidth = 0.98f,
            detailBubbleWidth = 0.98f,
        )

        "spacious" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            itemSpacing = 14.dp,
            bubblePadding = 16.dp,
            bubbleShape = 24.dp,
            userBubbleWidth = 0.78f,
            assistantBubbleWidth = 0.96f,
            detailBubbleWidth = 0.92f,
        )

        else -> ConversationLayoutMetrics(
            listPadding = PaddingValues(16.dp),
            itemSpacing = 10.dp,
            bubblePadding = 14.dp,
            bubbleShape = 20.dp,
            userBubbleWidth = 0.82f,
            assistantBubbleWidth = 0.98f,
            detailBubbleWidth = 0.96f,
        )
    }
}

@Composable
fun Conversation(
    agent: Agent?,
    layoutMode: String = DEFAULT_APP_LAYOUT,
    emptyMessage: String = "创建或选择一个智能体开始。",
    relayUrl: String = "",
    apiKey: String = "",
    onOpenDiffReview: () -> Unit = {},
    onOpenPlan: (AgentMessage) -> Unit = {},
) {
    val metrics = conversationLayoutMetrics(layoutMode)
    val strings = LocalAppStrings.current
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var initializedAtBottom by remember(agent?.id) { mutableStateOf(false) }
    var followOutput by remember(agent?.id) { mutableStateOf(true) }

    if (agent == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val visibleMessages by remember(agent.messages) {
        derivedStateOf {
            agent.messages.filter { it.isPrimaryConversationVisible() }
        }
    }
    val conversationItems = remember(visibleMessages) { visibleMessages.toConversationListItems() }
    val visibleMessageKeys = remember(conversationItems) { conversationItems.uniqueLazyKeys() }
    val bottomIndex = conversationItems.size
    val bottomAnchorCount = conversationItems.size + 1
    val lastMessage = visibleMessages.lastOrNull()
    val outputRevision = "${visibleMessages.size}:${lastMessage?.stableKey().orEmpty()}:${lastMessage?.text?.length ?: 0}:${lastMessage?.streaming == true}"
    val isAtBottom by remember(listState, bottomAnchorCount) {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    val userScrollConnection = remember(agent.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) followOutput = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(agent.id, bottomAnchorCount) {
        if (visibleMessages.isNotEmpty() && !initializedAtBottom) {
            listState.scrollToBottomAnchor(bottomIndex)
            initializedAtBottom = true
        }
    }
    LaunchedEffect(agent.id) {
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { atBottom ->
                if (atBottom) followOutput = true
            }
    }
    LaunchedEffect(agent.id, outputRevision, followOutput) {
        if (visibleMessages.isNotEmpty() && followOutput) listState.scrollToBottomAnchor(bottomIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(userScrollConnection),
                verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
                contentPadding = metrics.listPadding,
            ) {
                itemsIndexed(
                    conversationItems,
                    key = { index, item -> visibleMessageKeys.getOrNull(index) ?: "${item.stableKey()}#$index" },
                ) { _, item ->
                    when (item) {
                        is ConversationListItem.Message -> MessageBubble(
                            message = item.message,
                            metrics = metrics,
                            relayUrl = relayUrl,
                            apiKey = apiKey,
                            onOpenPlan = { onOpenPlan(item.message) },
                            onOpenDiffReview = onOpenDiffReview,
                        )

                        is ConversationListItem.DetailGroup -> DetailGroupBubble(
                            messages = item.messages,
                            kind = item.kind,
                            metrics = metrics,
                            onOpenDiffReview = onOpenDiffReview,
                        )
                    }
                }
                item(key = "conversation-bottom-anchor") {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = visibleMessages.isNotEmpty() && !isAtBottom,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    followOutput = true
                    scrollScope.launch {
                        listState.scrollToBottomAnchor(bottomIndex)
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

private sealed class ConversationListItem {
    data class Message(val message: AgentMessage) : ConversationListItem()
    data class DetailGroup(val messages: List<AgentMessage>, val kind: DetailGroupKind) : ConversationListItem()

    fun stableKey(): String {
        return when (this) {
            is Message -> message.stableKey()
            is DetailGroup -> "${kind.name.lowercase()}_group_${messages.first().stableKey()}_${messages.last().stableKey()}_${messages.size}"
        }
    }
}

private enum class DetailGroupKind {
    Command,
    FileChange,
    Mixed,
}

private fun List<ConversationListItem>.uniqueLazyKeys(): List<String> {
    val totals = groupingBy { it.stableKey() }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return map { item ->
        val baseKey = item.stableKey()
        if ((totals[baseKey] ?: 0) <= 1) {
            baseKey
        } else {
            val occurrence = seen.getOrDefault(baseKey, 0)
            seen[baseKey] = occurrence + 1
            "$baseKey#$occurrence"
        }
    }
}

private fun List<AgentMessage>.toConversationListItems(): List<ConversationListItem> {
    val items = mutableListOf<ConversationListItem>()
    var index = 0
    while (index < size) {
        val message = this[index]
        if (!message.isDetailGroupCandidate()) {
            items.add(ConversationListItem.Message(message))
            index += 1
            continue
        }

        val group = mutableListOf<AgentMessage>()
        while (index < size && this[index].isDetailGroupCandidate()) {
            group.add(this[index])
            index += 1
        }
        if (group.size >= 2) {
            items.add(ConversationListItem.DetailGroup(group, group.detailGroupKind()))
        } else {
            items.add(ConversationListItem.Message(group.first()))
        }
    }
    return items
}

private fun AgentMessage.isDetailGroupCandidate(): Boolean {
    return role == "agent" && type in setOf("command", "command_output", "file_change")
}

private fun List<AgentMessage>.detailGroupKind(): DetailGroupKind {
    val hasCommand = any { it.type == "command" || it.type == "command_output" }
    val hasFileChange = any { it.type == "file_change" }
    return when {
        hasCommand && hasFileChange -> DetailGroupKind.Mixed
        hasFileChange -> DetailGroupKind.FileChange
        else -> DetailGroupKind.Command
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

private fun cleanFileChangeBody(raw: String): String {
    val noiseLine = Regex(
        pattern = "^(success\\s+)?(update|updated|modify|modified|edit|edited)\\b.*\\b(following\\s+files?|files?)\\b.*$",
        option = RegexOption.IGNORE_CASE,
    )
    return raw.lineSequence()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.equals("Files:", ignoreCase = true) || noiseLine.matches(trimmed)
        }
        .joinToString("\n")
        .trim()
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
            val existing = entryStats[summaryEntry.path]
            entryStats[summaryEntry.path] = if (existing == null) {
                summaryEntry
            } else {
                existing.copy(
                    additions = existing.additions + summaryEntry.additions,
                    deletions = existing.deletions + summaryEntry.deletions,
                )
            }
            return@forEach
        }
        val inlineStats = parseInlineFileChangeStats(trimmed)
        if (inlineStats != null) {
            additions += inlineStats.first
            deletions += inlineStats.second
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
    val bullet = Regex(
        "^[-•]\\s+(.+?)(?:\\s+\\(?\\+(\\d+)\\s+-(\\d+)\\)?)?$",
    ).find(trimmed)
    val edited = Regex(
        "^(?:已编辑|已修改|修改|edited|modified|updated)\\s+(.+?)(?:\\s+\\(?\\+(\\d+)\\s+-(\\d+)\\)?)?$",
        RegexOption.IGNORE_CASE,
    ).find(trimmed)
    val match = bullet ?: edited ?: return null
    val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
    if (path.isBlank() || (!path.contains("/") && !path.contains("\\") && !path.contains("."))) return null
    return FileChangeEntry(
        path = path.removePrefix("a/").removePrefix("b/"),
        additions = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0,
        deletions = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
    )
}

private fun parseInlineFileChangeStats(trimmed: String): Pair<Int, Int>? {
    val match = Regex("\\+\\s*(\\d+)\\s+-\\s*(\\d+)").find(trimmed) ?: return null
    return (match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0) to
        (match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0)
}

private fun String.compactDetailTitle(limit: Int = 96): String {
    val singleLine = trim().replace(Regex("\\s+"), " ")
    if (singleLine.length <= limit) return singleLine
    return singleLine.take(limit).trimEnd() + "..."
}

@Composable
private fun LightweightDetailHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticView = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) {
                hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggleExpanded()
            }
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
            modifier = Modifier.size(15.dp),
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.72f, fill = false),
            )
        }
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "收起细节" else "展开细节",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = Modifier.size(18.dp),
        )
    }
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
private fun DetailGroupBubble(
    messages: List<AgentMessage>,
    kind: DetailGroupKind,
    metrics: ConversationLayoutMetrics,
    onOpenDiffReview: () -> Unit = {},
) {
    var expanded by remember(messages.firstOrNull()?.stableKey(), messages.lastOrNull()?.stableKey(), messages.size) { mutableStateOf(false) }
    val running = messages.any { it.streaming }
    val commandCount = messages.count { it.type == "command" }.takeIf { it > 0 } ?: messages.size
    val fileCount = remember(messages) {
        messages.sumOf { message ->
            message.detailDisplay().files.size.coerceAtLeast(
                if (message.type == "file_change") 1 else 0,
            )
        }.coerceAtLeast(messages.size)
    }
    val latestTitle = remember(messages) {
        messages.lastOrNull()
            ?.detailDisplay()
            ?.title
            .orEmpty()
    }
    val commandTitle = if (running) "正在运行 $commandCount 条命令" else "已运行 $commandCount 条命令"
    val fileTitle = "$fileCount 个文件已更改"
    val title = when (kind) {
        DetailGroupKind.Command -> commandTitle
        DetailGroupKind.FileChange -> fileTitle
        DetailGroupKind.Mixed -> "$commandTitle · $fileTitle"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.fillMaxWidth(metrics.detailBubbleWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LightweightDetailHeader(
                    title = title,
                    subtitle = latestTitle,
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
                    exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 4.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        messages.forEachIndexed { index, message ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
                            DetailMessageCard(message, onOpenDiffReview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: AgentMessage,
    metrics: ConversationLayoutMetrics = conversationLayoutMetrics(DEFAULT_APP_LAYOUT),
    relayUrl: String = "",
    apiKey: String = "",
    onOpenPlan: () -> Unit = {},
    onOpenDiffReview: () -> Unit = {},
) {
    val isUser = message.role == "user" || message.type == "user"
    val isPlainAssistant = !isUser && message.type != "plan" && !message.isDetailMessage()
    val isDetail = message.isDetailMessage()
    if (isDetail) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(Modifier.fillMaxWidth(metrics.detailBubbleWidth)) {
                DetailMessageCard(message, onOpenDiffReview)
            }
        }
        return
    }
    val container = when {
        isUser -> MaterialTheme.colorScheme.surfaceContainerHighest
        message.type == "thinking" -> MaterialTheme.colorScheme.surfaceContainer
        message.type == "plan" -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    val contentPadding = when {
        isPlainAssistant -> PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        isUser -> PaddingValues(horizontal = metrics.bubblePadding, vertical = metrics.bubblePadding)
        else -> PaddingValues(metrics.bubblePadding)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val clipboard = LocalClipboard.current
        val clipboardScope = rememberCoroutineScope()
        val strings = LocalAppStrings.current
        var copyMenuExpanded by remember(message.stableKey(), message.text) { mutableStateOf(false) }
        val bubbleWidth = when {
            isUser -> metrics.userBubbleWidth
            else -> metrics.assistantBubbleWidth
        }
        Box(Modifier.fillMaxWidth(bubbleWidth)) {
            Surface(
                modifier = Modifier
                    .then(
                        when {
                            isUser -> Modifier.align(Alignment.CenterEnd)
                            else -> Modifier.fillMaxWidth()
                        },
                    )
                    .then(
                        if (message.text.isBlank()) {
                            Modifier
                        } else {
                            Modifier.pointerInput(message.text) {
                                detectTapGestures(
                                    onLongPress = { copyMenuExpanded = true },
                                )
                            }
                        },
                    ),
                color = container,
                border = if (isUser || message.type == "plan") {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isUser) 0.28f else 0.72f))
                } else {
                    null
                },
                shape = RoundedCornerShape(if (isPlainAssistant) 12.dp else metrics.bubbleShape),
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.attachments.any { it.isPreviewImage() }) {
                        AttachmentPreviewRow(message.attachments)
                    }
                    when {
                        message.type == "plan" -> PlanMessageCard(message, onOpenPlan)
                        message.type == "thinking" && message.streaming -> ThinkingMessageContent(message.text)
                        else -> MarkdownMessageContent(
                            text = message.text.ifBlank { "..." },
                            previewLongContent = isPlainAssistant,
                            relayUrl = relayUrl,
                            apiKey = apiKey,
                            modifier = if (isUser) Modifier else Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = copyMenuExpanded,
                onDismissRequest = { copyMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.copyContent) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        copyMenuExpanded = false
                        clipboardScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", message.text)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ThinkingMessageContent(text: String) {
    val label = text.trim().trimEnd('。', '.', '…').ifBlank { "思考中" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun AttachmentPreviewRow(attachments: List<AttachmentDraft>) {
    val images = attachments.filter { it.isPreviewImage() }
    if (images.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.forEach { attachment ->
            AttachmentImagePreview(attachment, wide = images.size == 1)
        }
    }
}

@Composable
private fun AttachmentImagePreview(attachment: AttachmentDraft, wide: Boolean) {
    val context = LocalContext.current
    var image by remember(attachment.previewUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(attachment.previewUri) {
        image = attachment.previewUri
            ?.let { uri -> runCatching { Uri.parse(uri) }.getOrNull() }
            ?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = if (wide) {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp, max = 220.dp)
        } else {
            Modifier
                .width(148.dp)
                .height(112.dp)
        },
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val body = remember(detail.body, message.type) {
        if (message.type == "file_change") cleanFileChangeBody(detail.body) else detail.body
    }.ifBlank { "..." }
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
            onToggleExpanded = { expanded = !expanded },
            onCopyText = { copyText(body.ifBlank { message.text }) },
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
        LightweightDetailHeader(
            title = detail.title,
            subtitle = detail.subtitle,
            expanded = expanded,
            onToggleExpanded = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
            exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
    onToggleExpanded: () -> Unit,
    onCopyText: () -> Unit,
    onOpenDiffReview: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val hapticView = LocalView.current
    val entries = detail.fileEntries.ifEmpty { detail.files.map { FileChangeEntry(it) } }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) {
                    hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleExpanded()
                }
                .padding(horizontal = 2.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                "${detail.files.size.coerceAtLeast(1)} 个文件已更改",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileChangeStatText(detail.additions, detail.deletions)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起细节" else "展开细节",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
            exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(
                        onClick = onOpenDiffReview,
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(strings.viewFullDiff) },
                    )
                    AssistChip(
                        onClick = onCopyText,
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(strings.copyContent) },
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        entries.take(4).forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
                            FileChangeRow(entry)
                        }
                        if (entries.size > 4) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
                            Text(
                                "另有 ${entries.size - 4} 个文件",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            entry.path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
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
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
fun MarkdownMessageContent(
    text: String,
    previewLongContent: Boolean = false,
    relayUrl: String = "",
    apiKey: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val isLong = previewLongContent && textNeedsPreview(text)
    val visibleText = remember(text, expanded, previewLongContent) {
        if (isLong && !expanded) messagePreviewText(text) else text
    }
    val blocks = remember(visibleText) { markdownBlocks(visibleText) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            if (block.isCode) {
                MarkdownCodeBlock(block)
            } else {
                block.text.lines().forEach { line ->
                    val image = markdownImage(line)
                    if (image != null) {
                        MarkdownImage(image, relayUrl, apiKey)
                    } else {
                        MarkdownTextLine(line)
                    }
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

private data class MarkdownImageRef(
    val alt: String,
    val source: String,
)

private fun markdownImage(line: String): MarkdownImageRef? {
    val match = Regex("^\\s*!\\[([^\\]]*)]\\(([^)]+)\\)\\s*$").find(line) ?: return null
    val source = match.groupValues[2].trim().trim('"')
    if (source.isBlank()) return null
    return MarkdownImageRef(match.groupValues[1].trim(), source)
}

private fun String.isHttpImageSource(): Boolean {
    return startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}

private fun String.isDataImageSource(): Boolean {
    return startsWith("data:image/", ignoreCase = true)
}

private fun String.isLocalImagePath(): Boolean {
    return startsWith("file://", ignoreCase = true) ||
        Regex("^[A-Za-z]:[\\\\/].+").containsMatchIn(this) ||
        startsWith("\\\\") ||
        startsWith("/")
}

private fun relayHttpBase(relayUrl: String): String {
    return relayUrl.trim()
        .replace(Regex("^ws://", RegexOption.IGNORE_CASE), "http://")
        .replace(Regex("^wss://", RegexOption.IGNORE_CASE), "https://")
        .trimEnd('/')
}

private fun imageLoadSource(source: String, relayUrl: String, apiKey: String): String {
    val clean = source.trim()
    if (!clean.isLocalImagePath() || relayUrl.isBlank() || apiKey.isBlank()) return clean
    val localPath = if (clean.startsWith("file://", ignoreCase = true)) {
        runCatching { Uri.parse(clean).path }.getOrNull().orEmpty().ifBlank { clean.removePrefix("file://") }
    } else {
        clean
    }
    return "${relayHttpBase(relayUrl)}/media/image?key=${URLEncoder.encode(apiKey, "UTF-8")}&path=${URLEncoder.encode(localPath, "UTF-8")}"
}

@Composable
private fun MarkdownImage(image: MarkdownImageRef, relayUrl: String, apiKey: String) {
    var bitmap by remember(image.source, relayUrl, apiKey) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(image.source, relayUrl, apiKey) { mutableStateOf(false) }
    val loadSource = remember(image.source, relayUrl, apiKey) { imageLoadSource(image.source, relayUrl, apiKey) }
    LaunchedEffect(loadSource) {
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    loadSource.isDataImageSource() -> {
                        val encoded = loadSource.substringAfter(',', "")
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    loadSource.isHttpImageSource() -> {
                        URL(loadSource).openStream().use { stream -> BitmapFactory.decodeStream(stream) }
                    }
                    else -> null
                }?.asImageBitmap()
            }.getOrNull()
        }
        failed = bitmap == null
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 360.dp),
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = image.alt.ifBlank { "image" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
            failed -> Text(
                image.source,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Description, contentDescription = image.alt.ifBlank { "image" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    val codeScroll = rememberScrollState()
    val codeBlockShape = RoundedCornerShape(10.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = codeBlockShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(codeBlockShape),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.language ?: "code",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { copyText(block.text) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制代码", modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(codeScroll)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    block.text.ifBlank { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                )
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

