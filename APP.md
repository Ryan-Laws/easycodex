# EasyCodex App

EasyCodex is a mobile app for controlling Codex coding agents from a phone. It is not a hosted SaaS: the agent relay runs beside the codebase, and the mobile app connects to that relay over WebSocket.

## What Runs Where

| Piece | Location | Purpose |
| --- | --- | --- |
| Mobile app | `mobile/` | Native Android Kotlin/Jetpack Compose app for connecting to the relay, managing agents, and chatting with Codex. |
| Agent Relay | `agent-relay/` | Node.js WebSocket server that authenticates clients and manages Codex agent processes. |
| CLI/setup | `scripts/` | Terminal setup flow for installing dependencies and starting the relay. |

## First Local Run

From the repository root:

```powershell
node scripts/setup-and-start.mjs
```

The setup script asks for:

- relay port, usually `3001`
- relay API key, generated automatically if blank
- whether to install dependencies

It starts the agent relay and prints a QR code containing the WebSocket URL and API key. Scan it with the phone camera to open EasyCodex and save the connection automatically.

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

Scan the QR code printed by the relay with the phone camera, or enter the values manually in app settings:

- Relay URL: `ws://<computer-ip>:3001`
- API key: the key printed by the relay

## Mobile App Structure

Important paths:

- `mobile/settings.gradle.kts` and `mobile/build.gradle.kts` define the Android build.
- `mobile/app/build.gradle.kts` configures the native app module.
- `mobile/app/src/main/java/com/easycodex/mobile/MainActivity.kt` contains the Compose UI and relay WebSocket client.
- `agent-relay/src/server.ts` is the relay WebSocket API the Android app consumes.

## Runtime Features

The native Android app currently supports:

- creating and managing multiple Codex agents
- streaming agent responses
- live task-list sync and active resumable-thread refresh from relay WebSocket events
- mobile approval prompts for Codex server-initiated requests
- delegated sub-agent activity shown as first-class task detail cards
- visible Git worktree selection in the project picker, including current worktree and branch labels
- relay URL/API key settings
- model, reasoning, service tier, cwd, approval policy, and system prompt controls

The native Android app also supports QR connection setup, file/Git flows, notifications, and richer workspace organization.

## Environment

The Android app stores the relay URL and API key in Android SharedPreferences, either from the QR deep link or from manual settings edits. Do not commit relay keys or private config files.

## Verification

For mobile-only edits, build the native Android module when an Android toolchain is installed:

```powershell
Set-Location mobile
gradle assembleDebug
```

For a real app smoke test, install the debug build or run it from Android Studio and verify:

- the app opens
- relay URL/API key can be saved
- connection status updates
- a workspace/thread can be opened
- messages stream from an agent when the relay is running
