# Changelog

## 0.1.0 - 2026-05-14

Initial public release of EasyCodex.

### Added

- Native Android mobile app for connecting to a local EasyCodex relay, starting Codex sessions, following agent activity, and reviewing conversation updates from a phone.
- Node.js agent relay for authenticated WebSocket control, workspace-aware Codex session orchestration, QR/deep-link pairing, and local health checks.
- Electron desktop relay launcher for Windows, including install/build controls, relay start/stop, workspace selection, API key management, QR pairing, and live health/status display.
- Windows desktop relay distribution with installer and portable executable builds.
- Multilingual app and desktop relay UI coverage for English, Simplified Chinese, Traditional Chinese, Japanese, Korean, Spanish, French, and German.
- Mobile handling for long command output, file diffs, and streaming agent updates so phone-sized screens stay responsive.

### Improved

- Desktop relay packaging now bundles the agent relay runtime and production dependencies for more reliable packaged launches.
- Relay health and client-status events now update the desktop UI more quickly after phones connect, disconnect, or change language.
- Mobile conversation views now summarize noisy tool output and provide expandable previews for long messages.
- Project and worktree selection flows are more resilient on small screens.
- App and desktop icons were refreshed for the first release.
