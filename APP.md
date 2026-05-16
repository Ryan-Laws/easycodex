# EasyCodex App

EasyCodex is a local-first app for controlling Codex coding agents from Android and desktop. It is not a hosted SaaS: the Agent Relay runs on the user's computer beside the codebase, and clients connect to that relay over an authenticated WebSocket.

## What Runs Where

| Piece | Location | Purpose |
| --- | --- | --- |
| Android app | `mobile/` | Native Kotlin/Jetpack Compose app for pairing, task control, chat, attachments, CLI windows, file/Git review, notifications, and settings. |
| Agent Relay | `agent-relay/` | Node.js WebSocket server that authenticates clients, validates workspaces, manages Codex processes, exposes file/Git/repo APIs, and normalizes Codex events. |
| Desktop relay | `desktop-relay/` | Electron app for starting/stopping the local relay, QR pairing, workspace/Codex path setup, updates, lightweight tray mode, and a desktop task workbench. |
| CLI/setup | `scripts/` | Terminal setup flow and npm CLI helpers. |

## First Local Run

From the repository root:

```powershell
node scripts/setup-and-start.mjs
```

Or use the desktop relay app:

```powershell
Set-Location desktop-relay
npm install
npm start
```

The setup script asks for:

- relay port, usually `3001`
- relay API key, generated automatically if blank
- whether to install dependencies

The desktop relay app provides the richer path: choose a workspace and Codex executable, preview/reclaim the port, start the relay, then scan the QR code or copy the deep link into the Android app. Packaged builds keep a writable relay runtime under `~/.easycodex/desktop-relay-runtime/`.

## Manual Run

Start the relay:

```powershell
Set-Location agent-relay
npm install
npm run dev
```

Build or run the Android app:

```powershell
Set-Location mobile
gradle assembleDebug
```

Manual app settings:

- Relay URL: `ws://<computer-ip>:3001`
- API key: the key printed by the relay or shown by the desktop relay app

## Runtime Features

The Android app currently supports:

- creating, resuming, interrupting, stopping, and archiving Codex tasks
- active/resumable/history task lists with queued follow-ups and unread completion state
- streaming agent responses, reasoning, command output, file changes, plans, and sub-agent activity
- attachment upload for files/images with mobile previews
- quick replies, emoji insertion, and Android system voice input in the composer
- mobile approval prompts and structured user-input request dialogs
- plan review with optimize/start actions
- Git status, full/single-file diff review, file preview, and selected-file commit
- project/workspace picker with trusted roots and Git worktree visibility
- multi-window Codex CLI consoles backed by `codex exec`, including exec/resume/review modes, profiles, images, extra dirs, JSON output, ephemeral runs, ignore-rules, sandbox, and Git repo check controls
- model, reasoning, service tier, cwd, approval policy, sandbox, and skip-git-repo-check controls
- relay URL/API key settings, QR scanning, language/theme/layout defaults, notification permissions/preferences/history, and stable/beta APK update checks

The desktop relay currently supports:

- relay install/build/start/stop from source or packaged runtime
- QR/deep-link pairing and API key refresh
- port preview and reclaiming an existing EasyCodex relay process
- workspace and Codex executable selection
- startup/update state, packaged installer updates, source checkout updates, and light tray mode
- a task workbench that can list agents/threads, read conversations, stream updates, send messages, answer approval prompts, and inspect Git status/diff

## Data and State

Android stores connection/default UI settings in SharedPreferences. Relay state is outside the repository:

```text
~/.easycodex/config.json
~/.easycodex/agents.json
~/.easycodex/repos/
~/.easycodex/desktop-relay.json
~/.easycodex/desktop-relay-runtime/
```

The relay also reads Codex state under `~/.codex/` to surface visible workspace roots, pinned/history threads, queued follow-ups, archived threads, and active sessions.

## Important Source Files

- `mobile/app/src/main/java/com/easycodex/mobile/MainActivity.kt` contains the top-level Compose app, composer, runtime pickers, onboarding, and dialog wiring.
- `mobile/app/src/main/java/com/easycodex/mobile/EasyCodexController.kt` owns WebSocket state, request/response handling, stream replay, task reconciliation, attachments, CLI state, notifications, and relay actions.
- `mobile/app/src/main/java/com/easycodex/mobile/ConversationUi.kt` renders chat, markdown, detail cards, plans, file changes, and attachment previews.
- `mobile/app/src/main/java/com/easycodex/mobile/ProjectPickerUi.kt` renders the task drawer, home screen, project picker, and worktree rows.
- `mobile/app/src/main/java/com/easycodex/mobile/CliConsoleUi.kt` renders mobile CLI windows.
- `mobile/app/src/main/java/com/easycodex/mobile/SessionDialogsUi.kt` renders approval, user-input, plan review, and diff review dialogs.
- `agent-relay/src/server.ts` is the WebSocket/API boundary consumed by Android and desktop clients.
- `agent-relay/src/session-orchestrator.ts` owns Codex process and thread behavior.
- `desktop-relay/src/main.cjs` owns Electron main-process relay startup, updates, tray mode, and IPC.
- `desktop-relay/src/renderer-app/src/main.jsx` owns the current desktop workbench UI.

## Verification

For relay changes:

```powershell
Set-Location agent-relay
npm run build
```

For desktop renderer changes:

```powershell
Set-Location desktop-relay
npm run build:renderer
```

For Android changes:

```powershell
Set-Location mobile
gradle assembleDebug
```

For a real smoke test, start the relay, open the Android app, verify connection status, browse a workspace/worktree, create or resume a task, send a message, and confirm stream updates arrive.
