package com.easycodex.mobile

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CliConsoleScreen(
    state: CliConsoleState,
    connected: Boolean,
    onCwdChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onSandboxModeChange: (String) -> Unit,
    onSkipGitRepoCheckChange: (Boolean) -> Unit,
    projectOptions: List<String>,
    modelOptions: List<CodexModelOption>,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCreateWindow: () -> Unit,
    onSelectWindow: (String) -> Unit,
) {
    val window = state.activeWindow
    val listState = rememberLazyListState()
    var showProjectPicker by remember { mutableStateOf(false) }
    var showCommandMenu by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showReasoningPicker by remember { mutableStateOf(false) }
    var showSandboxPicker by remember { mutableStateOf(false) }
    var showCliHelp by remember { mutableStateOf(false) }
    val reasoningOptions = remember(modelOptions, window.model) {
        modelOptions.firstOrNull { it.model == window.model }?.supportedReasoningEfforts
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("low", "medium", "high", "xhigh")
    }
    LaunchedEffect(window.id, window.lines.size, window.lines.lastOrNull()?.text?.length) {
        if (window.lines.isNotEmpty()) listState.animateScrollToItem(window.lines.lastIndex + 1)
    }
    LaunchedEffect(window.input) {
        when (window.input.trim()) {
            "/" -> showCommandMenu = true
            "/model" -> {
                onInputChange("")
                showModelPicker = true
            }
            "/reasoning" -> {
                onInputChange("")
                showReasoningPicker = true
            }
            "/sandbox" -> {
                onInputChange("")
                showSandboxPicker = true
            }
            "/git-check" -> {
                onInputChange("")
                onSkipGitRepoCheckChange(false)
            }
            "/skip-git-check" -> {
                onInputChange("")
                onSkipGitRepoCheckChange(true)
            }
            "/project" -> {
                onInputChange("")
                showProjectPicker = true
            }
            "/help" -> {
                onInputChange("")
                showCliHelp = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CliTerminalHeader(
                window = window,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080808)),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 22.dp),
            ) {
                item(key = "cli-tip") {
                    CliTip()
                }
                if (window.lines.isNotEmpty()) {
                    items(window.lines, key = { it.id }) { line ->
                        CliConsoleLine(line)
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))
        CliPromptBar(
            window = window,
            connected = connected,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onShowCommandMenu = { showCommandMenu = true },
            showCommandMenu = showCommandMenu,
            onDismissCommandMenu = { showCommandMenu = false },
            onShowModelPicker = { showModelPicker = true },
            onShowReasoningPicker = { showReasoningPicker = true },
            onShowSandboxPicker = { showSandboxPicker = true },
            onSkipGitRepoCheckChange = onSkipGitRepoCheckChange,
            onShowHelp = { showCliHelp = true },
            onShowProjectPicker = { showProjectPicker = true },
        )
    }

    if (showModelPicker) {
        CliRuntimeChoiceDialog(
            title = "Choose model",
            options = modelOptions.map { CliRuntimeChoice(it.model, it.displayName) },
            selected = window.model,
            onSelect = {
                onModelChange(it)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showReasoningPicker) {
        CliRuntimeChoiceDialog(
            title = "Choose reasoning",
            options = reasoningOptions.map { CliRuntimeChoice(it, it) },
            selected = window.reasoningEffort,
            onSelect = {
                onReasoningEffortChange(it)
                showReasoningPicker = false
            },
            onDismiss = { showReasoningPicker = false },
        )
    }

    if (showSandboxPicker) {
        CliRuntimeChoiceDialog(
            title = "Choose sandbox",
            options = listOf(
                CliRuntimeChoice("read-only", "read-only"),
                CliRuntimeChoice("workspace-write", "workspace-write"),
                CliRuntimeChoice("danger-full-access", "danger-full-access"),
            ),
            selected = window.sandboxMode,
            onSelect = {
                onSandboxModeChange(it)
                showSandboxPicker = false
            },
            onDismiss = { showSandboxPicker = false },
        )
    }

    if (showCliHelp) {
        CliHelpDialog(onDismiss = { showCliHelp = false })
    }

    if (showProjectPicker) {
        DirectoryPickerDialog(
            initialPath = window.cwd,
            pinnedPaths = projectOptions,
            onBrowseDirectories = onBrowseDirectories,
            onSelect = {
                onCwdChange(it)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false },
        )
    }
}

@Composable
private fun CliPromptBar(
    window: CliConsoleWindow,
    connected: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onShowCommandMenu: () -> Unit,
    showCommandMenu: Boolean,
    onDismissCommandMenu: () -> Unit,
    onShowModelPicker: () -> Unit,
    onShowReasoningPicker: () -> Unit,
    onShowSandboxPicker: () -> Unit,
    onSkipGitRepoCheckChange: (Boolean) -> Unit,
    onShowHelp: () -> Unit,
    onShowProjectPicker: () -> Unit,
) {
    Surface(color = Color(0xFF080808)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                ">",
                color = Color(0xFFE6E6E6),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Box {
                TextButton(
                    onClick = {
                        onInputChange("/")
                        onShowCommandMenu()
                    },
                    enabled = connected && !window.busy,
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(
                        "/",
                        color = Color(0xFF2EA3F2),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                DropdownMenu(
                    expanded = showCommandMenu,
                    onDismissRequest = onDismissCommandMenu,
                ) {
                    DropdownMenuItem(
                        text = { Text("/model  Change model") },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onShowModelPicker()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("/reasoning  Change reasoning") },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onShowReasoningPicker()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("/project  Change directory") },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onShowProjectPicker()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("/sandbox  Change sandbox") },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onShowSandboxPicker()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (window.skipGitRepoCheck) {
                                    "/git-check  Require Git repo"
                                } else {
                                    "/skip-git-check  Allow non-Git dir"
                                },
                            )
                        },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onSkipGitRepoCheckChange(!window.skipGitRepoCheck)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("/help  Show CLI commands") },
                        onClick = {
                            onInputChange("")
                            onDismissCommandMenu()
                            onShowHelp()
                        },
                    )
                }
            }
            BasicTextField(
                value = window.input,
                onValueChange = onInputChange,
                enabled = connected && !window.busy,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFE6E6E6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (window.input.isBlank()) {
                        Text(
                            "Summarize recent commits",
                            color = Color(0xFF5F5F5F),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                        )
                    }
                    innerTextField()
                },
            )
            if (window.busy) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(44.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            } else {
                TextButton(
                    onClick = {
                        when (window.input.trim()) {
                            "/", "/model" -> {
                                onInputChange("")
                                onShowModelPicker()
                            }
                            "/reasoning" -> {
                                onInputChange("")
                                onShowReasoningPicker()
                            }
                            "/sandbox" -> {
                                onInputChange("")
                                onShowSandboxPicker()
                            }
                            "/git-check" -> {
                                onInputChange("")
                                onSkipGitRepoCheckChange(false)
                            }
                            "/skip-git-check" -> {
                                onInputChange("")
                                onSkipGitRepoCheckChange(true)
                            }
                            "/project" -> {
                                onInputChange("")
                                onShowProjectPicker()
                            }
                            "/help" -> {
                                onInputChange("")
                                onShowHelp()
                            }
                            else -> onSend()
                        }
                    },
                    enabled = connected && window.input.isNotBlank(),
                    modifier = Modifier.height(44.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (window.input.isBlank()) Color(0xFF4A4A4A) else Color(0xFF2EA3F2),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CliWindowTabs(
    state: CliConsoleState,
    onCreateWindow: () -> Unit,
    onSelectWindow: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFF080808))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.windows.forEach { window ->
            val selected = window.id == state.activeWindowId
            Button(
                onClick = { onSelectWindow(window.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFF1F1F1F) else Color(0xFF0F0F0F),
                    contentColor = if (selected) Color(0xFFE6E6E6) else Color(0xFF8E8E8E),
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (window.busy) "${window.title} *" else window.title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onCreateWindow) {
            Text("+")
        }
    }
}

@Composable
private fun CliTerminalHeader(
    window: CliConsoleWindow,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Surface(
            color = Color(0xFF101010),
            border = BorderStroke(1.dp, Color(0xFF5A5A5A)),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ">_ OpenAI Codex",
                    color = Color(0xFFE6E6E6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (window.version.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "(${window.version})",
                        color = Color(0xFF747474),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private data class CliRuntimeChoice(val value: String, val label: String)

@Composable
private fun CliRuntimeChoiceDialog(
    title: String,
    options: List<CliRuntimeChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val hapticView = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Surface(
                        color = if (option.value == selected) Color(0xFFE8F3FF) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(option.value)
                            },
                    ) {
                        Text(
                            option.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (option.value == selected) Color(0xFF0B5CAD) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun CliHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Codex CLI / commands") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("/model  Change --model")
                Text("/reasoning  Change -c model_reasoning_effort")
                Text("/project  Change --cd")
                Text("/sandbox  Change --sandbox")
                Text("/git-check  Remove --skip-git-repo-check")
                Text("/skip-git-check  Add --skip-git-repo-check")
                Text("/help  Show this list")
                Text(
                    "Advanced exec options like --profile, --image, --json, --output-schema, --add-dir, and resume/review subcommands are not wired into this mobile CLI yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun CliMetaLabel(text: String) {
    Text(
        text,
        color = Color(0xFF858585),
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(108.dp),
    )
}

@Composable
private fun CliMetaValue(text: String) {
    Text(
        text,
        color = Color(0xFFE0E0E0),
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CliTip() {
    Text(
        "Tip: GPT-5.5 is now available in Codex. It's our strongest agentic coding model yet, built to reason through large codebases, check assumptions with tools, and keep going until the work is done.\n\nLearn more: https://openai.com/index/introducing-gpt-5-5/",
        color = Color(0xFFCFCFCF),
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
    )
}

@Composable
private fun CliHeaderLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color(0xFFB8B8B8),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun CliConsoleLine(line: CliConsoleLine) {
    val isUser = line.role == "user"
    val isDiagnostic = line.role == "diagnostic" || line.role == "stderr"
    val content = when {
        isUser -> Color(0xFFE6E6E6)
        line.role == "status" -> Color(0xFF8B8B8B)
        isDiagnostic -> Color(0xFF9A9A9A)
        else -> Color(0xFFD8D8D8)
    }
    val prefix = when (line.role) {
        "user" -> "user\n"
        "diagnostic", "stderr" -> "log\n"
        "status" -> ""
        else -> ""
    }
    Text(
        "$prefix${line.text.ifBlank { "..." }}",
        color = content,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        overflow = TextOverflow.Visible,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 3.dp),
    )
}

private fun CliConsoleWindow.reasoningEffortLabel(): String {
    return when (reasoningEffort) {
        "low", "medium", "high", "xhigh" -> reasoningEffort
        else -> reasoningEffort.ifBlank { "medium" }
    }
}

private fun String.directoryLabelForCli(): String {
    val clean = trim().trimEnd('\\', '/')
    if (clean.isBlank() || clean == ".") return "."
    val home = Regex("^C:\\\\Users\\\\[^\\\\/]+$", RegexOption.IGNORE_CASE)
    if (home.matches(clean)) return "~"
    return clean.substringAfterLast('\\').substringAfterLast('/').ifBlank { clean }
}
