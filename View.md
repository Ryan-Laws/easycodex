# EasyCodex View Map

This document maps the current user-facing views in the Android app and desktop relay.

## Android App

Main entry: `mobile/app/src/main/java/com/easycodex/mobile/MainActivity.kt`

State/controller: `mobile/app/src/main/java/com/easycodex/mobile/EasyCodexController.kt`

Shared models: `mobile/app/src/main/java/com/easycodex/mobile/Models.kt`

### Startup and Connection

- `StartupMask` briefly covers first render while settings and connection state settle.
- `UsageGuideDialog` introduces the local relay, QR/deep-link pairing, project selection, task control, and notifications.
- `SettingsActivity` owns relay URL/API key setup, QR scanning, connection testing, defaults, language, theme, layout, notifications, and APK update checks.
- `EasyCodexConnectionService` keeps a foreground connection status notification available when the app is active.

### Home and Task Drawer

Implemented mainly in `ProjectPickerUi.kt`.

- `AgentDrawer` shows relay-managed agents, active resumable Codex threads, historical threads, unread/completed state, busy indicators, and task actions.
- `HomeTaskScreen` is the empty/home task surface.
- `ProjectHeader` and project rows group tasks by cwd/project.
- `AgentTaskActions` supports opening a task, starting a CLI in the task cwd, and archiving.
- `DirectoryPickerDialog` browses allowed roots, child directories, trusted paths, and Git worktrees.

### Conversation

Implemented mainly in `ConversationUi.kt` and composer pieces in `MainActivity.kt`.

- `ConversationScreen` renders user messages, agent messages, grouped detail cards, plans, command output, file changes, sub-agent activity, and queued follow-ups.
- Long text is collapsed with copy controls.
- Markdown text, fenced code, and markdown image references are rendered for mobile readability.
- File-change and command cards expose compact summaries and actions to open diff review or copy details.
- Attachment previews show sent files and image thumbnails where available.

### Composer

Main function: `MessageComposer`.

Controls:

- text prompt
- send/interrupt button
- runtime bar for model, reasoning effort, service tier, and project cwd
- plan mode toggle
- quick replies
- attachment picker for files/images
- emoji panel
- voice input panel

When attachments are selected, the controller uploads them through `upload_attachments` before sending or creating a task.

### Runtime and Creation Dialogs

- `AgentRuntimeBar` shows current model/reasoning/service tier/project and opens runtime pickers.
- `RuntimeChoiceDialog` lists model, reasoning, and service-tier choices from relay runtime metadata.
- `CreateAgentDialog` creates a new task with name, model, cwd, and reasoning effort.

### Plan, Approval, and Diff Dialogs

Implemented in `SessionDialogsUi.kt`.

- `PlanReviewDialog` appears for plan messages and allows dismiss, optimize, or start.
- Approval dialogs show Codex tool/command requests and call `respond_agent_request`.
- User-input dialogs answer Codex `request_user_input` prompts through `respond_agent_user_input`.
- `DiffReviewDialog` shows Git status, diff, file filters, single-file preview, commit message, selected files, and commit action.

### CLI Console

Implemented in `CliConsoleUi.kt`.

- Multi-window tabs (`CliWindowTabs`) allow separate mobile CLI sessions.
- `CliPromptBar` controls cwd, model, reasoning effort, sandbox mode, and skip-git-repo-check.
- Slash commands expose `exec`, `resume`, and `review` modes plus profile, image, extra-directory, JSON output, ephemeral, ignore-rules, sandbox, and Git-repo-check options.
- `CliConsoleScreen` streams `cli/output` lines and shows run status/version metadata.
- `cli_run` starts one `codex exec` per prompt; `cli_stop` stops the active run.

## Desktop Relay

Main process: `desktop-relay/src/main.cjs`

Renderer app: `desktop-relay/src/renderer-app/src/main.jsx`

Legacy renderer: `desktop-relay/src/renderer/renderer.js`

### Configuration View

The desktop relay app can:

- choose port
- preview whether a port is free or reclaimable
- stop an existing EasyCodex relay process on that port
- choose default workspace
- choose Codex executable
- generate or refresh relay API key
- show relay URL, connect URL, deep link, and QR code
- choose update channel
- install/build relay runtime when running from source

Packaged builds copy the relay runtime to `~/.easycodex/desktop-relay-runtime/` so dependencies and build output are writable outside the app bundle.

### Task Workbench

The Preact renderer has a task workbench that connects to the same relay WebSocket as the phone.

It can:

- list relay agents and active/history Codex threads
- read selected thread detail
- stream conversation updates
- show command/file/sub-agent detail cards
- show Git diff/status for the selected cwd
- start/resume tasks from selected threads
- send messages to an active agent
- answer approval prompts where exposed by relay streams

### Tray and Lightweight Mode

The desktop app can enter lightweight mode: the renderer window closes while the relay process stays alive in the tray. Closing the window asks whether to minimize/background or quit so the phone connection is not accidentally dropped.

### Updates

Desktop update behavior depends on runtime mode:

- Source checkout: can apply a fast git-based update.
- Packaged app: can find installer assets from GitHub releases and launch the matching installer after explicit confirmation.
- The Windows installer refuses to continue while EasyCodex Relay is still running.

## View Maintenance Checklist

Update this file when:

- a top-level Android screen is added/removed
- task drawer, composer, CLI, settings, or dialogs gain major behavior
- desktop relay config/workbench/tray/update flows change
- UI starts consuming new WebSocket actions or stream events
