# EasyCodex Agent Instructions

These instructions apply to the whole `EasyCodex` repository.

## Project Shape

EasyCodex is a mobile remote control for Codex coding agents.

- `mobile/` is the native Android app implemented with Kotlin and Jetpack Compose.
- `agent-relay/` is the Node.js Agent Relay that launches `codex app-server`.
- `scripts/` contains the npm CLI and local setup helpers.

## Development Defaults

- This repository is usually developed on Windows. Prefer PowerShell commands.
- Use `rg` / `rg --files` first for discovery.
- Keep changes minimal and follow the style of nearby files.
- Do not add dependencies unless the feature cannot be implemented with the current stack.
- Do not write credentials, API keys, relay keys, or tokens into tracked files.

## Safety Boundary

Treat the current repository root as the writable boundary.

Before editing, confirm write targets resolve inside that directory. Do not modify parent folders, user profile folders, or global config unless the user explicitly asks for that exact file.

## App Work

For `mobile/` changes:

- Keep the app usable on phone-sized screens.
- Preserve the native Android/Kotlin/Compose setup.
- Prefer focused Android builds or Kotlin/Gradle checks over broad unrelated rewrites.

Useful commands:

```powershell
Set-Location mobile
gradle assembleDebug
```

## Relay Work

For `agent-relay/` changes:

- Keep the relay protocol explicit in `src/codex-rpc.ts`.
- Keep Codex process management in `src/session-orchestrator.ts`.
- Treat paths and shell execution as sensitive. Validate input paths before exposing filesystem operations.
- Preserve API key authentication for WebSocket and health endpoints.

Useful commands:

```powershell
Set-Location agent-relay
npm install
npm run build
npm run dev
```

## Documentation Work

- Keep README focused on first-run usage.
- Update `APP.md` when the app architecture changes.
- Update `AGENT.md` when relay-agent behavior or WebSocket actions change.
