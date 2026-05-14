<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex logo" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>Start Codex on your computer. Control it from your Android phone.</strong></p>
  <p>
    <a href="README.md">English</a> |
    <a href="README.zh-CN.md">简体中文</a> |
    <a href="README.zh-TW.md">繁體中文</a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.1"><img alt="Release v0.1.1" src="https://img.shields.io/badge/release-v0.1.1-2f80ed?style=flat-square"></a>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-green?style=flat-square"></a>
    <img alt="Windows" src="https://img.shields.io/badge/Windows-relay%20app-0078D4?logo=windows&logoColor=white&style=flat-square">
    <img alt="Android" src="https://img.shields.io/badge/Android-mobile%20APK-3DDC84?logo=android&logoColor=white&style=flat-square">
    <img alt="macOS" src="https://img.shields.io/badge/macOS-relay%20app-000000?logo=apple&logoColor=white&style=flat-square">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white&style=flat-square">
    <img alt="Electron" src="https://img.shields.io/badge/Electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square">
    <a href="https://visitor-badge.laobi.icu/"><img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex"></a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.1"><strong>Download EasyCodex 0.1.1</strong></a>
    ·
    <a href="https://github.com/Ryan-Laws/easycodex/releases">All releases</a>
  </p>
</div>

EasyCodex is a local remote-control app for Codex coding agents. Run the desktop relay on your computer, install the Android app on your phone, scan the QR code, and then manage Codex sessions from mobile while the actual agent work stays on your machine.

The public release now includes ready-to-use Windows, macOS, and Linux relay builds plus an Android APK. You do not need to clone the repository just to try the product.

## Install EasyCodex

Download the current release from [EasyCodex 0.1.1](https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.1). On GitHub, you can also open the repository's **Releases** page from the right sidebar and choose the latest EasyCodex release.

| Platform | Download | What it is for |
| --- | --- | --- |
| Windows | [`EasyCodex.Relay.Setup.0.1.1-x64.exe`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.Setup.0.1.1-x64.exe) | Recommended Windows desktop relay installer |
| Windows | [`EasyCodex.Relay.Portable.0.1.1-x64.exe`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.Portable.0.1.1-x64.exe) | Portable Windows relay app |
| Android | [`EasyCodex.Mobile.0.1.1.apk`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Mobile.0.1.1.apk) | Android phone app |
| macOS Apple Silicon | [`EasyCodex.Relay.0.1.1.mac-arm64.dmg`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.0.1.1.mac-arm64.dmg) | Apple Silicon desktop relay |
| macOS Intel | [`EasyCodex.Relay.0.1.1.mac-x64.dmg`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.0.1.1.mac-x64.dmg) | Intel desktop relay |
| Linux | [`EasyCodex.Relay.0.1.1.linux-x64.AppImage`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.0.1.1.linux-x64.AppImage) | Portable Linux relay app |
| Linux | [`EasyCodex.Relay.0.1.1.linux-x64.deb`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.1/EasyCodex.Relay.0.1.1.linux-x64.deb) | Debian/Ubuntu relay package |

## Quick Start

1. Install the Codex CLI on your computer and make sure it is authenticated.
2. Install and open **EasyCodex Relay** on Windows or macOS.
3. Click the relay start button in the desktop app. It will show the relay status, connection URL, API key, and QR code.
4. Install `EasyCodex.Mobile.0.1.1.apk` on your Android phone.
5. Scan the QR code with the phone or open the connection link from the app.
6. Pick a workspace, create or resume a Codex session, and control the agent from your phone.

The phone does not run Codex directly. It connects to the desktop relay over WebSocket, and the relay launches `codex app-server` locally beside your repositories.

## What You Can Do

- Start, resume, interrupt, and stop Codex agent sessions.
- Stream Codex responses in real time from your phone.
- Queue messages while an agent is busy.
- Review mobile approval prompts before local actions run.
- Browse workspace files, inspect Git status, view diffs, and work with branches and worktrees.
- Receive local relay events and optional mobile notifications when work finishes.
- Pair quickly by QR code or `easycodex://connect` deep link.

## Tech Stack

| Area | Technology |
| --- | --- |
| Android app | Kotlin, native Android, Jetpack Compose, Material 3, OkHttp, Google Code Scanner |
| Desktop relay | Electron, electron-builder, bundled local relay launcher |
| Agent relay | Node.js 18+, TypeScript, Express, `ws`, `simple-git`, Codex `app-server` JSON-RPC |
| Developer tooling | PowerShell-friendly Node.js scripts and GitHub Actions release builds |

## Architecture

```text
Android app <-> EasyCodex Relay <-> codex app-server <-> Codex thread
```

The relay is local-first. It authenticates mobile clients with a relay API key, starts and supervises Codex processes, translates Codex JSON-RPC events, and exposes explicit file/Git/workspace actions to the app.

## Requirements

- A Windows or macOS computer for the desktop relay
- An Android phone or emulator for the mobile app
- OpenAI Codex CLI installed and authenticated on the computer
- Phone and computer on the same trusted network, or connected through a private network such as Tailscale

## Build From Source

Most users should install from the release page. Use these commands only if you are developing EasyCodex itself.

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
├── desktop-relay/   Electron Windows/macOS relay desktop app
└── scripts/         CLI and local setup helpers
```

## Security Model

- The relay requires an API key for WebSocket clients and health checks.
- Treat relay access as powerful: it can read workspace files, inspect Git state, and launch Codex in a working directory.
- Prefer a trusted LAN or private network for day-to-day use.
- Never commit API keys, relay keys, OpenAI tokens, local agent state, or private environment files.

## Documentation

- [APP.md](APP.md) explains the app architecture and local runtime model.
- [AGENT.md](AGENT.md) describes relay-managed Codex agents, WebSocket actions, and runtime behavior.
- [RELEASE.md](RELEASE.md) documents the CI/CD release process, Android signing requirements, and launch smoke tests.
- [desktop-relay/README.md](desktop-relay/README.md) covers packaging and release builds for the desktop relay.

## Community

- [LinuxDo](https://linux.do/) - a friendly community for developers and open source users.

## License

EasyCodex is released under the [MIT License](LICENSE).
