package com.easycodex.mobile

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
fun ApprovalRequestDialog(
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
fun PlanReviewDialog(
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
fun DiffReviewDialog(
    review: DiffReviewState,
    commitDraft: GitCommitDraft,
    onSelectFile: (String?) -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var showCommitConfirm by remember(review.agentId, review.cwd) { mutableStateOf(false) }
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.diffReview) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    review.cwd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (review.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(strings.readingGitStatusAndDiff)
                    }
                }
                review.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                review.status?.let { status ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                listOf(
                                    status.branch.takeIf { it.isNotBlank() }?.let { "分支 $it" },
                                    if (status.isClean) "工作区干净" else "${status.files.size} 个文件有改动",
                                ).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            status.files.take(8).forEach { file ->
                                Text(file, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (review.files.isNotEmpty()) {
                    Text(strings.fileDiff, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = review.selectedFile == null,
                            onClick = { onSelectFile(null) },
                            label = { Text("全部") },
                        )
                        review.files.take(10).forEach { file ->
                            FilterChip(
                                selected = review.selectedFile == file.path,
                                onClick = { onSelectFile(file.path) },
                                label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                Text(if (review.selectedFile == null) strings.fullDiff else strings.singleFileDiff, style = MaterialTheme.typography.labelLarge)
                val displayDiff = remember(review.diff, review.selectedFile) {
                    review.diff.take(12_000) + if (review.diff.length > 12_000) "\n\n... ${strings.diffTruncated}" else ""
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        displayDiff.ifBlank { if (review.loading) "..." else strings.noDiff },
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (review.selectedFile != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.filePreview, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { copyText(review.selectedFile) }) {
                            Text(strings.copyPath)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                review.fileLoading -> strings.readingFile
                                review.fileContent.isBlank() -> strings.fileEmptyOrUnavailable
                                else -> review.fileContent
                            },
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                if (commitDraft.files.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.commitPreview, style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = commitDraft.message,
                                onValueChange = onCommitMessageChange,
                                singleLine = true,
                                label = { Text(strings.commitMessage) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                strings.commitFilesCount(commitDraft.files.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            commitDraft.files.take(6).forEach { file ->
                                Text(file, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (commitDraft.files.size > 6) {
                                Text(
                                    strings.moreFilesCount(commitDraft.files.size - 6),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            commitDraft.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { showCommitConfirm = true },
                                enabled = !commitDraft.busy && commitDraft.message.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (commitDraft.busy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(strings.commitChanges)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { copyText(review.diff) }, enabled = review.diff.isNotBlank()) {
                Text(strings.copyDiff)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
    if (showCommitConfirm) {
        AlertDialog(
            onDismissRequest = { showCommitConfirm = false },
            title = { Text(strings.confirmCommitChanges) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(commitDraft.message)
                    Text(strings.commitFilesCount(commitDraft.files.size))
                    commitDraft.files.take(10).forEach { file ->
                        Text(file, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCommitConfirm = false
                        onCommit()
                    },
                    enabled = !commitDraft.busy,
                ) {
                    Text(strings.confirmCommit)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitConfirm = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
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

