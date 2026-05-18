package com.easycodex.mobile

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image as MarkdownImageNode
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link as MarkdownLinkNode
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
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
private const val STREAMING_REVEAL_FRAME_MS = 24L
private const val MARKDOWN_IMAGE_CONNECT_TIMEOUT_MS = 5_000
private const val MARKDOWN_IMAGE_READ_TIMEOUT_MS = 10_000
private const val MARKDOWN_IMAGE_MAX_BYTES = 16 * 1024 * 1024
private const val ATTACHMENT_PREVIEW_MAX_DIMENSION = 900

private fun AttachmentDraft.isPreviewImage(): Boolean {
    return !previewUri.isNullOrBlank() && mimeType?.startsWith("image/") == true
}

private fun sampledBitmapFromBytes(bytes: ByteArray, maxDimension: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = generateSequence(1) { it * 2 }
        .first { largestDimension / it <= maxDimension || it >= 16 }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private suspend fun LazyListState.scrollToBottomAnchor(bottomIndex: Int, animated: Boolean = false) {
    if (animated) {
        animateScrollToItem(bottomIndex)
    } else {
        scrollToItem(bottomIndex)
    }
    withFrameNanos { }
    if (canScrollForward) {
        if (animated) animateScrollToItem(bottomIndex) else scrollToItem(bottomIndex)
    }
}

private fun conversationLayoutMetrics(layoutMode: String): ConversationLayoutMetrics {
    return when (layoutMode) {
        "compact" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            itemSpacing = 7.dp,
            bubblePadding = 10.dp,
            bubbleShape = 16.dp,
            userBubbleWidth = 0.84f,
            assistantBubbleWidth = 0.98f,
            detailBubbleWidth = 0.98f,
        )

        "spacious" -> ConversationLayoutMetrics(
            listPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            itemSpacing = 12.dp,
            bubblePadding = 16.dp,
            bubbleShape = 22.dp,
            userBubbleWidth = 0.76f,
            assistantBubbleWidth = 0.96f,
            detailBubbleWidth = 0.92f,
        )

        else -> ConversationLayoutMetrics(
            listPadding = PaddingValues(16.dp),
            itemSpacing = 9.dp,
            bubblePadding = 12.dp,
            bubbleShape = 18.dp,
            userBubbleWidth = 0.80f,
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
    onOpenSubAgent: (AgentMessage) -> Unit = {},
    onUndoFileChanges: (List<String>) -> Unit = {},
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
            agent.messages
                .filter { it.isPrimaryConversationVisible() }
                .withStreamingThinkingAtBottom()
        }
    }
    val conversationItems = remember(visibleMessages, agent.status) { visibleMessages.toConversationListItems(agent.isBusy()) }
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
        if (visibleMessages.isNotEmpty() && followOutput) listState.scrollToBottomAnchor(bottomIndex, animated = true)
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
                            onOpenSubAgent = { onOpenSubAgent(item.message) },
                        )

                        is ConversationListItem.DetailGroup -> DetailGroupBubble(
                            messages = item.messages,
                            kind = item.kind,
                            metrics = metrics,
                            relayUrl = relayUrl,
                            apiKey = apiKey,
                            onOpenDiffReview = onOpenDiffReview,
                        )

                        is ConversationListItem.ChangeSummary -> FileChangeSummaryCard(
                            messages = item.messages,
                            metrics = metrics,
                            onOpenDiffReview = onOpenDiffReview,
                            onUndoFileChanges = onUndoFileChanges,
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
    data class ChangeSummary(val messages: List<AgentMessage>) : ConversationListItem()

    fun stableKey(): String {
        return when (this) {
            is Message -> message.stableKey()
            is DetailGroup -> "${kind.name.lowercase()}_group_${messages.first().stableKey()}_${messages.last().stableKey()}_${messages.size}"
            is ChangeSummary -> "change_summary_${messages.first().stableKey()}_${messages.last().stableKey()}_${messages.size}"
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

private fun List<AgentMessage>.toConversationListItems(agentBusy: Boolean): List<ConversationListItem> {
    val items = mutableListOf<ConversationListItem>()
    val fileChangeMessages = filter { it.role == "agent" && it.type == "file_change" }
    var index = 0
    while (index < size) {
        val message = this[index]
        if (message.role == "agent" && message.type == "file_change") {
            index += 1
            continue
        }
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
    if (!agentBusy && fileChangeMessages.isNotEmpty()) {
        items.add(ConversationListItem.ChangeSummary(fileChangeMessages))
    }
    return items
}

private fun AgentMessage.isDetailGroupCandidate(): Boolean {
    return role == "agent" && type in setOf("command", "command_output")
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

internal fun detailGroupDefaultExpanded(messages: List<AgentMessage>): Boolean {
    return false
}

internal fun detailMessageDefaultExpanded(message: AgentMessage): Boolean {
    return false
}

private val MarkdownExtensions: List<Extension> = listOf(
    TablesExtension.create(),
    AutolinkExtension.create(),
    StrikethroughExtension.create(),
    TaskListItemsExtension.create(),
)

private val MarkdownParser: Parser = Parser.builder()
    .extensions(MarkdownExtensions)
    .build()

internal fun parseMarkdownForMobile(text: String): Node {
    return MarkdownParser.parse(text.ifBlank { "..." })
}

private data class MarkdownBlock(
    val text: String,
    val language: String? = null,
    val isCode: Boolean = false,
)

internal data class MarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

private data class MarkdownInlineStyles(
    val code: SpanStyle,
    val link: SpanStyle,
)

private data class MarkdownTableCellModel(
    val source: Node,
    val plainText: String,
    val header: Boolean,
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
    val artifact: StructuredArtifactDisplay? = null,
)

internal enum class StructuredArtifactStatus {
    Passed,
    Failed,
    Running,
    Unknown,
}

internal data class StructuredArtifactDisplay(
    val type: String,
    val label: String,
    val title: String,
    val status: String = "",
    val statusKind: StructuredArtifactStatus = StructuredArtifactStatus.Unknown,
    val source: String = "",
    val summary: String = "",
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

internal data class FileTypeBadgeSpec(
    val label: String,
    val background: Long,
    val foreground: Long = 0xFFFFFFFF,
    val generic: Boolean = false,
)

internal data class MarkdownFileReference(
    val path: String,
)

private val FileTypeBadgeSpecs = mapOf(
    "ts" to FileTypeBadgeSpec("TS", 0xFF3178C6),
    "tsx" to FileTypeBadgeSpec("TSX", 0xFF3178C6),
    "js" to FileTypeBadgeSpec("JS", 0xFFF0DB4F, foreground = 0xFF1F2328),
    "jsx" to FileTypeBadgeSpec("JSX", 0xFFF0DB4F, foreground = 0xFF1F2328),
    "kt" to FileTypeBadgeSpec("KT", 0xFF7F52FF),
    "kts" to FileTypeBadgeSpec("KTS", 0xFF7F52FF),
    "json" to FileTypeBadgeSpec("JSON", 0xFF6B7280),
    "md" to FileTypeBadgeSpec("MD", 0xFF2563EB),
    "css" to FileTypeBadgeSpec("CSS", 0xFF264DE4),
    "scss" to FileTypeBadgeSpec("SCSS", 0xFFCD6799),
    "html" to FileTypeBadgeSpec("HTML", 0xFFE34F26),
    "xml" to FileTypeBadgeSpec("XML", 0xFFF97316),
    "yml" to FileTypeBadgeSpec("YML", 0xFFCB171E),
    "yaml" to FileTypeBadgeSpec("YML", 0xFFCB171E),
    "txt" to FileTypeBadgeSpec("TXT", 0xFF64748B),
    "java" to FileTypeBadgeSpec("JAVA", 0xFFB07219),
    "py" to FileTypeBadgeSpec("PY", 0xFF3776AB),
    "go" to FileTypeBadgeSpec("GO", 0xFF00ADD8, foreground = 0xFF042F3B),
    "rs" to FileTypeBadgeSpec("RS", 0xFFB7410E),
    "swift" to FileTypeBadgeSpec("SW", 0xFFF05138),
    "php" to FileTypeBadgeSpec("PHP", 0xFF777BB4),
    "rb" to FileTypeBadgeSpec("RB", 0xFFCC342D),
    "sh" to FileTypeBadgeSpec("SH", 0xFF16A34A),
    "ps1" to FileTypeBadgeSpec("PS", 0xFF2563EB),
    "sql" to FileTypeBadgeSpec("SQL", 0xFF0284C7),
    "gradle" to FileTypeBadgeSpec("GR", 0xFF02303A),
)

private val GenericFileTypeBadgeSpec = FileTypeBadgeSpec("FILE", 0xFF94A3B8, generic = true)

internal fun fileTypeBadgeSpec(path: String): FileTypeBadgeSpec {
    val name = path.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('\\')
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return FileTypeBadgeSpecs[extension] ?: GenericFileTypeBadgeSpec
}

private fun fileReferenceCandidateFromText(text: String): String? {
    val trimmed = text.trimStart()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return null
    val candidate = Regex("""^`?([A-Za-z0-9_.@%+\-~/\\(){}\[\] ]+\.[A-Za-z0-9]{1,10})`?(?:\s*(?:\(|:|,|,|$))""")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trimEnd('.', ',', ':', ';')
        .orEmpty()
    if (candidate.isBlank()) return null
    if (candidate.contains("://")) return null
    return candidate
}

private fun textLooksLikeFileReference(text: String): Boolean {
    return fileReferenceCandidateFromText(text) != null
}

internal fun markdownLeadingFileReference(item: ListItem): MarkdownFileReference? {
    if (item.findFirstChild<TaskListItemMarker>() != null) return null
    val paragraph = item.children().firstOrNull { it is Paragraph } as? Paragraph ?: return null
    var child = paragraph.firstChild
    while (child is TaskListItemMarker || child is SoftLineBreak || child is HardLineBreak) {
        child = child.next
    }
    val path = when (child) {
        is MarkdownLinkNode -> nodePlainText(child).takeIf(::textLooksLikeFileReference)
        is Code -> child.literal.orEmpty().takeIf(::textLooksLikeFileReference)
        is MarkdownTextNode -> fileReferenceCandidateFromText(child.literal.orEmpty())
        else -> fileReferenceCandidateFromText(nodePlainText(paragraph))
    }
    return path?.let { MarkdownFileReference(it) }
}

@Composable
internal fun FileTypeIcon(path: String, modifier: Modifier = Modifier) {
    val spec = fileTypeBadgeSpec(path)
    val accent = Color(spec.background)
    if (spec.generic) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = modifier.size(20.dp),
        )
        return
    }
    Box(
        modifier = modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.58f)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box {
                Surface(
                    color = accent.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomStart = 4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp),
                ) {}
                Surface(
                    color = accent,
                    shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                ) {}
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 1.dp, bottom = 3.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                spec.label,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = when {
                    spec.label.length > 3 -> 6.sp
                    spec.label.length > 2 -> 7.sp
                    else -> 8.sp
                },
                maxLines = 1,
            )
        }
    }
}

private fun AgentMessage.isDetailMessage(): Boolean {
    return type == "command" ||
        type == "command_output" ||
        type == "file_change" ||
        type == "screenshot" ||
        type == "test_result" ||
        type == "plugin_activity" ||
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
    if (type == "thinking") return streaming
    if (text.trim() == "已加载项目上下文。") return false
    if (isInternalStatusMessage()) return false
    return true
}

private fun List<AgentMessage>.withStreamingThinkingAtBottom(): List<AgentMessage> {
    val activeThinking = lastOrNull { it.type == "thinking" && it.streaming } ?: return this
    val rest = filterNot { it.type == "thinking" }
    return rest + activeThinking
}

private fun messageTypeLabel(type: String, strings: AppStrings): String {
    return when (type) {
        "command" -> strings.commandLabel
        "command_output" -> strings.commandOutputLabel
        "file_change" -> strings.fileChangeLabel
        "screenshot" -> "截图"
        "test_result" -> "测试结果"
        "plugin_activity" -> "插件/技能"
        "sub_agent" -> strings.subAgentLabel
        "plan" -> strings.planLabel
        "thinking" -> strings.thinkingLabel
        "status" -> strings.statusLabel
        else -> type.replace('_', ' ')
    }
}

private fun AgentMessage.detailDisplay(strings: AppStrings): DetailDisplay {
    val bodyText = detailText.ifBlank { text }
    return when (type) {
        "file_change" -> fileChangeDisplay(bodyText, strings)
        "sub_agent" -> commandDisplay(bodyText, isOutput = true, strings = strings).copy(label = strings.subAgentLabel)
        "screenshot", "test_result", "plugin_activity" -> artifactDisplay(type, bodyText)
        "command" -> commandDisplay(bodyText, isOutput = false, strings = strings)
        "command_output" -> commandDisplay(bodyText, isOutput = true, strings = strings)
        else -> DetailDisplay(messageTypeLabel(type, strings), text.lineSequence().firstOrNull { it.isNotBlank() } ?: messageTypeLabel(type, strings), "", bodyText)
    }
}

private fun commandDisplay(raw: String, isOutput: Boolean, strings: AppStrings): DetailDisplay {
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
        isOutput && duration.isNotBlank() -> strings.commandProcessedWithDuration(duration)
        isOutput -> status.ifBlank { strings.commandProcessed }
        command.isNotBlank() -> strings.commandRan(command)
        else -> strings.commandDefaultTitle
    }.compactDetailTitle()
    val subtitleParts = if (isOutput) {
        listOf(command, exit.takeIf { it.isNotBlank() }?.let { "exit $it" }.orEmpty(), status)
    } else {
        listOf(status.ifBlank { strings.commandStarted }, duration)
    }
    val subtitle = subtitleParts.filter { it.isNotBlank() }.joinToString(" · ")
    return DetailDisplay(if (isOutput) strings.commandOutputLabel else strings.commandLabel, title, subtitle, raw)
}

private fun artifactDisplay(type: String, raw: String): DetailDisplay {
    val artifact = parseStructuredArtifactDisplay(type, raw)
    val subtitle = listOf(artifact.status, artifact.source, artifact.summary)
        .filter { it.isNotBlank() && it != artifact.title }
        .joinToString(" · ")
    return DetailDisplay(
        label = artifact.label,
        title = artifact.title.compactDetailTitle(),
        subtitle = subtitle.compactDetailTitle(72),
        body = raw,
        artifact = artifact,
    )
}

internal fun parseStructuredArtifactDisplay(type: String, raw: String): StructuredArtifactDisplay {
    val label = structuredArtifactLabel(type)
    var title = ""
    var status = ""
    var source = ""
    var summary = ""
    var markdownAlt = ""
    raw.lineSequence().take(80).forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        val image = markdownImage(trimmed)
        if (image != null) {
            markdownAlt = image.alt
            if (source.isBlank()) source = image.source
            if (title.isBlank()) title = image.alt
            return@forEach
        }
        val key = trimmed.substringBefore(':', "").trim().lowercase().replace("-", "_")
        val value = trimmed.substringAfter(':', "").trim()
        when {
            key in setOf("title", "name") && title.isBlank() -> title = value
            key == "screenshot" && title.isBlank() -> title = value
            key == "command" && title.isBlank() -> title = value
            key in setOf("tool", "plugin", "skill") && title.isBlank() -> title = value
            key in setOf("source", "path", "file", "url") && source.isBlank() -> source = value
            key == "status" && status.isBlank() -> status = value
            ':' !in trimmed && summary.isBlank() -> summary = trimmed
        }
    }
    if (title.isBlank()) {
        title = when {
            markdownAlt.isNotBlank() -> markdownAlt
            source.isNotBlank() -> source.substringAfterLast('/').substringAfterLast('\\')
            summary.isNotBlank() -> summary
            else -> label
        }
    }
    return StructuredArtifactDisplay(
        type = type,
        label = label,
        title = title.ifBlank { label },
        status = status,
        statusKind = structuredArtifactStatusKind(status),
        source = source,
        summary = summary,
    )
}

private fun structuredArtifactLabel(type: String): String {
    return when (type) {
        "screenshot" -> "截图"
        "test_result" -> "测试结果"
        "plugin_activity" -> "插件/技能"
        else -> type.replace('_', ' ')
    }
}

internal fun structuredArtifactStatusKind(status: String): StructuredArtifactStatus {
    val normalized = status.trim().lowercase()
    return when {
        normalized in setOf("pass", "passed", "success", "succeeded", "ok", "green") ||
            normalized.contains("passed") ||
            normalized.contains("success") ||
            normalized.contains("通过") ||
            normalized.contains("成功") -> StructuredArtifactStatus.Passed

        normalized in setOf("fail", "failed", "failure", "error", "errored", "red") ||
            normalized.contains("failed") ||
            normalized.contains("failure") ||
            normalized.contains("error") ||
            normalized.contains("失败") ||
            normalized.contains("错误") -> StructuredArtifactStatus.Failed

        normalized in setOf("running", "started", "pending", "in_progress", "working") ||
            normalized.contains("running") ||
            normalized.contains("progress") ||
            normalized.contains("运行") ||
            normalized.contains("进行中") -> StructuredArtifactStatus.Running

        else -> StructuredArtifactStatus.Unknown
    }
}

private fun fileChangeDisplay(raw: String, strings: AppStrings): DetailDisplay {
    val stats = fileChangeStats(raw)
    val paths = stats.files
    val status = raw.lineSequence()
        .firstOrNull { it.startsWith("status:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        .orEmpty()
    val title = when {
        paths.size == 1 -> paths.first().substringAfterLast('\\').substringAfterLast('/')
        paths.size > 1 -> strings.detailGroupFilesChanged(paths.size)
        else -> strings.fileChangeLabel
    }.compactDetailTitle()
    val subtitle = listOf(
        status.ifBlank { strings.processed },
        if (stats.additions + stats.deletions > 0) "+${stats.additions} -${stats.deletions}" else "",
        paths.firstOrNull().orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return DetailDisplay(strings.fileChangeLabel, title, subtitle, raw, stats.additions, stats.deletions, paths, stats.entries)
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

private fun summarizeFileChangeMessages(messages: List<AgentMessage>): FileChangeStats {
    val entryStats = linkedMapOf<String, FileChangeEntry>()
    var additions = 0
    var deletions = 0
    messages.forEach { message ->
        val stats = fileChangeStats(message.detailText.ifBlank { message.text })
        additions += stats.additions
        deletions += stats.deletions
        stats.entries.forEach { entry ->
            val existing = entryStats[entry.path]
            entryStats[entry.path] = if (existing == null) {
                entry
            } else {
                existing.copy(
                    additions = existing.additions + entry.additions,
                    deletions = existing.deletions + entry.deletions,
                )
            }
        }
    }
    val entries = entryStats.values.toList()
    val summaryAdditions = entries.sumOf { it.additions }
    val summaryDeletions = entries.sumOf { it.deletions }
    return FileChangeStats(
        files = entries.map { it.path },
        additions = if (summaryAdditions > 0 || summaryDeletions > 0) summaryAdditions else additions,
        deletions = if (summaryAdditions > 0 || summaryDeletions > 0) summaryDeletions else deletions,
        entries = entries,
    )
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
    icon: ImageVector = Icons.Default.Description,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hapticClickable(role = Role.Button, onClick = onToggleExpanded)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
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
            contentDescription = if (expanded) strings.detailsCollapse else strings.detailsExpand,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatDurationToken(raw: String): String {
    val value = raw.trim()
    val millis = value.removeSuffix("ms").toLongOrNull() ?: return value
    return formatDurationMillis(millis)
}

private fun formatDurationMillis(millis: Long): String {
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
    relayUrl: String = "",
    apiKey: String = "",
    onOpenDiffReview: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    var expanded by remember(messages.firstOrNull()?.stableKey(), messages.lastOrNull()?.stableKey(), messages.size) {
        mutableStateOf(detailGroupDefaultExpanded(messages))
    }
    val running = messages.any { it.streaming }
    val commandCount = messages.count { it.type == "command" }.takeIf { it > 0 } ?: messages.size
    val fileCount = remember(messages, strings) {
        messages.sumOf { message ->
            message.detailDisplay(strings).files.size.coerceAtLeast(
                if (message.type == "file_change") 1 else 0,
            )
        }.coerceAtLeast(messages.size)
    }
    val latestTitle = remember(messages, strings) {
        messages.lastOrNull()
            ?.detailDisplay(strings)
            ?.title
            .orEmpty()
    }
    val commandTitle = strings.detailGroupCommands(running, commandCount)
    val fileTitle = strings.detailGroupFilesChanged(fileCount)
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
                    icon = when (kind) {
                        DetailGroupKind.Command -> Icons.Default.Terminal
                        DetailGroupKind.FileChange -> Icons.Default.Description
                        DetailGroupKind.Mixed -> Icons.Default.TaskAlt
                    }
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
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                            DetailMessageCard(message, relayUrl = relayUrl, apiKey = apiKey, onOpenDiffReview = onOpenDiffReview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeSummaryCard(
    messages: List<AgentMessage>,
    metrics: ConversationLayoutMetrics,
    onOpenDiffReview: () -> Unit,
    onUndoFileChanges: (List<String>) -> Unit,
) {
    val strings = LocalAppStrings.current
    var expanded by remember(messages.firstOrNull()?.stableKey(), messages.lastOrNull()?.stableKey(), messages.size) { mutableStateOf(false) }
    val summary = remember(messages) { summarizeFileChangeMessages(messages) }
    val entries = summary.entries.ifEmpty { summary.files.map { FileChangeEntry(it) } }
    val files = summary.files
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
            modifier = Modifier.fillMaxWidth(metrics.detailBubbleWidth),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EasyCodexIconBubble(
                        icon = Icons.Default.Description,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            strings.filesChangedCount(files.size.coerceAtLeast(1)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            files.firstOrNull().orEmpty().ifBlank { strings.fileChangeLabel },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FileChangeStatText(summary.additions, summary.deletions)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = rememberHapticClick { expanded = !expanded },
                        leadingIcon = {
                            Icon(
                                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text(if (expanded) strings.detailsCollapse else "打开详细") },
                    )
                    AssistChip(
                        onClick = rememberHapticClick { onUndoFileChanges(files) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("撤销") },
                    )
                    AssistChip(
                        onClick = rememberHapticClick(onOpenDiffReview),
                        leadingIcon = { Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(strings.reviewChanges) },
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = easyCodexExpandVertically(expandFrom = Alignment.Top),
                    exit = easyCodexShrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entries.take(8).forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
                            FileChangeRow(entry)
                        }
                        if (entries.size > 8) {
                            Text(
                                "+${entries.size - 8}",
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
private fun SubAgentActivityRow(
    message: AgentMessage,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickable = message.subAgentThreadId.isNotBlank()
    val title = remember(message.text, message.subAgentStatus) {
        val normalized = message.subAgentStatus.trim().lowercase()
        when {
            normalized in setOf("failed", "errored") -> "子代理失败"
            normalized in setOf("inprogress", "running", "pendinginit") -> "子代理正在工作"
            message.text.isNotBlank() -> message.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "子代理已完成"
            else -> "子代理已完成"
        }
    }
    val subtitle = remember(message.subAgentNickname, message.subAgentRole, message.subAgentThreadId) {
        listOf(message.subAgentNickname, message.subAgentRole, message.subAgentThreadId.takeLast(8))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .hapticClickable(enabled = clickable, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 4.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
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
                modifier = Modifier.weight(0.8f, fill = false),
            )
        }
        if (clickable) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp),
            )
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
    onOpenSubAgent: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val isUser = message.role == "user" || message.type == "user"
    val isPlainAssistant = !isUser && message.type != "plan" && !message.isDetailMessage()
    val isDetail = message.isDetailMessage()
    val totalDurationLabel = remember(message.durationMs, strings) {
        message.durationMs
            ?.takeIf { it > 0L && isPlainAssistant }
            ?.let { strings.taskProcessedWithDuration(formatDurationMillis(it)) }
            .orEmpty()
    }
    if (message.type == "thinking" && message.streaming) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            ThinkingMessageContent(
                text = message.text,
                modifier = Modifier.fillMaxWidth(metrics.assistantBubbleWidth),
            )
        }
        return
    }
    if (message.type == "sub_agent") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            SubAgentActivityRow(
                message = message,
                onOpen = onOpenSubAgent,
                modifier = Modifier.fillMaxWidth(metrics.detailBubbleWidth),
            )
        }
        return
    }
    if (isDetail) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(Modifier.fillMaxWidth(metrics.detailBubbleWidth)) {
                DetailMessageCard(message, relayUrl = relayUrl, apiKey = apiKey, onOpenDiffReview = onOpenDiffReview)
            }
        }
        return
    }
    val container = when {
        isUser -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        message.type == "plan" -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        isPlainAssistant -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val contentPadding = when {
        isPlainAssistant -> PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        isUser -> PaddingValues(horizontal = metrics.bubblePadding, vertical = metrics.bubblePadding)
        else -> PaddingValues(metrics.bubblePadding)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val clipboard = LocalClipboard.current
        val clipboardScope = rememberCoroutineScope()
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
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isUser) 0.18f else 0.42f))
                } else {
                    null
                },
                shape = RoundedCornerShape(if (isPlainAssistant) 12.dp else metrics.bubbleShape),
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(if (isPlainAssistant) 6.dp else 6.dp),
                ) {
                    if (totalDurationLabel.isNotBlank()) {
                        ProcessedDurationHeader(totalDurationLabel)
                    }
                    if (message.attachments.any { it.isPreviewImage() }) {
                        AttachmentPreviewRow(message.attachments)
                    }
                    when {
                        message.type == "plan" -> PlanMessageCard(message, onOpenPlan)
                        else -> MarkdownMessageContent(
                            text = message.text.ifBlank { "..." },
                            streaming = message.streaming && isPlainAssistant,
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
private fun ProcessedDurationHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f),
            modifier = Modifier.size(16.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
}

@Composable
private fun ThinkingMessageContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val rawLabel = text.trim().trimEnd('。', '.', '…')
    val label = when {
        rawLabel.startsWith("已排队") -> rawLabel
        strings.thinkingLabel.equals("Thinking", ignoreCase = true) -> "Thinking"
        else -> "正在思考中"
    }
    Row(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
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
        image = null
        image = withContext(Dispatchers.IO) {
            attachment.previewUri
                ?.let { uri -> runCatching { Uri.parse(uri) }.getOrNull() }
                ?.let { uri ->
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                            readBytesWithinLimit(stream, MARKDOWN_IMAGE_MAX_BYTES)
                        } ?: return@runCatching null
                        sampledBitmapFromBytes(bytes, ATTACHMENT_PREVIEW_MAX_DIMENSION)?.asImageBitmap()
                    }.getOrNull()
                }
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
    val strings = LocalAppStrings.current
    val actionable = remember(message.text, message.streaming) { isActionablePlanMessage(message) }
    val displayText = remember(message.text) {
        planDisplayText(message.text).ifBlank { strings.planMessageFallback }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                if (actionable) strings.planReady else strings.planPreparing,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        MarkdownMessageContent(
            text = displayText,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetailMessageCard(
    message: AgentMessage,
    relayUrl: String = "",
    apiKey: String = "",
    onOpenDiffReview: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    var expanded by remember(message.stableKey()) { mutableStateOf(detailMessageDefaultExpanded(message)) }
    val detail = remember(message.text, message.detailText, message.type, strings) { message.detailDisplay(strings) }
    val body = remember(detail.body, message.type) {
        if (message.type == "file_change") cleanFileChangeBody(detail.body) else detail.body
    }.ifBlank { "..." }
    val bodyIsLong = body.length > LONG_DETAIL_TEXT_LIMIT
    val visibleBody = body
    val artifact = detail.artifact
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    fun openDirectSource(source: String) {
        val target = artifactOpenSource(source)
        if (target.isBlank()) return
        val uri = runCatching { Uri.parse(target) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https", "content")) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    fun openArtifactSource(source: String) {
        if (source.isLocalArtifactSource() && relayUrl.isNotBlank()) {
            clipboardScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    downloadArtifactSourceToCache(context, source, relayUrl, apiKey)
                }
                if (cached == null) {
                    Toast.makeText(context, strings.artifactOpenFailed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(cached.uri, cached.mimeType)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }.onFailure {
                    Toast.makeText(context, strings.artifactOpenFailed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            openDirectSource(source)
        }
    }
    fun shareSource(source: String) {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, source),
                    strings.shareSource,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
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
            icon = artifactIcon(artifact) ?: Icons.Default.Description,
            iconTint = artifactIconTint(artifact),
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
                        onClick = rememberHapticClick { copyText(detail.body.ifBlank { message.text }) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(if (bodyIsLong) strings.copyFullText else strings.copyContent) },
                    )
                    val artifactSource = artifact?.source.orEmpty()
                    if (artifactSource.isNotBlank()) {
                        val openableSource = artifactOpenSource(artifactSource)
                        val sourceScheme = runCatching { Uri.parse(openableSource).scheme?.lowercase().orEmpty() }.getOrDefault("")
                        if (sourceScheme in setOf("http", "https", "content") || (artifactSource.isLocalArtifactSource() && relayUrl.isNotBlank())) {
                            AssistChip(
                                onClick = rememberHapticClick { openArtifactSource(artifactSource) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text(strings.openSource) },
                            )
                        }
                        AssistChip(
                            onClick = rememberHapticClick { shareSource(artifactSource) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(strings.shareSource) },
                        )
                        AssistChip(
                            onClick = rememberHapticClick { copyText(artifactSource) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(strings.copySource) },
                        )
                    }
                    if (message.type == "file_change") {
                        AssistChip(
                            onClick = rememberHapticClick(onOpenDiffReview),
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(strings.viewFullDiff) },
                        )
                        detail.files.take(3).forEach { path ->
                            AssistChip(
                                onClick = rememberHapticClick { copyText(path) },
                                leadingIcon = { FileTypeIcon(path, modifier = Modifier.size(18.dp)) },
                                label = { Text(path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                artifact?.let { display ->
                    if (display.type == "test_result" && display.status.isNotBlank()) {
                        ArtifactStatusBadge(display.status, display.statusKind)
                    }
                    if (display.type == "screenshot" && display.source.isNotBlank()) {
                        MarkdownImage(MarkdownImageRef(display.title, display.source), relayUrl, apiKey)
                        Text(
                            display.source,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.48f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        visibleBody,
                        modifier = Modifier
                            .padding(horizontal = 11.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun artifactIcon(artifact: StructuredArtifactDisplay?): ImageVector? {
    return when (artifact?.type) {
        "screenshot" -> Icons.Default.Image
        "test_result" -> when (artifact.statusKind) {
            StructuredArtifactStatus.Passed -> Icons.Default.TaskAlt
            StructuredArtifactStatus.Failed -> Icons.Default.Error
            else -> Icons.Default.Science
        }
        "plugin_activity" -> Icons.Default.Extension
        else -> null
    }
}

@Composable
private fun artifactIconTint(artifact: StructuredArtifactDisplay?): Color {
    return when (artifact?.statusKind) {
        StructuredArtifactStatus.Passed -> Color(0xFF16A34A)
        StructuredArtifactStatus.Failed -> MaterialTheme.colorScheme.error
        StructuredArtifactStatus.Running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f)
    }
}

@Composable
private fun ArtifactStatusBadge(status: String, statusKind: StructuredArtifactStatus) {
    val color = when (statusKind) {
        StructuredArtifactStatus.Passed -> Color(0xFF16A34A)
        StructuredArtifactStatus.Failed -> MaterialTheme.colorScheme.error
        StructuredArtifactStatus.Running -> MaterialTheme.colorScheme.primary
        StructuredArtifactStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                when (statusKind) {
                    StructuredArtifactStatus.Passed -> Icons.Default.TaskAlt
                    StructuredArtifactStatus.Failed -> Icons.Default.Error
                    else -> Icons.Default.Science
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(status, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    val entries = detail.fileEntries.ifEmpty { detail.files.map { FileChangeEntry(it) } }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .hapticClickable(role = Role.Button, onClick = onToggleExpanded)
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
                strings.filesChangedCount(detail.files.size.coerceAtLeast(1)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileChangeStatText(detail.additions, detail.deletions)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) strings.detailsCollapse else strings.detailsExpand,
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
                        onClick = rememberHapticClick(onOpenDiffReview),
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(strings.viewFullDiff) },
                    )
                    AssistChip(
                        onClick = rememberHapticClick(onCopyText),
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
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
                            FileChangeRow(entry)
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
        FileTypeIcon(entry.path)
        Text(
            entry.path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
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
    streaming: Boolean = false,
    relayUrl: String = "",
    apiKey: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val displayText = rememberSmoothStreamingText(text, streaming)
    var expanded by remember(text) { mutableStateOf(false) }
    val isLong = previewLongContent && textNeedsPreview(displayText)
    val visibleText = remember(displayText, expanded, previewLongContent) {
        if (isLong && !expanded) messagePreviewText(displayText) else displayText
    }
    val document = remember(visibleText) { parseMarkdownForMobile(visibleText) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MarkdownNodeChildren(document, relayUrl, apiKey)
        if (isLong) {
            OutlinedButton(
                onClick = rememberHapticClick { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (expanded) LocalAppStrings.current.collapse else LocalAppStrings.current.expandMore)
            }
        }
    }
}

@Composable
private fun rememberSmoothStreamingText(targetText: String, streaming: Boolean): String {
    var displayedText by remember { mutableStateOf(if (streaming) "" else targetText) }
    LaunchedEffect(targetText, streaming) {
        if (!streaming) {
            displayedText = targetText
            return@LaunchedEffect
        }
        if (targetText.isBlank()) {
            displayedText = targetText
            return@LaunchedEffect
        }
        if (displayedText.isBlank() || !targetText.startsWith(displayedText)) {
            displayedText = targetText.take(minOf(displayedText.length, targetText.length))
        }
        while (displayedText.length < targetText.length) {
            val remaining = targetText.length - displayedText.length
            val step = streamingRevealStep(remaining)
            displayedText = targetText.take(displayedText.length + step)
            delay(STREAMING_REVEAL_FRAME_MS)
        }
    }
    return displayedText
}

private fun streamingRevealStep(remainingChars: Int): Int {
    return when {
        remainingChars > 1_200 -> 220
        remainingChars > 480 -> 96
        remainingChars > 180 -> 44
        remainingChars > 60 -> 22
        else -> 10
    }
}

@Composable
private fun MarkdownNodeChildren(parent: Node, relayUrl: String, apiKey: String, listDepth: Int = 0) {
    var child = parent.firstChild
    while (child != null) {
        MarkdownNodeBlock(child, relayUrl, apiKey, listDepth)
        child = child.next
    }
}

@Composable
private fun MarkdownNodeBlock(node: Node, relayUrl: String, apiKey: String, listDepth: Int = 0) {
    when (node) {
        is Document -> MarkdownNodeChildren(node, relayUrl, apiKey, listDepth)
        is Paragraph -> MarkdownParagraph(node, relayUrl, apiKey)
        is Heading -> MarkdownHeading(node)
        is FencedCodeBlock -> MarkdownCodeBlock(MarkdownBlock(node.literal.orEmpty(), node.info?.trim()?.takeIf { it.isNotBlank() }, isCode = true))
        is IndentedCodeBlock -> MarkdownCodeBlock(MarkdownBlock(node.literal.orEmpty(), isCode = true))
        is BlockQuote -> MarkdownBlockQuote(node, relayUrl, apiKey, listDepth)
        is BulletList -> MarkdownList(node, relayUrl, apiKey, ordered = false, start = 1, listDepth = listDepth)
        is OrderedList -> MarkdownList(node, relayUrl, apiKey, ordered = true, start = node.markerStartNumber ?: 1, listDepth = listDepth)
        is TableBlock -> MarkdownAstTableBlock(node)
        is ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
        is HtmlBlock -> MarkdownCodeBlock(MarkdownBlock(node.literal.orEmpty(), "html", isCode = true))
        else -> {
            val text = nodePlainText(node).trim()
            if (text.isNotBlank()) MarkdownParagraphText(markdownInlineFrom(node))
        }
    }
}

@Composable
private fun MarkdownParagraph(node: Paragraph, relayUrl: String, apiKey: String) {
    val onlyChild = node.firstChild?.takeIf { it.next == null }
    if (onlyChild is MarkdownImageNode) {
        MarkdownImage(MarkdownImageRef(nodePlainText(onlyChild), onlyChild.destination.orEmpty()), relayUrl, apiKey)
        return
    }
    MarkdownParagraphText(markdownInlineFrom(node))
}

@Composable
private fun MarkdownParagraphText(text: AnnotatedString, modifier: Modifier = Modifier) {
    if (text.text.isBlank()) return
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    )
}

@Composable
private fun MarkdownHeading(node: Heading) {
    Text(
        markdownInlineFrom(node),
        style = when (node.level) {
            1 -> MaterialTheme.typography.titleMedium
            2 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.titleSmall
        },
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    )
}

@Composable
private fun MarkdownBlockQuote(node: BlockQuote, relayUrl: String, apiKey: String, listDepth: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.width(3.dp).heightIn(min = 28.dp),
        ) {}
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MarkdownNodeChildren(node, relayUrl, apiKey, listDepth)
        }
    }
}

@Composable
private fun MarkdownList(
    node: ListBlock,
    relayUrl: String,
    apiKey: String,
    ordered: Boolean,
    start: Int,
    listDepth: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (node.isTight) 1.dp else 4.dp)) {
        var child = node.firstChild
        var index = 0
        while (child != null) {
            if (child is ListItem) {
                MarkdownListItem(child, relayUrl, apiKey, ordered, start + index, listDepth)
                index += 1
            }
            child = child.next
        }
    }
}

@Composable
private fun MarkdownListItem(
    item: ListItem,
    relayUrl: String,
    apiKey: String,
    ordered: Boolean,
    number: Int,
    listDepth: Int,
) {
    val taskMarker = item.findFirstChild<TaskListItemMarker>()
    val leadingFileReference = remember(item) { markdownLeadingFileReference(item) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (listDepth * 12).dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (listDepth > 0) {
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp, end = 2.dp)
                    .width(1.5.dp)
                    .heightIn(min = 16.dp)
            ) {}
        }
        Text(
            when {
                taskMarker != null -> if (taskMarker.isChecked()) "[x]" else "[ ]"
                ordered -> "$number."
                else -> when (listDepth % 3) {
                    0 -> "•"
                    1 -> "◦"
                    else -> "▪"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (taskMarker != null) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        )
        if (leadingFileReference != null) {
            CompactFileReferenceMarker(
                leadingFileReference.path,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MarkdownNodeChildren(item, relayUrl, apiKey, listDepth + 1)
        }
    }
}

@Composable
private fun CompactFileReferenceMarker(path: String, modifier: Modifier = Modifier) {
    val spec = fileTypeBadgeSpec(path)
    if (spec.generic) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = modifier.size(14.dp),
        )
        return
    }
    val accent = Color(spec.background)
    Surface(
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
        modifier = modifier.size(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                spec.label.take(2),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 6.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

internal fun markdownTableAt(lines: List<String>, start: Int): Pair<MarkdownTable, Int>? {
    if (start + 1 >= lines.size) return null
    val header = parseMarkdownTableRow(lines[start]) ?: return null
    if (header.size < 2) return null
    val separator = parseMarkdownTableRow(lines[start + 1]) ?: return null
    if (separator.size < header.size || !separator.take(header.size).all(::isMarkdownTableSeparatorCell)) return null

    val rows = mutableListOf<List<String>>()
    var cursor = start + 2
    while (cursor < lines.size) {
        val row = parseMarkdownTableRow(lines[cursor]) ?: break
        if (row.size < 2) break
        rows += row.normalizedMarkdownTableRow(header.size)
        cursor += 1
    }
    if (rows.isEmpty()) return null
    return MarkdownTable(header, rows) to cursor
}

private fun parseMarkdownTableRow(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return null
    val withoutOuterPipes = trimmed
        .removePrefix("|")
        .removeSuffix("|")
    val cells = withoutOuterPipes.split('|').map { it.trim() }
    if (cells.size < 2 || cells.all { it.isBlank() }) return null
    return cells
}

private fun isMarkdownTableSeparatorCell(cell: String): Boolean {
    return Regex("^:?-{3,}:?$").matches(cell.trim())
}

private fun List<String>.normalizedMarkdownTableRow(columnCount: Int): List<String> {
    return when {
        size == columnCount -> this
        size > columnCount -> take(columnCount)
        else -> this + List(columnCount - size) { "" }
    }
}

private fun tableRows(table: TableBlock): List<List<MarkdownTableCellModel>> {
    val rows = mutableListOf<List<MarkdownTableCellModel>>()
    table.walkChildren { node ->
        if (node is TableRow) {
            rows.add(node.children()
                .filterIsInstance<TableCell>()
                .map { cell ->
                    MarkdownTableCellModel(
                        source = cell,
                        plainText = nodePlainText(cell),
                        header = cell.isHeader(),
                    )
                }.toList())
        }
    }
    return rows
}

@Composable
private fun MarkdownAstTableBlock(table: TableBlock) {
    val rows = remember(table) { tableRows(table) }
    if (rows.isEmpty()) return
    val columnCount = rows.maxOf { it.size }
    val columnWidths = remember(rows) {
        (0 until columnCount).map { column ->
            val maxChars = rows
                .mapNotNull { row -> row.getOrNull(column)?.plainText }
                .maxOfOrNull { it.length }
                ?: 0
            when {
                maxChars <= 4 -> 72.dp
                maxChars <= 8 -> 96.dp
                maxChars <= 14 -> 128.dp
                else -> 168.dp
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    columnWidths.forEachIndexed { columnIndex, width ->
                        val cell = row.getOrNull(columnIndex)
                        Text(
                            cell?.let { markdownInlineFrom(it.source) } ?: AnnotatedString(" "),
                            style = if (cell?.header == true) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (cell?.header == true) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (cell?.header == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun markdownInlineFrom(node: Node): AnnotatedString {
    val styles = MarkdownInlineStyles(
        code = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
        link = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium,
        ),
    )
    return buildAnnotatedString {
        appendMarkdownInlineChildren(node, styles, SpanStyle())
    }
}

private fun AnnotatedString.Builder.appendMarkdownInlineChildren(
    parent: Node,
    styles: MarkdownInlineStyles,
    currentStyle: SpanStyle,
) {
    var child = parent.firstChild
    while (child != null) {
        appendMarkdownInline(child, styles, currentStyle)
        child = child.next
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    node: Node,
    styles: MarkdownInlineStyles,
    currentStyle: SpanStyle,
) {
    when (node) {
        is MarkdownTextNode -> withStyle(currentStyle) { append(node.literal.orEmpty()) }
        is SoftLineBreak -> withStyle(currentStyle) { append("\n") }
        is HardLineBreak -> withStyle(currentStyle) { append("\n") }
        is Code -> withStyle(currentStyle.merge(styles.code)) { append(node.literal.orEmpty()) }
        is HtmlInline -> withStyle(currentStyle) { append(node.literal.orEmpty()) }
        is Emphasis -> appendMarkdownInlineChildren(node, styles, currentStyle.merge(SpanStyle(fontStyle = FontStyle.Italic)))
        is StrongEmphasis -> appendMarkdownInlineChildren(node, styles, currentStyle.merge(SpanStyle(fontWeight = FontWeight.Bold)))
        is Strikethrough -> appendMarkdownInlineChildren(node, styles, currentStyle.merge(SpanStyle(textDecoration = TextDecoration.LineThrough)))
        is MarkdownLinkNode -> {
            val destination = node.destination.orEmpty()
            withLink(LinkAnnotation.Url(destination, TextLinkStyles(styles.link))) {
                appendMarkdownInlineChildren(node, styles, currentStyle.merge(styles.link))
            }
        }
        is MarkdownImageNode -> {
            val alt = nodePlainText(node).ifBlank { node.destination.orEmpty() }
            withStyle(currentStyle.merge(SpanStyle(fontStyle = FontStyle.Italic))) { append(alt) }
        }
        is TaskListItemMarker -> Unit
        else -> appendMarkdownInlineChildren(node, styles, currentStyle)
    }
}

private fun nodePlainText(node: Node): String {
    val builder = StringBuilder()
    fun appendNode(current: Node) {
        when (current) {
            is MarkdownTextNode -> builder.append(current.literal.orEmpty())
            is Code -> builder.append(current.literal.orEmpty())
            is HtmlInline -> builder.append(current.literal.orEmpty())
            is HtmlBlock -> builder.append(current.literal.orEmpty())
            is FencedCodeBlock -> builder.append(current.literal.orEmpty())
            is IndentedCodeBlock -> builder.append(current.literal.orEmpty())
            is SoftLineBreak, is HardLineBreak -> builder.append('\n')
        }
        var child = current.firstChild
        while (child != null) {
            appendNode(child)
            child = child.next
        }
    }
    appendNode(node)
    return builder.toString()
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

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}

private fun Node.walkChildren(block: (Node) -> Unit) {
    var child = firstChild
    while (child != null) {
        block(child)
        child.walkChildren(block)
        child = child.next
    }
}

private inline fun <reified T : Node> Node.findFirstChild(): T? {
    var found: T? = null
    walkChildren { child ->
        if (found == null && child is T) found = child
    }
    return found
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

private fun imageLoadSource(source: String, relayUrl: String): String {
    val clean = source.trim()
    if (!clean.isLocalImagePath() || relayUrl.isBlank()) return clean
    val localPath = if (clean.startsWith("file://", ignoreCase = true)) {
        runCatching { Uri.parse(clean).path }.getOrNull().orEmpty().ifBlank { clean.removePrefix("file://") }
    } else {
        clean
    }
    return "${relayHttpBase(relayUrl)}/media/image?path=${URLEncoder.encode(localPath, "UTF-8")}"
}

private fun String.isLocalArtifactSource(): Boolean = isLocalImagePath()

private fun localArtifactPath(source: String): String {
    val clean = source.trim()
    return if (clean.startsWith("file://", ignoreCase = true)) {
        runCatching { Uri.parse(clean).path }.getOrNull().orEmpty().ifBlank { clean.removePrefix("file://") }
    } else {
        clean
    }
}

private fun artifactFileLoadSource(source: String, relayUrl: String): String {
    val clean = source.trim()
    if (!clean.isLocalArtifactSource() || relayUrl.isBlank()) return clean
    return "${relayHttpBase(relayUrl)}/media/file?path=${URLEncoder.encode(localArtifactPath(clean), "UTF-8")}"
}

internal fun artifactOpenSource(source: String): String {
    val clean = source.trim()
    if (clean.isLocalImagePath()) return ""
    return clean
}

private data class CachedArtifactSource(val uri: Uri, val mimeType: String)

private fun artifactCacheFileName(source: String, contentType: String): String {
    val base = localArtifactPath(source)
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "artifact" }
        .take(96)
    val fallbackExtension = when {
        contentType.startsWith("image/png") -> ".png"
        contentType.startsWith("image/jpeg") -> ".jpg"
        contentType.startsWith("image/webp") -> ".webp"
        contentType.startsWith("application/pdf") -> ".pdf"
        contentType.startsWith("text/") -> ".txt"
        else -> ""
    }
    return if (base.contains('.')) base else base + fallbackExtension
}

private fun mimeTypeForArtifact(name: String, contentType: String): String {
    val cleanContentType = contentType.substringBefore(';').trim().lowercase()
    if (cleanContentType.isNotBlank() && cleanContentType != "application/octet-stream") return cleanContentType
    return when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt", "log", "md", "json", "csv", "tsv", "xml", "html", "css", "js", "ts", "kt", "java", "py", "rs", "go" -> "text/plain"
        else -> "application/octet-stream"
    }
}

private fun downloadArtifactSourceToCache(
    context: android.content.Context,
    source: String,
    relayUrl: String,
    apiKey: String,
): CachedArtifactSource? {
    return runCatching {
        val loadSource = artifactFileLoadSource(source, relayUrl)
        if (!loadSource.isHttpImageSource()) return null
        val relayBase = relayHttpBase(relayUrl)
        val connection = (URL(loadSource).openConnection() as? HttpURLConnection) ?: return null
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        if (apiKey.isNotBlank() && loadSource.startsWith("$relayBase/media/file?", ignoreCase = true)) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.connectTimeout = MARKDOWN_IMAGE_CONNECT_TIMEOUT_MS
        connection.readTimeout = MARKDOWN_IMAGE_READ_TIMEOUT_MS
        if (connection.responseCode !in 200..299) return null
        val contentType = connection.contentType.orEmpty()
        val bytes = connection.inputStream.use { stream ->
            readBytesWithinLimit(stream, MARKDOWN_IMAGE_MAX_BYTES)
        } ?: return null
        val fileName = artifactCacheFileName(source, contentType)
        val cacheDir = File(context.cacheDir, "artifacts").apply { mkdirs() }
        val target = File(cacheDir, fileName)
        target.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        CachedArtifactSource(uri, mimeTypeForArtifact(fileName, contentType))
    }.getOrNull()
}

private fun readBytesWithinLimit(input: java.io.InputStream, maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun decodeHttpImage(source: String, apiKey: String, relayHttpBase: String): android.graphics.Bitmap? {
    val connection = URL(source).openConnection()
    connection.useCaches = false
    if (apiKey.isNotBlank() && source.startsWith("$relayHttpBase/media/image?", ignoreCase = true)) {
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        (connection as? HttpURLConnection)?.instanceFollowRedirects = false
    }
    connection.connectTimeout = MARKDOWN_IMAGE_CONNECT_TIMEOUT_MS
    connection.readTimeout = MARKDOWN_IMAGE_READ_TIMEOUT_MS
    val contentLength = connection.contentLengthLong
    if (contentLength > MARKDOWN_IMAGE_MAX_BYTES) return null
    val bytes = connection.getInputStream().use { stream ->
        readBytesWithinLimit(stream, MARKDOWN_IMAGE_MAX_BYTES)
    } ?: return null
    return sampledBitmapFromBytes(bytes, ATTACHMENT_PREVIEW_MAX_DIMENSION)
}

@Composable
private fun MarkdownImage(image: MarkdownImageRef, relayUrl: String, apiKey: String) {
    var bitmap by remember(image.source, relayUrl, apiKey) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(image.source, relayUrl, apiKey) { mutableStateOf(false) }
    val relayBase = remember(relayUrl) { relayHttpBase(relayUrl) }
    val loadSource = remember(image.source, relayUrl) { imageLoadSource(image.source, relayUrl) }
    LaunchedEffect(loadSource) {
        bitmap = null
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    loadSource.isDataImageSource() -> {
                        val encoded = loadSource.substringAfter(',', "")
                        if ((encoded.length * 3L) / 4L > MARKDOWN_IMAGE_MAX_BYTES) return@runCatching null
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        if (bytes.size > MARKDOWN_IMAGE_MAX_BYTES) return@runCatching null
                        sampledBitmapFromBytes(bytes, ATTACHMENT_PREVIEW_MAX_DIMENSION)
                    }
                    loadSource.isHttpImageSource() -> {
                        decodeHttpImage(loadSource, apiKey, relayBase)
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
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    val codeScroll = rememberScrollState()
    val codeBlockShape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        shape = codeBlockShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(codeBlockShape),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.language ?: "code",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = rememberHapticClick { copyText(block.text) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = strings.copyCode, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(codeScroll)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    block.text.ifBlank { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                )
            }
        }
    }
}
