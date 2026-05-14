# EasyCodex Relay Desktop

This is the Electron desktop relay app for EasyCodex. It packages Windows, macOS, and Linux desktop control surfaces for the existing `agent-relay`.

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

Architecture-specific builds are available through:

```zsh
npm run dist:mac:x64
npm run dist:mac:arm64
```

## Package Linux

Run this on Linux:

```bash
cd desktop-relay
npm install
npm run dist:linux
```

Linux artifacts are written to `desktop-relay/release/` as AppImage and deb packages.

## GitHub Release Builds

You do not need a MacBook or Linux workstation to publish the desktop builds. Push a version tag and GitHub Actions will build Windows x64, macOS x64, macOS arm64, and Linux x64:

```powershell
git tag v0.1.1
git push origin v0.1.1
```

The workflow uploads the Windows installer, Windows portable app, macOS DMG/ZIP builds for both Intel and Apple Silicon, and Linux AppImage/deb builds to the GitHub Release for that tag.

## Behavior

- Development mode uses the repository `agent-relay/` directly.
- Packaged mode copies `agent-relay` into `~/.easycodex/desktop-relay-runtime/` so dependencies and builds can be written safely.
- The relay API key is read from or generated into `~/.easycodex/config.json`.
- The desktop app follows the latest connected phone language reported by the relay. If no phone language is available, it follows the desktop system language.
