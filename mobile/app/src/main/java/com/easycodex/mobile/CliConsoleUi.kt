package com.easycodex.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCreateWindow: () -> Unit,
    onSelectWindow: (String) -> Unit,
) {
    val window = state.activeWindow
    val listState = rememberLazyListState()
    LaunchedEffect(window.id, window.lines.size, window.lines.lastOrNull()?.text?.length) {
        if (window.lines.isNotEmpty()) listState.animateScrollToItem(window.lines.lastIndex + 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CliWindowTabs(
                state = state,
                onCreateWindow = onCreateWindow,
                onSelectWindow = onSelectWindow,
            )
            CliHeroPanel(
                window = window,
                connected = connected,
                onCwdChange = onCwdChange,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                item(key = "cli-tip") {
                    CliTerminalTip(window, connected)
                }
                if (window.lines.isNotEmpty()) {
                    items(window.lines, key = { it.id }) { line ->
                        CliConsoleLine(line)
                    }
                }
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = window.input,
                onValueChange = onInputChange,
                enabled = connected && !window.busy,
                minLines = 2,
                maxLines = 5,
                label = { Text("输入要交给 Codex CLI 的指令") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (window.busy) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    }
                } else {
                    Button(
                        onClick = onSend,
                        enabled = connected && window.input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("发送")
                    }
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.windows.forEach { window ->
            val selected = window.id == state.activeWindowId
            Button(
                onClick = { onSelectWindow(window.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (window.busy) "${window.title} *" else window.title,
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
private fun CliHeroPanel(
    window: CliConsoleWindow,
    connected: Boolean,
    onCwdChange: (String) -> Unit,
) {
    Surface(
        color = Color(0xFF080808),
        border = BorderStroke(1.dp, Color(0xFF565656)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ">_ OpenAI Codex",
                    color = Color(0xFFE8E8E8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CliMetaRow("model:", "${window.model} ${window.reasoningEffortLabel()}")
                CliMetaRow("directory:", window.cwd.directoryLabelForCli())
            }
            Text(
                when {
                    !connected -> "• Waiting for EasyCodex relay connection"
                    window.busy -> "• Running codex exec (esc/stop to interrupt)"
                    else -> "› Ready for a Codex CLI prompt"
                },
                color = if (window.busy) Color(0xFF8C8C8C) else Color(0xFFCFCFCF),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = window.cwd,
                onValueChange = onCwdChange,
                enabled = connected && !window.busy,
                singleLine = true,
                label = { Text("directory") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CliMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            label,
            color = Color(0xFF858585),
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            color = Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CliTerminalTip(window: CliConsoleWindow, connected: Boolean) {
    val text = when {
        !connected -> "Connect to the relay first."
        window.busy -> "Booting MCP servers and running the current prompt..."
        window.lines.isEmpty() -> "Tip: enter a prompt below. The relay will run codex exec in this directory and stream the CLI output here."
        else -> "${window.model} ${window.reasoningEffortLabel()} · ${window.cwd.directoryLabelForCli()}"
    }
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

@Composable
private fun CliConsoleLine(line: CliConsoleLine) {
    val isUser = line.role == "user"
    val isError = line.role == "stderr"
    val container = when {
        isUser -> Color.Transparent
        line.role == "status" -> Color.Transparent
        else -> Color(0xFF080808)
    }
    val content = when {
        isUser -> Color(0xFFE6E6E6)
        line.role == "status" -> Color(0xFF8B8B8B)
        isError -> MaterialTheme.colorScheme.error
        else -> Color(0xFFD8D8D8)
    }
    val prefix = when (line.role) {
        "user" -> "› "
        "stderr" -> "err "
        "status" -> "• "
        else -> "  "
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (line.role == "stdout") Color(0xFF222222) else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                prefix,
                color = content.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                line.text.ifBlank { "..." },
                color = content,
                fontFamily = if (line.role == "status") null else FontFamily.Monospace,
                fontSize = if (line.role == "status") 14.sp else 13.sp,
                lineHeight = 20.sp,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                softWrap = false,
            )
        }
    }
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
