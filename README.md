<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex logo" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>A local-first mobile and desktop control room for Codex agents.</strong></p>
  <p>
    <a href="README.md">English</a> |
    <a href="README.zh-CN.md">简体中文</a> |
    <a href="README.zh-TW.md">繁體中文</a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Ryan-Laws/easycodex?label=release&style=flat-square"></a>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-green?style=flat-square"></a>
    <img alt="Windows" src="https://img.shields.io/badge/Windows-relay%20app-0078D4?logo=windows&logoColor=white&style=flat-square">
    <img alt="Android" src="https://img.shields.io/badge/Android-mobile%20APK-3DDC84?logo=android&logoColor=white&style=flat-square">
    <img alt="macOS" src="https://img.shields.io/badge/macOS-relay%20app-000000?logo=apple&logoColor=white&style=flat-square">
    <img alt="Linux-relay" src="https://img.shields.io/badge/Linux-relay%20app-FCC624?logo=linux&logoColor=black&style=flat-square">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white&style=flat-square">
    <img alt="Electron" src="https://img.shields.io/badge/Electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square">
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/latest"><strong>Download the latest EasyCodex release</strong></a>
    ·
    <a href="https://github.com/Ryan-Laws/easycodex/releases">All releases</a>
  </p>
</div>

EasyCodex lets you control local Codex work from a desktop relay app and an Android phone. The relay runs on your computer beside your repositories, starts and supervises `codex app-server`, and exposes a secure QR/deep-link pairing flow so the phone can follow and steer the same work without moving source code or credentials to a hosted service.

The current app is a full control room: create and resume Codex sessions, stream conversation updates, answer approvals and user-input prompts, send attachments, review plans and diffs, commit selected files, browse projects and worktrees, archive tasks, run multi-window Codex CLI consoles, receive notifications, and keep task lists synced as Codex thread state changes.

## Install

Download the current release from the [latest EasyCodex release](https://github.com/Ryan-Laws/easycodex/releases/latest).

| Platform | Download | What it is for |
| --- | --- | --- |
| Windows | `EasyCodex.Relay.Setup.*-x64.exe` | Recommended Windows desktop relay installer |
| Windows | `EasyCodex.Relay.Portable.*-x64.exe` | Portable Windows relay app |
| Android | `EasyCodex.Mobile.*.apk` | Android phone app |
| macOS Apple Silicon | `EasyCodex.Relay.*.mac-arm64.dmg` | Apple Silicon desktop relay |
| macOS Intel | `EasyCodex.Relay.*.mac-x64.dmg` | Intel desktop relay |
| Linux | `EasyCodex.Relay.*.linux-x64.AppImage` | Portable Linux relay app |
| Linux | `EasyCodex.Relay.*.linux-x64.deb` | Debian/Ubuntu relay package |

## Quick Start

1. Install the Codex CLI on your computer and make sure it is authenticated.
2. Install and open **EasyCodex Relay** on your desktop.
3. Choose a default workspace, confirm the port/API key, and start the relay.
4. Install `EasyCodex.Mobile.*.apk` on your Android phone.
5. Scan the QR code or open the `easycodex://connect` deep link.
6. Pick a workspace or worktree, then create or resume a Codex task.

The phone does not run Codex. It connects to the relay over an authenticated WebSocket. The relay launches `codex app-server` for agent sessions and `codex exec` for mobile CLI windows.

## What You Can Do

- Start, resume, interrupt, stop, and archive Codex tasks.
- See running agents, active Codex threads, historical threads, queued follow-ups, and unread completed work.
- Send text, images, and files from the phone; attachments are stored under the selected workspace in `.easycodex-attachments/`.
- Review plans before execution and ask Codex to optimize them.
- Inspect Git status/diff, preview changed files, and commit selected files.
- Browse allowed workspaces, trusted directories, relay-managed repos, and Git worktrees.
- Answer Codex approval prompts and structured user-input prompts.
- Open multiple mobile CLI windows backed by separate `codex exec` runs.
- Choose model, reasoning effort, service tier, cwd, sandbox mode, and update channel where supported.
- Receive local app notifications and optional mobile push notifications.
- Use the desktop relay workbench to monitor tasks without opening the phone.

## Architecture

```text
Android app / Desktop workbench
        <-> Agent Relay
        <-> codex app-server / codex exec
        <-> Codex thread state
```

The relay authenticates clients with a local API key, validates workspace paths, starts Codex processes, translates Codex JSON-RPC events into stable app messages, and exposes explicit file/Git/workspace actions.

## Requirements

- A desktop computer for the relay
- An Android phone or emulator for the mobile app
- Codex CLI installed and authenticated on the desktop computer
- Phone and computer on the same trusted network, or connected through a private network such as Tailscale

## Build From Source

Most users should install from the release page. Use these commands when developing EasyCodex itself.

```powershell
# Desktop relay
Set-Location desktop-relay
npm install
npm start

# Agent relay
Set-Location agent-relay
npm install
npm run build

# Android app
Set-Location mobile
gradle assembleDebug
```

## Repository Layout

```text
EasyCodex/
├── mobile/          Native Android app
├── agent-relay/     Node.js Agent Relay for Codex
├── desktop-relay/   Electron desktop relay app
└── scripts/         CLI and local setup helpers
```

## Security Model

- The relay requires an API key for WebSocket clients and health checks.
- Treat relay access as powerful: it can read workspace files, inspect Git state, commit selected files, and launch Codex in a working directory.
- Workspace access is limited to known/trusted roots; the relay refuses obvious system/profile/application-data roots.
- Prefer a trusted LAN or private network for day-to-day use.
- Never commit API keys, relay keys, OpenAI tokens, local agent state, or private environment files.

## Documentation

- [文档.md](文档.md) is the Chinese project overview.
- [View.md](View.md) maps Android and desktop user-facing views.
- [AGENT.md](AGENT.md) describes relay-managed Codex agents, WebSocket actions, stream events, and runtime behavior.
- [APP.md](APP.md) explains the app architecture and local runtime model.
- [RELEASE.md](RELEASE.md) documents CI/CD release builds, signing, and smoke tests.
- [desktop-relay/README.md](desktop-relay/README.md) covers desktop relay packaging.

## License

EasyCodex is released under the [MIT License](LICENSE).
