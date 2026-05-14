package com.easycodex.mobile

data class AgentMessage(
    val role: String,
    val type: String,
    val text: String,
    val timestamp: Long,
    val itemId: String? = null,
    val streaming: Boolean = false,
    val attachments: List<AttachmentDraft> = emptyList(),
)

data class Agent(
    val id: String,
    val name: String,
    val model: String,
    val cwd: String,
    val projectRoot: String? = null,
    val status: String,
    val serviceTier: String = DEFAULT_SERVICE_TIER,
    val reasoningEffort: String = DEFAULT_REASONING_EFFORT,
    val activity: String? = null,
    val messages: List<AgentMessage> = emptyList(),
    val codexThreadId: String? = null,
    val preview: String? = null,
    val resumable: Boolean = false,
    val pinned: Boolean = false,
    val updatedAt: Long = 0,
    val queuedFollowUps: List<QueuedFollowUp> = emptyList(),
)

data class QueuedFollowUp(
    val id: String,
    val text: String,
    val cwd: String,
    val createdAt: Long,
    val pausedReason: String? = null,
)

enum class AgentAlertKind {
    Completed,
    Question,
    Confirmation,
    Error,
}

data class AgentAlert(
    val id: String,
    val agentId: String,
    val agentName: String,
    val kind: AgentAlertKind,
    val title: String,
    val detail: String,
    val timestamp: Long,
)

data class AgentApprovalRequest(
    val id: String,
    val agentId: String,
    val method: String,
    val title: String,
    val detail: String,
    val timestamp: Long,
)

data class AgentUserInputOption(
    val label: String,
    val description: String,
)

data class AgentUserInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean,
    val isSecret: Boolean,
    val options: List<AgentUserInputOption>,
)

data class AgentUserInputRequest(
    val id: String,
    val agentId: String,
    val title: String,
    val detail: String,
    val questions: List<AgentUserInputQuestion>,
    val timestamp: Long,
)

data class PlanReview(
    val agentId: String,
    val message: AgentMessage,
)

data class RuntimeCapabilities(
    val providerMode: String = "official",
    val supportsServiceTier: Boolean = true,
    val supportsReasoningEffort: Boolean = true,
    val reason: String = "official Codex runtime",
)

data class CodexModelOption(
    val model: String,
    val displayName: String,
    val defaultReasoningEffort: String = DEFAULT_REASONING_EFFORT,
    val supportedReasoningEfforts: List<String> = emptyList(),
    val additionalSpeedTiers: List<String> = emptyList(),
    val isDefault: Boolean = false,
)

data class DirectoryOption(
    val name: String,
    val path: String,
)

data class WorktreeOption(
    val name: String,
    val path: String,
    val branch: String? = null,
    val current: Boolean = false,
    val locked: Boolean = false,
)

data class DirectoryListing(
    val path: String,
    val parent: String?,
    val roots: List<DirectoryOption>,
    val worktrees: List<WorktreeOption> = emptyList(),
    val entries: List<DirectoryOption>,
)

data class CliConsoleLine(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val streaming: Boolean = false,
)

data class CliConsoleWindow(
    val id: String,
    val title: String = "CLI",
    val cwd: String = DEFAULT_AGENT_CWD,
    val model: String = DEFAULT_AGENT_MODEL,
    val reasoningEffort: String = DEFAULT_REASONING_EFFORT,
    val version: String = "",
    val running: Boolean = false,
    val busy: Boolean = false,
    val runId: String? = null,
    val input: String = "",
    val lines: List<CliConsoleLine> = emptyList(),
)

data class CliConsoleState(
    val activeWindowId: String = "cli_1",
    val windows: List<CliConsoleWindow> = listOf(CliConsoleWindow(id = "cli_1", title = "CLI 1")),
) {
    val activeWindow: CliConsoleWindow
        get() = windows.firstOrNull { it.id == activeWindowId } ?: windows.first()
}

data class AttachmentDraft(
    val name: String,
    val path: String,
    val mimeType: String? = null,
    val previewUri: String? = null,
)

data class GitStatusSummary(
    val branch: String = "",
    val isClean: Boolean = true,
    val files: List<String> = emptyList(),
)

data class FileEntry(
    val name: String,
    val path: String,
    val type: String,
)

data class FileListing(
    val cwd: String,
    val path: String,
    val entries: List<FileEntry>,
)

sealed class RelayResult<out T> {
    data class Success<T>(val value: T) : RelayResult<T>()
    data class Failure(val message: String) : RelayResult<Nothing>()
}

data class DiffReviewState(
    val agentId: String,
    val cwd: String,
    val requestId: Int,
    val loading: Boolean = true,
    val error: String? = null,
    val status: GitStatusSummary? = null,
    val selectedFile: String? = null,
    val diff: String = "",
    val files: List<FileEntry> = emptyList(),
    val fileContent: String = "",
    val fileLoading: Boolean = false,
)

data class GitCommitDraft(
    val message: String = "",
    val files: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

data class NotificationLevelState(
    val agentId: String,
    val level: String = "all",
    val loading: Boolean = false,
    val error: String? = null,
)

data class LongTextDisplayState(
    val expanded: Boolean = false,
    val copiedAt: Long? = null,
)

data class PendingStreamDelta(
    val agentId: String,
    val itemId: String,
    var type: String,
    val text: StringBuilder = StringBuilder(),
)

data class FileChangeLiveStat(
    var additions: Int = 0,
    var deletions: Int = 0,
)

data class CommandOutputLiveStat(
    var lines: Int = 0,
    var chars: Int = 0,
    var lastText: String = "",
)
