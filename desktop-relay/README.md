# EasyCodex Relay Desktop

This is the Electron desktop relay app for EasyCodex. It packages a Windows and macOS desktop control surface for the existing `agent-relay`.

## Run Locally

```powershell
Set-Location desktop-relay
npm install
npm start
```

## Package Windows

```powershell
Set-Location desktop-relay
npm run dist:win
```

Windows artifacts are written to `desktop-relay/release/`:

- installer: `EasyCodex Relay Setup <version>.exe`
- portable app: `EasyCodex Relay <version>.exe`
- unpacked app: `win-unpacked/`

## Package macOS

Run this on macOS:

```zsh
cd desktop-relay
npm install
npm run dist:mac
```

macOS artifacts are written to `desktop-relay/release/`. The first release is unsigned, so macOS may require right-clicking the app and choosing Open. Teams with Apple Developer credentials can wire signing into Electron Builder later.

## GitHub Release Builds

You do not need a MacBook to publish the macOS build. Push a version tag and GitHub Actions will build Windows on `windows-latest` and macOS on `macos-latest`:

```powershell
git tag v0.1.0
git push origin v0.1.0
```

The workflow uploads the Windows installer, Windows portable app, macOS DMG, and macOS ZIP to the GitHub Release for that tag.

## Behavior

- Development mode uses the repository `agent-relay/` directly.
- Packaged mode copies `agent-relay` into `~/.easycodex/desktop-relay-runtime/` so dependencies and builds can be written safely.
- The relay API key is read from or generated into `~/.easycodex/config.json`.
- The desktop app follows the latest connected phone language reported by the relay. If no phone language is available, it follows the desktop system language.
