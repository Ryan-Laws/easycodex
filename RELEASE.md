# EasyCodex Release Process

EasyCodex releases must be built by GitHub Actions. Do not manually replace public release assets unless the CI/CD workflow has produced and smoke-tested the same artifact.

## Required GitHub Secrets

Android release signing uses one stable keystore. Keep it unchanged across versions so users can upgrade without uninstalling.

Configure these repository secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded Android release keystore file. |
| `ANDROID_KEYSTORE_ALIAS` | Key alias inside the release keystore. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_PASSWORD` | Key password, only if different from the keystore password. |
| `ANDROID_SIGNING_CERT_SHA256` | Expected release signing certificate SHA-256 digest. |

The release workflow verifies the signed APK certificate digest against `ANDROID_SIGNING_CERT_SHA256`. If the keystore changes by accident, the release fails before upload.

## CI Gate

Every pull request and `main` push runs `.github/workflows/ci.yml`:

- Agent relay: `npm ci` and `npm run build`.
- Desktop relay: Electron unpacked packaging smoke on Windows.
- Android: debug and release builds, `lintVitalRelease`, signing the release APK with an ephemeral CI key, installing the release APK on an emulator, launching `MainActivity`, and failing on startup crashes such as `VerifyError` or `AndroidRuntime`.

Android UI/runtime changes are not considered safe until the emulator launch smoke passes.

## Android Release

Use `.github/workflows/release-android.yml`.

1. Bump `versionName` and `versionCode` in `mobile/app/build.gradle.kts`.
2. Bump matching package versions where relevant.
3. Update `CHANGELOG.md`. Keep each release note to exactly two subsections, `### Added` and `### Fixed`; put behavior changes and improvements under the closest matching subsection instead of adding extra headings.
4. Commit the release directly on `main`; do not create a temporary release branch unless explicitly asked.
5. Push `main`, then push a tag matching `versionName`, for example `v0.1.1`.
6. Let the workflow build, sign, verify the signing certificate, launch on emulator, and upload:
   - `EasyCodex.Mobile.<version>.apk`
   - `EasyCodex.Mobile.<version>.apk.sha256`

Each public release tag keeps its own assets and changelog notes. The workflow extracts only the matching `CHANGELOG.md` section for the tag, and release uploads fail if an asset with the same name already exists. Do not overwrite old release assets; publish fixes under a new version tag.

Beta builds should be published from the `beta` branch with prerelease tags such as `v0.1.2-beta.1`. Mark the GitHub release as a pre-release so stable clients continue to use the latest public release, while beta-channel clients can detect and download the prerelease APK.

For manual dry runs, start `Release Android` with `upload=false`. It will still build, sign, verify, smoke-test, and keep artifacts in the workflow run without changing the public release.

## Desktop Relay Release

Use `.github/workflows/release-desktop-relay.yml`.

The workflow checks that the tag version matches:

- root `package.json`
- `agent-relay/package.json`
- `desktop-relay/package.json`

Then it builds and uploads Windows, macOS, and Linux relay assets to the matching release.

Desktop release uploads follow the same rule: each tag has separate release notes and assets, and existing assets are not clobbered.

## Local Signing

Local Android signing is only for emergency validation and uses `scripts/sign-android-release.ps1` with an untracked `.release-secrets/android-release-signing.psd1` file.

Do not commit keystores, passwords, `.release-secrets`, or generated signed APKs.

## Local Android Smoke

When Android Studio or a local emulator is available, validate the signed APK before sharing it:

```powershell
.\scripts\sign-android-release.ps1 -SkipBuild
.\scripts\smoke-android-release.ps1
```

The smoke script installs the signed APK, grants expected runtime permissions, launches `MainActivity`, waits for the process to remain alive, and fails on fatal startup logs, verifier errors, or low-memory process death.

## Recovery Rule

If a released Android APK fails to install over an older build with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the old app was signed by a different key. That should not happen for public releases after this pipeline is in place. Investigate the signing certificate before publishing another APK.
