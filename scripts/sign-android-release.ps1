param(
  [string]$Version,
  [switch]$SkipBuild,
  [switch]$Upload,
  [string]$Tag
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$MobileDir = Join-Path $RepoRoot "mobile"
$AppBuildFile = Join-Path $MobileDir "app\build.gradle.kts"
$SecretsFile = Join-Path $RepoRoot ".release-secrets\android-release-signing.psd1"

if (-not (Test-Path -LiteralPath $SecretsFile)) {
  throw "Missing local signing config: $SecretsFile"
}

$Signing = Import-PowerShellDataFile -LiteralPath $SecretsFile
$Keystore = $Signing.Keystore
$Alias = $Signing.Alias
$StorePassword = $Signing.StorePassword
$KeyPassword = if ($Signing.KeyPassword) { $Signing.KeyPassword } else { $Signing.StorePassword }

if (-not (Test-Path -LiteralPath $Keystore)) {
  throw "Keystore not found: $Keystore"
}

if (-not $Alias -or -not $StorePassword) {
  throw "Signing config must define Alias and StorePassword."
}

if (-not $Version) {
  $BuildText = Get-Content -Raw -LiteralPath $AppBuildFile
  $Match = [regex]::Match($BuildText, 'versionName\s*=\s*"([^"]+)"')
  if (-not $Match.Success) {
    throw "Could not infer versionName from $AppBuildFile. Pass -Version explicitly."
  }
  $Version = $Match.Groups[1].Value
}

if (-not $Tag) {
  $Tag = "v$Version"
}

$BuildToolsRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
if (-not (Test-Path -LiteralPath $BuildToolsRoot)) {
  throw "Android build-tools not found at $BuildToolsRoot"
}

$BuildTools = Get-ChildItem -LiteralPath $BuildToolsRoot -Directory |
  Sort-Object Name -Descending |
  Where-Object {
    (Test-Path -LiteralPath (Join-Path $_.FullName "zipalign.exe")) -and
    (Test-Path -LiteralPath (Join-Path $_.FullName "lib\apksigner.jar"))
  } |
  Select-Object -First 1

if (-not $BuildTools) {
  throw "Could not find build-tools with zipalign.exe and lib\apksigner.jar."
}

$Zipalign = Join-Path $BuildTools.FullName "zipalign.exe"
$ApkSignerJar = Join-Path $BuildTools.FullName "lib\apksigner.jar"
$Java = (Get-Command java -ErrorAction Stop).Source

if (-not $SkipBuild) {
  Push-Location $MobileDir
  try {
    & ".\gradlew.bat" assembleRelease
    if ($LASTEXITCODE -ne 0) {
      throw "Gradle assembleRelease failed with code $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }
}

$ReleaseDir = Join-Path $MobileDir "app\build\outputs\apk\release"
$UnsignedApk = Join-Path $ReleaseDir "app-release-unsigned.apk"
$AlignedApk = Join-Path $ReleaseDir "EasyCodex.Mobile.$Version-aligned.apk"
$SignedApk = Join-Path $ReleaseDir "EasyCodex.Mobile.$Version.apk"

if (-not (Test-Path -LiteralPath $UnsignedApk)) {
  throw "Unsigned release APK not found: $UnsignedApk"
}

& $Zipalign -p -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) {
  throw "zipalign failed with code $LASTEXITCODE"
}

$SignArgs = @(
  "-jar", $ApkSignerJar,
  "sign",
  "--ks", $Keystore,
  "--ks-key-alias", $Alias,
  "--ks-pass=pass:$StorePassword",
  "--key-pass=pass:$KeyPassword",
  "--in", $AlignedApk,
  "--out", $SignedApk
)
& $Java @SignArgs
if ($LASTEXITCODE -ne 0) {
  throw "apksigner sign failed with code $LASTEXITCODE"
}

$VerifyArgs = @("-jar", $ApkSignerJar, "verify", "--verbose", $SignedApk)
& $Java @VerifyArgs
if ($LASTEXITCODE -ne 0) {
  throw "apksigner verify failed with code $LASTEXITCODE"
}

if ($Upload) {
  & gh release upload $Tag $SignedApk
  if ($LASTEXITCODE -ne 0) {
    throw "gh release upload failed with code $LASTEXITCODE. Existing release assets are never overwritten; publish a new version tag or delete the mistaken asset manually."
  }
}

Write-Host "Signed APK: $SignedApk"
