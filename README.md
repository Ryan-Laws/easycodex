# EasyCodex

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

**EasyCodex** is a mobile remote control for Codex coding agents. It includes a native Android app and a local Node.js agent relay that connects your phone to `codex app-server`.

The mobile app display name is **EasyCodex**.

### What It Does

- Control Codex agents from your phone
- Create and manage multiple agent threads
- Stream agent responses in real time
- Queue messages while an agent is busy
- Receive local app notifications when connected work finishes
- Scan a QR code to connect the app to the agent relay
- Browse files, view diffs, inspect branches, and run workspace actions through the relay

### Project Structure

```text
EasyCodex/
├── mobile/          Native Android app
├── agent-relay/     Node.js Agent Relay for Codex
└── scripts/         Setup and helper scripts
```

### Requirements

- Node.js 18 or newer
- OpenAI Codex CLI installed and authenticated
- Android device or emulator
- Computer and phone on the same network, or connected through a private network such as Tailscale

### Quick Start

Install and run the agent relay:

```powershell
Set-Location agent-relay
npm install
npm run dev
```

Build or run the Android app from Android Studio. If Gradle and the Android SDK are on PATH, you can also run:

```powershell
Set-Location mobile
gradle assembleDebug
```

Scan the QR code printed by the relay with your phone camera. Android opens EasyCodex and saves the WebSocket URL and API key automatically.

### Useful Commands

```powershell
# Agent Relay
Set-Location agent-relay
npm run build
npm run dev

# Android app
Set-Location mobile
gradle assembleDebug
```

### Security Notes

- Do not commit API keys, relay keys, tokens, or private environment files.
- Treat the relay API key like a password.
