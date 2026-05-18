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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun FilePathLine(path: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FileTypeIcon(path)
        Text(
            path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
                TextButton(onClick = rememberHapticClick(onConfigure)) {
                    Text(strings.fillIn)
                }
            }
            IconButton(onClick = rememberHapticClick(onHelp)) {
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
            Button(onClick = rememberHapticClick(onApprove)) {
                Text("批准")
            }
        },
        dismissButton = {
            TextButton(onClick = rememberHapticClick(onDeny)) {
                Text("拒绝")
            }
        },
    )
}

@Composable
fun UserInputRequestDialog(
    request: AgentUserInputRequest,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var answers by remember(request.id) { mutableStateOf(emptyMap<String, String>()) }
    var currentQuestionIndex by remember(request.id) { mutableStateOf(0) }
    val questions = request.questions
    val questionCount = questions.size
    val safeIndex = currentQuestionIndex.coerceIn(0, (questionCount - 1).coerceAtLeast(0))
    val currentQuestion = questions.getOrNull(safeIndex)
    val canContinue = currentQuestion?.let { answers[it.id].orEmpty().isNotBlank() } == true
    val isLastQuestion = safeIndex >= questionCount - 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CodeX 等你回答") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (questionCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { currentQuestionIndex = (safeIndex - 1).coerceAtLeast(0) },
                            enabled = safeIndex > 0,
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
                            enabled = canContinue && !isLastQuestion,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一题")
                        }
                    }
                }
                currentQuestion?.let { question ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            question.question.ifBlank { question.header.ifBlank { request.detail.ifBlank { "你的回答" } } },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (question.header.isNotBlank() && question.question.isNotBlank()) {
                            Text(
                                question.header,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (question.options.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                question.options.forEach { option ->
                                    val selected = answers[question.id] == option.label
                                    FilterChip(
                                        selected = selected,
                                        onClick = rememberHapticClick { answers = answers + (question.id to option.label) },
                                        label = {
                                            Column {
                                                Text(option.label)
                                                if (option.description.isNotBlank()) {
                                                    Text(
                                                        option.description,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        },
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
                                minLines = 2,
                                label = { Text("没有我想要的答案，向 CodeX 提问") },
                                visualTransformation = if (question.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canContinue,
                onClick = rememberHapticClick {
                    if (!isLastQuestion) {
                        currentQuestionIndex = (safeIndex + 1).coerceAtMost(questionCount - 1)
                    } else {
                        onSubmit(answers.mapValues { it.value.trim() }.filterValues { it.isNotBlank() })
                    }
                },
            ) {
                Text(if (isLastQuestion) "发送回答" else "下一题")
            }
        },
        dismissButton = {
            TextButton(onClick = rememberHapticClick(onDismiss)) {
                Text("稍后")
            }
        },
    )
}

@Composable
fun PlanReviewDialog(
    review: PlanReview,
    onDismiss: () -> Unit,
    onOptimize: (String) -> Unit,
    onStart: () -> Unit,
) {
    var adjustmentText by remember(review.agentId, review.message.stableKey()) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("是否开始这个计划？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "任务：${review.taskName.ifBlank { "未命名任务" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (review.projectPath.isNotBlank()) {
                    Text(
                        review.projectPath,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "你可以直接开始，也可以写一句希望怎么调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PlanReviewPreview(
                        text = review.message.text.ifBlank { "计划内容为空。" },
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
                OutlinedTextField(
                    value = adjustmentText,
                    onValueChange = { adjustmentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("补充调整要求") },
                    placeholder = { Text("例如：先问我更多问题，或把测试步骤写清楚") },
                )
            }
        },
        confirmButton = {
            Button(onClick = rememberHapticClick(onStart)) {
                Text("开始计划")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = rememberHapticClick { onOptimize(adjustmentText) }) {
                    Text("让他补充/调整")
                }
                TextButton(onClick = rememberHapticClick(onDismiss)) {
                    Text("稍后")
                }
            }
        },
    )
}

@Composable
private fun PlanReviewPreview(text: String, modifier: Modifier = Modifier) {
    val planText = remember(text) { planDisplayText(text).ifBlank { "计划内容为空。" } }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkdownMessageContent(planText)
    }
}

private enum class DiffReviewSection {
    Files,
    Diff,
    Preview,
    Commit,
}

@Composable
fun DiffReviewDialog(
    review: DiffReviewState,
    commitDraft: GitCommitDraft,
    onSelectFile: (String?) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onSetFileSelection: (List<String>) -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var showCommitConfirm by remember(review.agentId, review.cwd) { mutableStateOf(false) }
    var showRestoreConfirm by remember(review.agentId, review.cwd) { mutableStateOf(false) }
    var activeSection by remember(review.agentId, review.cwd, review.requestId) { mutableStateOf(DiffReviewSection.Diff) }
    val restorableSelection = remember(review.selectedFiles, review.status?.restorableFiles) {
        val restorableFiles = review.status?.restorableFiles.orEmpty()
        if (restorableFiles.isEmpty()) review.selectedFiles else review.selectedFiles.filter { it in restorableFiles }
    }
    fun copyText(text: String) {
        clipboardScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("EasyCodex", text)))
        }
    }
    val displayDiff = remember(review.diff, review.selectedFile) {
        review.diff.take(12_000) + if (review.diff.length > 12_000) "\n\n... ${strings.diffTruncated}" else ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(strings.diffReview, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            review.cwd,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = rememberHapticClick(onDismiss)) {
                        Text(strings.close)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Text(
                                listOf(
                                    status.branch.takeIf { it.isNotBlank() }?.let { strings.gitBranchLabel(it) },
                                    if (status.isClean) strings.gitCleanWorkspace else strings.gitChangedFilesCount(status.files.size),
                                    strings.selectedFilesCount(review.selectedFiles.size, review.files.size),
                                ).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            status.files.take(3).forEach { file -> FilePathLine(file) }
                            if (status.files.size > 3) {
                                Text(
                                    strings.moreFilesCount(status.files.size - 3),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        DiffReviewSection.Files to strings.diffSectionFiles,
                        DiffReviewSection.Diff to strings.diffSectionDiff,
                        DiffReviewSection.Preview to strings.diffSectionPreview,
                        DiffReviewSection.Commit to strings.diffSectionCommit,
                    ).forEach { (section, label) ->
                        val enabled = section != DiffReviewSection.Preview || review.selectedFile != null
                        FilterChip(
                            selected = activeSection == section,
                            onClick = rememberHapticClick { if (enabled) activeSection = section },
                            enabled = enabled,
                            label = { Text(label) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (activeSection) {
                        DiffReviewSection.Files -> {
                            Text(strings.fileDiff, style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = review.selectedFile == null,
                                    onClick = rememberHapticClick { onSelectFile(null) },
                                    label = { Text(strings.all) },
                                )
                                review.files.forEach { file ->
                                    FilterChip(
                                        selected = review.selectedFile == file.path,
                                        onClick = rememberHapticClick {
                                            onSelectFile(file.path)
                                            activeSection = DiffReviewSection.Diff
                                        },
                                        leadingIcon = { FileTypeIcon(file.path, modifier = Modifier.size(18.dp)) },
                                        label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                            Text(strings.commitRestoreSelection, style = MaterialTheme.typography.labelLarge)
                            val restorableFiles = review.status?.restorableFiles.orEmpty()
                            Text(
                                strings.selectedFilesCount(review.selectedFiles.size, review.files.size) +
                                    if (restorableFiles.isNotEmpty() && restorableFiles.size < review.files.size) {
                                        " · ${strings.untrackedFilesNotRestored(review.files.size - restorableFiles.size)}"
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = false,
                                    onClick = rememberHapticClick { onSetFileSelection(review.files.map { it.path }) },
                                    label = { Text(strings.selectAll) },
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = rememberHapticClick { onSetFileSelection(emptyList()) },
                                    label = { Text(strings.clearSelection) },
                                )
                                review.selectedFile?.let { currentFile ->
                                    FilterChip(
                                        selected = review.selectedFiles == listOf(currentFile),
                                        onClick = rememberHapticClick { onSetFileSelection(listOf(currentFile)) },
                                        label = { Text(strings.currentFileOnly) },
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                review.files.forEach { file ->
                                    val trackedForRestore = restorableFiles.isEmpty() || file.path in restorableFiles
                                    FilterChip(
                                        selected = file.path in review.selectedFiles,
                                        onClick = rememberHapticClick { onToggleFileSelection(file.path) },
                                        leadingIcon = { FileTypeIcon(file.path, modifier = Modifier.size(18.dp)) },
                                        label = {
                                            Text(
                                                file.name + if (!trackedForRestore) " · ${strings.untrackedBadge}" else "",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        DiffReviewSection.Diff -> {
                            Text(if (review.selectedFile == null) strings.fullDiff else strings.singleFileDiff, style = MaterialTheme.typography.labelLarge)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    displayDiff.ifBlank { if (review.loading) "..." else strings.noDiff },
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        DiffReviewSection.Preview -> {
                            Text(strings.filePreview, style = MaterialTheme.typography.labelLarge)
                            review.selectedFile?.let { selected ->
                                TextButton(onClick = rememberHapticClick { copyText(selected) }) {
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
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        DiffReviewSection.Commit -> {
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
                                        if (commitDraft.files.isEmpty()) strings.noChangesToCommit else strings.commitFilesCount(commitDraft.files.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    commitDraft.files.take(10).forEach { file -> FilePathLine(file) }
                                    if (commitDraft.files.size > 10) {
                                        Text(
                                            strings.moreFilesCount(commitDraft.files.size - 10),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    commitDraft.error?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    review.restoreError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = rememberHapticClick { copyText(review.diff) }, enabled = review.diff.isNotBlank()) {
                        Text(strings.copyDiff)
                    }
                    TextButton(
                        onClick = rememberHapticClick {
                            activeSection = DiffReviewSection.Commit
                            showRestoreConfirm = true
                        },
                        enabled = !review.restoreBusy && restorableSelection.isNotEmpty(),
                    ) {
                        if (review.restoreBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(strings.discardChanges)
                    }
                    Button(
                        onClick = rememberHapticClick {
                            activeSection = DiffReviewSection.Commit
                            showCommitConfirm = true
                        },
                        enabled = !commitDraft.busy && commitDraft.message.isNotBlank() && commitDraft.files.isNotEmpty(),
                    ) {
                        if (commitDraft.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(strings.commitChanges)
                    }
                    TextButton(onClick = rememberHapticClick(onDismiss)) {
                        Text(strings.close)
                    }
                }
            }
        }
    }
    if (showCommitConfirm) {
        AlertDialog(
            onDismissRequest = { showCommitConfirm = false },
            title = { Text(strings.confirmCommitChanges) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(commitDraft.message)
                    Text(strings.commitFilesCount(commitDraft.files.size))
                    commitDraft.files.take(10).forEach { file ->
                        FilePathLine(file)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = rememberHapticClick {
                        showCommitConfirm = false
                        onCommit()
                    },
                    enabled = !commitDraft.busy,
                ) {
                    Text(strings.confirmCommit)
                }
            },
            dismissButton = {
                TextButton(onClick = rememberHapticClick { showCommitConfirm = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(strings.confirmDiscardChanges) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.confirmDiscardChangesBody)
                    Text(strings.commitFilesCount(restorableSelection.size))
                    restorableSelection.take(10).forEach { file ->
                        FilePathLine(file)
                    }
                    if (restorableSelection.size > 10) {
                        Text(
                            strings.moreFilesCount(restorableSelection.size - 10),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = rememberHapticClick {
                        showRestoreConfirm = false
                        onRestoreSelected()
                    },
                    enabled = !review.restoreBusy,
                ) {
                    Text(strings.confirmDiscard)
                }
            },
            dismissButton = {
                TextButton(onClick = rememberHapticClick { showRestoreConfirm = false }) {
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
        confirmButton = { Button(onClick = rememberHapticClick(onConfigure)) { Text(strings.openSettings) } },
        dismissButton = { TextButton(onClick = rememberHapticClick(onDismiss)) { Text(strings.close) } },
    )
}

