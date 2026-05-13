<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex logo" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>Run Codex at your desk. Control it from your Android phone.</strong></p>
  <p>
    <a href="README.md">English</a> |
    <a href="README.zh-CN.md">简体中文</a> |
    <a href="README.zh-TW.md">繁體中文</a>
  </p>
  <p>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/Ryan-Laws/easycodex?style=flat-square"></a>
    <a href="https://github.com/Ryan-Laws/easycodex/releases"><img alt="Release" src="https://img.shields.io/github/v/release/Ryan-Laws/easycodex?include_prereleases&style=flat-square"></a>
    <a href="https://github.com/Ryan-Laws/easycodex/actions/workflows/release-desktop-relay.yml"><img alt="Desktop Relay workflow" src="https://img.shields.io/github/actions/workflow/status/Ryan-Laws/easycodex/release-desktop-relay.yml?branch=main&label=desktop%20relay&style=flat-square"></a>
    <a href="package.json"><img alt="Node.js >=18" src="https://img.shields.io/badge/node-%3E%3D18-339933?logo=node.js&logoColor=white&style=flat-square"></a>
    <a href="mobile/app/build.gradle.kts"><img alt="Android API 26+" src="https://img.shields.io/badge/android-API%2026%2B-3DDC84?logo=android&logoColor=white&style=flat-square"></a>
    <a href="mobile/"><img alt="Kotlin" src="https://img.shields.io/badge/kotlin-native%20android-7F52FF?logo=kotlin&logoColor=white&style=flat-square"></a>
    <a href="mobile/app/build.gradle.kts"><img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square"></a>
    <a href="agent-relay/"><img alt="TypeScript" src="https://img.shields.io/badge/typescript-relay-3178C6?logo=typescript&logoColor=white&style=flat-square"></a>
    <a href="desktop-relay/"><img alt="Electron" src="https://img.shields.io/badge/electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square"></a>
    <a href="https://visitor-badge.laobi.icu/"><img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex"></a>
  </p>
</div>

EasyCodex turns an Android phone into a remote control surface for Codex coding agents. The Codex runtime and the relay stay on your own computer beside your codebase, while the phone gives you a native mobile interface for starting sessions, chatting with agents, following long-running work, reviewing files and diffs, and responding to approval prompts.

It is built for people who want Codex to keep working locally without being chained to the terminal window.

## Project Stats

| Metric | Badge |
| --- | --- |
| Visitors | ![Visitors](https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex) |
| Stars | ![GitHub stars](https://img.shields.io/github/stars/Ryan-Laws/easycodex?style=social) |
| Forks | ![GitHub forks](https://img.shields.io/github/forks/Ryan-Laws/easycodex?style=social) |
| Latest release | ![GitHub release](https://img.shields.io/github/v/release/Ryan-Laws/easycodex?include_prereleases) |
| License | ![GitHub license](https://img.shields.io/github/license/Ryan-Laws/easycodex) |
| Desktop release workflow | ![GitHub Actions workflow status](https://img.shields.io/github/actions/workflow/status/Ryan-Laws/easycodex/release-desktop-relay.yml?branch=main) |

## Why EasyCodex

- Keep Codex agents running on your computer while controlling them from your phone.
- Follow streaming responses and long-running coding tasks without staying at your desk.
- Manage multiple agents, Codex threads, working directories, models, and approval settings from a native Android UI.
- Inspect workspace files, diffs, branches, and Git worktrees before deciding what to do next.
- Keep sensitive execution local: the relay runs beside your repositories and requires an API key from clients.

## Features

- Create, resume, interrupt, and stop multiple Codex agent sessions.
- Stream agent responses in real time from `codex app-server`.
- Queue messages while an agent is busy.
- Respond to mobile approval prompts for Codex-requested actions.
- Browse files, read workspace content, view Git diffs, inspect branches, and work with Git worktrees.
- Connect by scanning a QR code or opening an `easycodex://connect` deep link.
- Receive local relay events and optional mobile notifications when work finishes.
- Launch and monitor the local relay from an Electron desktop app for Windows and macOS.
- Use terminal-first setup scripts when you prefer a CLI workflow.

## Tech Stack

| Area | Technology |
| --- | --- |
| Android app | Kotlin, native Android, Jetpack Compose, Material 3, OkHttp, Google Code Scanner |
| Agent relay | Node.js 18+, TypeScript, Express, `ws`, `simple-git`, Codex `app-server` JSON-RPC |
| Desktop relay | Electron, electron-builder, local QR code connection UI |
| CLI and setup | Node.js ESM scripts with PowerShell-friendly commands |

## Architecture

```text
Android app <-> Agent Relay <-> codex app-server <-> Codex thread
```

The phone never launches Codex directly. It connects to the local Agent Relay over WebSocket using a relay API key. The relay authenticates clients, starts and supervises `codex app-server`, translates JSON-RPC events, and exposes explicit file, Git, repo, model, and runtime actions to the app.

## Quick Start

### 1. Use the desktop relay app

```powershell
Set-Location desktop-relay
npm install
npm start
```

The desktop relay gives you a local control window with relay status, connection details, and a QR code for pairing your phone.

### 2. Use the terminal setup flow

From the repository root:

```powershell
node scripts/setup-and-start.mjs
```

The script asks for the relay port, generates an API key if you leave it blank, optionally installs dependencies, starts the relay, and prints the QR connection details.

### 3. Run everything manually

Start the Agent Relay:

```powershell
Set-Location agent-relay
npm install
npm run dev
```

Build or run the Android app from Android Studio. If Gradle and the Android SDK are on `PATH`, you can also run:

```powershell
Set-Location mobile
gradle assembleDebug
```

Scan the QR code printed by the relay with your phone camera. Android opens EasyCodex and saves the WebSocket URL and API key automatically.

## Requirements

- Node.js 18 or newer
- OpenAI Codex CLI installed and authenticated
- Android device or emulator
- Computer and phone on the same trusted network, or connected through a private network such as Tailscale
- Android Studio or a configured Android SDK/Gradle environment for building the mobile app

## Repository Layout

```text
EasyCodex/
├── mobile/          Native Android app
├── agent-relay/     Node.js Agent Relay for Codex
├── desktop-relay/   Electron Windows/macOS relay desktop app
└── scripts/         CLI and local setup helpers
```

## Useful Commands

```powershell
# Agent Relay
Set-Location agent-relay
npm run build
npm run dev

# Desktop relay app
Set-Location desktop-relay
npm start
npm run dist:win

# Android app
Set-Location mobile
gradle assembleDebug
```

## Security Model

- The relay requires an API key for WebSocket clients and health checks.
- Treat relay access as powerful: it can read workspace files, inspect Git state, and launch Codex in a working directory.
- Prefer a trusted LAN or private network for day-to-day use.
- Use `wss://` behind a properly configured reverse proxy if you expose the relay beyond a private network.
- Never commit API keys, relay keys, OpenAI tokens, or private environment files.

## Documentation

- [APP.md](APP.md) explains the app architecture and local runtime model.
- [AGENT.md](AGENT.md) describes relay-managed Codex agents, WebSocket actions, and runtime behavior.
- [desktop-relay/README.md](desktop-relay/README.md) covers packaging and release builds for the desktop relay.

## Contributing

Contributions are welcome. Keep changes focused, follow the existing project structure, and avoid introducing dependencies unless they clearly improve the feature or maintenance story.

Before opening a pull request, run the most relevant checks for the area you touched:

```powershell
# Relay
Set-Location agent-relay
npm run build

# Android
Set-Location mobile
gradle assembleDebug

# Desktop relay
Set-Location desktop-relay
npm run dist:win
```

For changes to relay-agent behavior or WebSocket actions, update [AGENT.md](AGENT.md). For app architecture changes, update [APP.md](APP.md).

## License

EasyCodex is released under the [MIT License](LICENSE).
