# EasyCodex Agent Runtime

This document describes the current relay-managed Codex runtime used by the Android app and the desktop relay workbench.

## Mental Model

EasyCodex is a local-first controller. The phone and desktop UI never run Codex directly; they talk to the Agent Relay, and the relay launches local Codex processes beside the selected workspace.

```text
Android app / Desktop workbench
        <-> authenticated WebSocket
Agent Relay
        <-> JSON-RPC over stdio
codex app-server
        <-> Codex thread state
```

The relay also exposes mobile Codex CLI consoles. Each CLI window runs a separate `codex exec` process in an allowed workspace and streams stdout/stderr back as `cli/*` events.

Each running agent has:

- an EasyCodex agent id
- a Codex thread id
- model, reasoning effort, service tier, cwd, approval policy, and optional system prompt
- status: `initializing`, `ready`, `working`, `error`, or `stopped`
- normalized messages for agent text, reasoning, commands, command output, file changes, plans, and delegated sub-agent activity
- pending approval or user-input requests when Codex asks for a client decision
- queued mobile follow-up turns when a message arrives while the agent is busy

## Relay Responsibilities

Main files:

- `agent-relay/src/server.ts` handles HTTP, WebSocket auth, action routing, workspace safety, Git/file APIs, attachment upload, CLI processes, stream replay, update checks, and desktop relay events.
- `agent-relay/src/session-orchestrator.ts` manages `codex app-server`, agent state, Codex thread listing/reading/archiving, stream normalization, message truncation, turn queues, user-input requests, and runtime capability detection.
- `agent-relay/src/codex-rpc.ts` defines Codex JSON-RPC calls and turn input item shapes, including text, image URLs, and local images.
- `agent-relay/src/notifier.ts` stores notification preferences/history and sends optional Expo push notifications.
- `agent-relay/src/updater.ts` checks and applies EasyCodex update channels.

The relay must keep these boundaries explicit:

- WebSocket and health checks require the relay API key.
- File, Git, CLI, and agent cwd operations must resolve inside allowed EasyCodex workspace roots.
- New arbitrary workspace roots require `trust_workspace_root`; system/profile/application-data roots are refused.
- Uploaded attachments are written under the selected cwd in `.easycodex-attachments/`.
- Relay state belongs under `~/.easycodex/`, not in the repository.

## Agent Lifecycle

1. A client sends `create_agent`, optionally with a first message and attachments.
2. The relay validates cwd against allowed workspace roots.
3. The relay starts `codex app-server` in that cwd.
4. The relay sends `initialize` and `initialized`.
5. The relay starts a new Codex thread or resumes an existing thread.
6. A client sends `send_message`.
7. The relay sends `turn/start` with text and optional attachment input items.
8. Codex streams notifications such as `turn/started`, `item/*`, `turn/plan/updated`, `turn/diff/updated`, and `turn/completed`.
9. The relay normalizes those events into mobile/desktop-friendly messages and broadcasts stream envelopes.
10. `agents/changed` and `codex/threads_changed` tell clients to refresh task lists and selected thread details.

Only one running relay agent is attached to a Codex thread id. If a client resumes a thread that is already active, the relay returns the existing agent.

EasyCodex has two task modes that must stay distinct:

- Desktop handoff resumes an existing Codex thread. The desktop Codex App remains the primary UI, and mobile messages are follow-ups on that thread.
- Mobile-originated tasks are relay-managed `codex app-server` sessions. They may appear in the desktop Codex App through shared Codex state, but the desktop UI is only a secondary view and may not show full app-server or sub-agent details.

For mobile-originated tasks, keep sub-agent results visible in the relay/mobile transcript when Codex provides them. Summarize or truncate only for size, and do not replace a returned sub-agent result with a generic "details omitted" placeholder.

## WebSocket Actions

Agent and message actions:

| Action | Purpose |
| --- | --- |
| `create_agent` | Start or resume an agent. Supports `name`, `model`, `cwd`, `permissionMode`, `systemPrompt`, `serviceTier`, `reasoningEffort`, `codexThreadId`, first `message`, and `attachments`. |
| `list_agents` | Return relay-managed running agents. |
| `get_agent` | Return one running agent. |
| `send_message` | Send a turn to an agent, or queue it while busy. Supports attachments. |
| `interrupt` | Interrupt the current turn. |
| `respond_agent_request` | Approve or deny a Codex approval/tool request. |
| `respond_agent_user_input` | Answer a Codex user-input request produced by `request_user_input`; payload `answers` is keyed by question id and relay formats each value as `{ answers: string[] }` for Codex. |
| `stop_agent` | Stop a running agent process. |
| `archive_codex_thread` | Archive a Codex thread. If a relay-managed agent is still running for that thread, the relay first marks it stopped, kills the process, removes it from local state, then sends Codex `thread/archive`. |
| `update_agent_model` | Change the in-memory model for an agent. |
| `update_agent_config` | Change model, cwd, permission mode, system prompt, service tier, or reasoning effort. |

Stream and runtime actions:

| Action | Purpose |
| --- | --- |
| `replay_stream` | Replay retained stream envelopes after reconnect from a session id and sequence number. |
| `list_codex_threads` | List Codex threads; supports cursoring, `all`, cwd scoping, global inclusion, and `activeOnly`. |
| `read_codex_thread` | Read a thread and convert it to app messages. |
| `list_codex_models` | Read the Codex runtime model catalog. |
| `runtime_capabilities` | Report official vs compatible runtime behavior and supported model knobs. |
| `check_update` | Check the configured EasyCodex update channel. |
| `apply_update` | Apply a git-based relay update when running from a checkout. |

CLI actions:

| Action | Purpose |
| --- | --- |
| `cli_start` | Prepare a CLI window and return Codex version/runtime metadata. |
| `cli_run` | Run one `codex exec` command for a window. Supports cwd, model, reasoning effort, sandbox mode, skip-git-repo-check, `mode` (`exec`, `resume`, `review`), `sessionId`, `reviewTarget`, `profile`, `images`, `addDirs`, `jsonOutput`, `ephemeral`, and `ignoreRules`. |
| `cli_stop` | Stop the active CLI run for a window. |

Workspace and attachment actions:

| Action | Purpose |
| --- | --- |
| `browse_directories` | Browse allowed roots, child directories, and Git worktrees for a directory picker. |
| `trust_workspace_root` | Add a validated custom workspace root for this relay process. |
| `list_files` | List files/directories under a cwd-relative path. |
| `list_directories` | List only directories under a cwd-relative path. |
| `read_file` | Read one text file under a cwd. |
| `upload_attachments` | Upload up to 12 files, each up to 12 MB, into `.easycodex-attachments/`. |

Git and repository actions:

| Action | Purpose |
| --- | --- |
| `git_status` | Return branch, clean state, and changed files. |
| `git_log` | Return recent commits. |
| `git_diff` | Return full or single-file diff. |
| `git_commit` | Stage and commit explicitly selected files. |
| `git_branches` | List local branches. |
| `git_worktrees` | List worktrees for a repository. |
| `git_checkout` | Switch branch. |
| `clone_repo` | Clone into the relay repo directory. |
| `list_repos` | List relay-managed repos. |
| `pull_repo` | Pull a relay-managed repo. |

Notification actions:

| Action | Purpose |
| --- | --- |
| `register_notification_token` | Register an Expo push token for the authenticated client. |
| `update_client_language` | Sync client language for localized notifications. |
| `update_notification_prefs` | Store per-agent notification level: `all`, `errors`, or `muted`. |
| `get_notification_prefs` | Return notification preferences. |
| `list_notification_history` | Return recent relay notification history. |

## Stream Events

The relay sends response envelopes for requests and stream envelopes for live state. Important stream events include:

- `agents/changed`
- `codex/threads_changed`
- `relay/update_available`
- `relay/update_applied`
- `agent/requested`
- `agent/request_resolved`
- `agent/user_input_requested`
- `agent/user_input_resolved`
- `turn/started`, `turn/completed`, `turn/failed`
- `item/started`, `item/completed`
- `item/agentMessage/delta`
- `item/reasoning/delta`
- `item/commandOutput/delta`
- `item/fileChange/delta`
- `cli/started`, `cli/output`, `cli/completed`, `cli/error`, `cli/stopped`

Long stream deltas are batched and capped for mobile. Long historical messages are summarized or truncated with an EasyCodex notice so the phone stays responsive.

## Thread and Message Normalization

The orchestrator maps old and current Codex event names into stable app message types:

- `agent` for assistant text
- `user` for user prompts
- `thinking` for reasoning summaries/deltas
- `command` for shell or MCP call start
- `command_output` for command/MCP output
- `file_change` for patches, diffs, and code changes
- `plan` for plan updates
- `sub_agent` for delegated agent activity. It is a compact navigation/status row in clients, not a normal chat bubble; full delegated work belongs in the sub-agent thread when a thread id is available.
- `status` for lifecycle/error notices

Command and sub-agent items are summarized for mobile by default. The relay strips shell wrappers, ANSI control codes, unsafe internal tool arguments, and repeated empty success output before sending display text; detail text may still be attached for explicit expansion.

When a user prompt contains injected AGENTS/environment context, the relay and Android app hide that context in display copies and keep the user-facing prompt readable.

## Attachments

Mobile attachments are uploaded before a turn. The relay:

- accepts at most 12 files per upload
- rejects files over 12 MB
- sanitizes file names
- writes files under `.easycodex-attachments/` in the selected cwd
- sends images to Codex as local image input where possible
- includes non-image files in the prompt as local file references

The Android app keeps preview metadata locally so sent messages can show attachment chips and image previews.

## Persistence

Runtime state is outside the repo:

```text
~/.easycodex/config.json
~/.easycodex/agents.json
~/.easycodex/repos/
~/.easycodex/desktop-relay.json
~/.easycodex/desktop-relay-runtime/
```

The relay also reads Codex desktop/global state from `~/.codex/` when available to surface pinned threads, visible workspace roots, queued follow-ups, archived threads, and active sessions.

Permission modes are stored with relay agents and mapped to Codex app-server fields:

| `permissionMode` | Codex `sandbox` | Codex `approvalPolicy` | Codex `approvalsReviewer` | Behavior |
| --- | --- | --- | --- | --- |
| `default-review` | `workspace-write` | `on-request` | `user` | Work inside the workspace normally; phone approval handles sandbox escapes, blocked network, MCP approvals, and similar permission requests. |
| `auto-review` | `workspace-write` | `on-request` | `auto_review` | Same sandbox boundary, but Codex routes permission decisions to its auto-reviewer before interrupting the user. |
| `full-access` | `danger-full-access` | `never` | unset | Full access; EasyCodex does not surface permission approvals because Codex should not ask for them in this mode. |

## Environment Variables

Common relay variables:

- `PORT`
- `API_KEY`
- `CODEX_CWD`
- `CODEX_EXECUTABLE` or `EASY_CODEX_CODEX_PATH`
- `REPOS_DIR`
- `AUTO_PULL_REPOS`
- `EASYCODEX_UPDATE_CHANNEL`
- `EASYCODEX_UPDATE_REPO`
- `EASYCODEX_LOG_CONNECT_SECRETS`
- `EASYCODEX_NO_TERMINAL_QR`
- `EASY_CODEX_COMPATIBLE_API`
- `EASY_CODEX_MOBILE_MESSAGE_TEXT_LIMIT`
- `EASY_CODEX_MOBILE_DETAIL_TEXT_LIMIT`
- `EASY_CODEX_MOBILE_STREAM_TEXT_LIMIT`
- `EASY_CODEX_STREAM_BATCH_FLUSH_MS`
- `EASY_CODEX_STREAM_BATCH_MAX_CHARS`

## When Changing Agent Behavior

Update this document when changing:

- WebSocket action names, payloads, or stream events
- Codex JSON-RPC request shapes
- agent status values or message type normalization
- workspace safety rules
- attachment limits or turn input mapping
- persistence behavior
- notification behavior
- runtime capability detection
- desktop relay startup/update behavior that affects the relay contract
