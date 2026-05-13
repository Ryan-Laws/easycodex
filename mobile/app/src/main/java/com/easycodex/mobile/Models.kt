package com.easycodex.mobile

data class AgentMessage(
    val role: String,
    val type: String,
    val text: String,
    val timestamp: Long,
    val itemId: String? = null,
    val streaming: Boolean = false,
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
    val updatedAt: Long = 0,
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

data class AttachmentDraft(
    val name: String,
    val path: String,
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
