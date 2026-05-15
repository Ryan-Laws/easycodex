# Changelog

## Unreleased

## 0.1.3 - 2026-05-15

### Added

- Added the refreshed desktop relay workbench with a Vite/Preact renderer, task history, live conversation controls, and file-change context.
- Added Android attachment limit coverage and updated Android backup/data extraction configuration.

### Fixed

- Improved relay task synchronization, Codex RPC handling, and session orchestration for mobile task refresh and command flow reliability.
- Tightened Android connection, conversation, project picker, CLI console, and settings behavior for phone-sized workflows.

### Improved

- Expanded English and Chinese project documentation for the current Android app, desktop relay, and agent relay behavior.

## 0.1.2 - 2026-05-15

### Added

- Added phone-accessible Codex CLI consoles for launching separate one-shot `codex exec` prompts in selected workspaces.

### Fixed

- Separated active mobile tasks from resumable Codex history so completed `notLoaded` threads no longer remain in project task counts.
- Used Codex session lifecycle events and archived thread state when syncing tasks from the relay.
- Refreshed Android task status merging so completed threads stop showing stale working indicators.

### Improved

- Expanded the desktop relay workbench with richer task history, live conversation controls, file-change context, and update handling.
- Tightened mobile conversation behavior around auto-scroll, file-change summaries, queued task taps, and connection reliability.

## 0.1.2-beta.2 - 2026-05-15

### Fixed

- Separated active mobile tasks from resumable Codex history so completed `notLoaded` threads no longer remain in project task counts.
- Used Codex session lifecycle events and archived thread state when syncing tasks from the relay.
- Refreshed Android task status merging so completed threads stop showing stale working indicators.

### Improved

- Tightened mobile conversation behavior around auto-scroll, file-change summaries, and queued task taps.

## 0.1.2-beta.1 - 2026-05-14

- Standardized desktop relay updates so packaged installs require a quit-and-update confirmation, stop the local relay first, and block the Windows installer if the relay app is still running.
- Changed Android APK update checks to require user confirmation before downloading and to warn that Android may close the app during system installation.

### Fixed

- Reused an already-running relay agent when resuming the same Codex thread, preventing duplicate `codex app-server` processes for one thread.
- Forwarded token-usage updates as live relay events instead of adding noisy status messages to mobile conversations.
- Added timeout and retry handling around GitHub update checks and desktop relay update downloads so unstable network links recover more reliably.

### Improved

- Added beta update channels to Android, the desktop relay, and the relay update API so prerelease builds can be tested without replacing stable release assets.
- Expanded desktop relay workspace discovery to include Codex Desktop visible workspace roots.
- Improved mobile relay summaries for plans, command activity, file changes, and pending agent requests.
- Added an Android foreground connection service to keep relay sessions alive more reliably while testing from a phone.
- Refined the desktop relay workbench with task history, live conversation controls, file-change context, and a larger desktop shell.

## 0.1.1 - 2026-05-14

### Fixed

- Hardened Android connection import and relay endpoint validation so QR/deep-link pairing rejects invalid relay URLs and public cleartext WebSocket hosts.
- Removed a nullable active-agent crash risk from the Android top bar during fast agent state changes.
- Added Android JVM tests for connection parsing and relay endpoint safety checks.

### Improved

- Refined the Android chat surface with a lighter status pill, more polished composer, cleaner message hierarchy, and a more compact first-run guide.
- Polished Android home, project, attachment, and settings entry surfaces for a more cohesive mobile app feel.
- Revalidated Android release signing, install, launch smoke, and fatal-log checks on an emulator.

## 0.1.0 - 2026-05-14

Initial public release of EasyCodex.

### Added

- Native Android mobile app for connecting to a local EasyCodex relay, starting Codex sessions, following agent activity, and reviewing conversation updates from a phone.
- Node.js agent relay for authenticated WebSocket control, workspace-aware Codex session orchestration, QR/deep-link pairing, and local health checks.
- Electron desktop relay launcher for Windows, including install/build controls, relay start/stop, workspace selection, API key management, QR pairing, and live health/status display.
- Windows desktop relay distribution with installer and portable executable builds.
- macOS Intel, macOS Apple Silicon, and Linux x64 desktop relay release builds.
- Multilingual app and desktop relay UI coverage for English, Simplified Chinese, Traditional Chinese, Japanese, Korean, Spanish, French, and German.
- Mobile handling for long command output, file diffs, and streaming agent updates so phone-sized screens stay responsive.

### Improved

- Desktop relay packaging now bundles the agent relay runtime and production dependencies for more reliable packaged launches.
- Relay health and client-status events now update the desktop UI more quickly after phones connect, disconnect, or change language.
- Mobile conversation views now summarize noisy tool output and provide expandable previews for long messages.
- Project and worktree selection flows are more resilient on small screens.
- App and desktop icons were refreshed for the first release.
- Windows relay installer icon metadata, system-wide install location, high-DPI awareness, and installer language selection were corrected.
