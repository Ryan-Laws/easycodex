# EasyCodex Agent Runtime

This document describes how EasyCodex manages Codex agents.

## Mental Model

In EasyCodex, an agent is a `codex app-server` child process managed by the agent relay. The phone never launches Codex directly.

```text
Mobile app <-> Agent Relay <-> codex app-server process <-> Codex thread
```

Each running EasyCodex agent has:

- an internal EasyCodex agent id
- a Codex thread id
- a model
- a working directory
- approval policy
- service tier
- reasoning effort
- optional system prompt
- status such as `initializing`, `ready`, `working`, `error`, or `stopped`

## Relay Responsibilities

The agent relay is responsible for:

- authenticating mobile clients with the relay API key
- spawning `codex app-server`
- sending JSON-RPC messages to Codex over stdio
- translating Codex notifications into WebSocket events
- translating Codex server-initiated requests, such as approval prompts, into mobile confirmation flows
- managing in-memory running agents
- persisting enough agent metadata to restore agents after relay restart
- sending optional Expo mobile notifications for registered clients and local in-app notification events over WebSocket
- exposing file, Git, repo, worktree, model, and runtime capability actions to the app

Main files:

- `agent-relay/src/server.ts` handles HTTP, WebSocket, auth, and action routing.
- `agent-relay/src/session-orchestrator.ts` manages Codex child processes and agent state.
- `agent-relay/src/codex-rpc.ts` builds JSON-RPC requests for `codex app-server`.
- `agent-relay/src/notifier.ts` sends Expo mobile notifications only for clients that explicitly register an Expo token.

## Agent Lifecycle

1. The app sends `create_agent`.
2. The relay spawns `codex app-server` with the requested cwd.
3. The relay sends `initialize`.
4. The relay sends `initialized`.
5. The relay starts or resumes a Codex thread.
6. The app sends user messages with `send_message`.
7. The relay sends `turn/start`.
8. Codex streams notifications such as `turn/started`, `item/*`, and `turn/completed`.
9. The relay broadcasts events back to authenticated app clients.
10. Codex thread state changes also emit `codex/threads_changed`, so clients can refresh task lists and active resumable thread details immediately.

## Core WebSocket Actions

Agent actions:

| Action | Purpose |
| --- | --- |
| `create_agent` | Start a Codex agent process and thread. |
| `list_agents` | Return running relay-managed agents. |
| `get_agent` | Return one agent. |
| `send_message` | Send a user message to an agent. |
| `interrupt` | Interrupt the current agent turn. |
| `respond_agent_request` | Approve or deny a pending Codex request from the mobile app. |
| `stop_agent` | Stop an agent process. |
| `update_agent_model` | Change an agent model. |
| `update_agent_config` | Change model, cwd, approval policy, system prompt, service tier, or reasoning effort. |

Codex metadata actions:

| Action | Purpose |
| --- | --- |
| `list_codex_models` | Read model catalog from the Codex runtime. |
| `runtime_capabilities` | Report official vs compatible runtime behavior. |
| `list_codex_threads` | List available Codex threads globally by default; pass `cwd` or `includeGlobal=false` to scope results to one workspace, and `all=true` to follow cursors across pages. |
| `read_codex_thread` | Read a Codex thread and convert it to app messages. |

Live sync events:

| Event | Purpose |
| --- | --- |
| `agents/changed` | Running agent collection changed and clients should refresh the task list. |
| `codex/threads_changed` | Codex thread state changed on disk or through relay actions; clients should refresh task lists and the selected resumable thread. |
| `agent/requested` | Codex asked the client for a decision, such as approving a command or tool action. |
| `agent/request_resolved` | A pending mobile approval request was answered. |

Workspace actions:

| Action | Purpose |
| --- | --- |
| `list_files` | List files and directories under a cwd. |
| `list_directories` | List directories for cwd picking. |
| `read_file` | Read a file for the mobile file viewer. |

Git/repo actions:

| Action | Purpose |
| --- | --- |
| `git_status` | Show branch and dirty state. |
| `git_log` | Show recent commits. |
| `git_diff` | Show working tree diff. |
| `git_commit` | Commit explicitly selected files. |
| `git_branches` | List branches. |
| `git_worktrees` | List Git worktrees for the current repository, including path, display name, branch, and current/locked state. |
| `git_checkout` | Switch branches. |
| `clone_repo` | Clone a remote repository into the relay repo directory. |
| `list_repos` | List relay-managed repositories. |
| `pull_repo` | Pull a relay-managed repository. |

Sub-agent and delegated-agent events are normalized into app messages with type `sub_agent` when Codex emits `collabAgentToolCall` or known delegation tools such as `spawn_agent`, `wait_agent`, `send_input`, `resume_agent`, or `close_agent`.

Directory browsing also includes a `worktrees` collection when the selected directory is inside a Git repository, so mobile clients can surface isolated Codex workspaces instead of treating them as ordinary folders.

## Persistence

The relay writes agent metadata under the relay host's user home:

```text
~/.easycodex/agents.json
```

This is runtime state, not repository state. Do not commit relay-generated agent state.

## Security Notes

- The relay controls local files and can launch Codex inside a cwd. Treat access to it as powerful.
- Always require the relay API key for WebSocket and health checks.
- Prefer trusted LAN or Tailscale for local use.
- Prefer `wss://` behind a reverse proxy for remote/VPS use.
- Never commit relay API keys or `OPENAI_API_KEY`.

## When Changing Agent Behavior

Update this document when changing:

- WebSocket action names or payloads
- Codex JSON-RPC request shapes
- agent status values
- persistence behavior
- notification behavior
- runtime capability detection
