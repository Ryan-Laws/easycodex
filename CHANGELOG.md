# Changelog

## Unreleased

- Standardized desktop relay updates so packaged installs require a quit-and-update confirmation, stop the local relay first, and block the Windows installer if the relay app is still running.
- Changed Android APK update checks to require user confirmation before downloading and to warn that Android may close the app during system installation.

## 0.1.1 - 2026-05-14

### Fixed

- Reused an already-running relay agent when resuming the same Codex thread, preventing duplicate `codex app-server` processes for one thread.
- Forwarded token-usage updates as live relay events instead of adding noisy status messages to mobile conversations.
- Hardened Android connection import and relay endpoint validation so QR/deep-link pairing rejects invalid relay URLs and public cleartext WebSocket hosts.
- Removed a nullable active-agent crash risk from the Android top bar during fast agent state changes.
- Added Android JVM tests for connection parsing and relay endpoint safety checks.

### Improved

- Expanded desktop relay workspace discovery to include Codex Desktop visible workspace roots.
- Improved mobile relay summaries for plans, command activity, file changes, and pending agent requests.
- Refined the desktop relay window sizing and visual shell for the updated relay experience.
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
