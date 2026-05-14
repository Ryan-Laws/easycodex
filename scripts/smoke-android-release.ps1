param(
  [string]$Apk,
  [string]$Device,
  [int]$WaitSeconds = 15,
  [switch]$SkipInstall,
  [switch]$NoPermissionGrant
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$PackageName = "com.easycodex.mobile"
$ActivityName = "$PackageName/.MainActivity"

function Find-Adb {
  $Candidates = @(
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
  )
  if ($env:ANDROID_HOME) {
    $Candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
  }
  if ($env:ANDROID_SDK_ROOT) {
    $Candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
  }

  foreach ($Candidate in $Candidates) {
    if (Test-Path -LiteralPath $Candidate) {
      return $Candidate
    }
  }

  $Command = Get-Command adb -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  throw "adb.exe not found. Install Android SDK platform-tools or set ANDROID_HOME."
}

if (-not $Apk) {
  $BuildFile = Join-Path $RepoRoot "mobile\app\build.gradle.kts"
  $BuildText = Get-Content -Raw -LiteralPath $BuildFile
  $Match = [regex]::Match($BuildText, 'versionName\s*=\s*"([^"]+)"')
  if (-not $Match.Success) {
    throw "Could not infer versionName from $BuildFile. Pass -Apk explicitly."
  }
  $Apk = Join-Path $RepoRoot "mobile\app\build\outputs\apk\release\EasyCodex.Mobile.$($Match.Groups[1].Value).apk"
}

$Apk = Resolve-Path -LiteralPath $Apk
$Adb = Find-Adb

if (-not $Device) {
  $DeviceLines = & $Adb devices | Where-Object { $_ -match "`tdevice$" }
  if ($DeviceLines.Count -ne 1) {
    throw "Expected exactly one connected Android device/emulator, found $($DeviceLines.Count). Pass -Device explicitly."
  }
  $Device = ($DeviceLines[0] -split "`t")[0]
}

Write-Host "Using device: $Device"
Write-Host "Using APK: $Apk"

& $Adb -s $Device wait-for-device
if ($LASTEXITCODE -ne 0) {
  throw "adb wait-for-device failed with code $LASTEXITCODE"
}

if (-not $SkipInstall) {
  & $Adb -s $Device install -r $Apk
  if ($LASTEXITCODE -ne 0) {
    throw "adb install failed with code $LASTEXITCODE"
  }
}

if (-not $NoPermissionGrant) {
  foreach ($Permission in @("android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS")) {
    & $Adb -s $Device shell pm grant $PackageName $Permission 2>$null
  }
}

& $Adb -s $Device logcat -c
if ($LASTEXITCODE -ne 0) {
  throw "adb logcat -c failed with code $LASTEXITCODE"
}

& $Adb -s $Device shell am start -W -n $ActivityName
if ($LASTEXITCODE -ne 0) {
  throw "Failed to launch $ActivityName with code $LASTEXITCODE"
}

Start-Sleep -Seconds $WaitSeconds

$AppPid = (& $Adb -s $Device shell pidof $PackageName).Trim()
if (-not $AppPid) {
  throw "$PackageName is not running after $WaitSeconds seconds."
}

$LogDir = Join-Path $RepoRoot ".tmp\android-smoke"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$LogPath = Join-Path $LogDir "easycodex-android-smoke-logcat.txt"
& $Adb -s $Device logcat -d -v time > $LogPath
if ($LASTEXITCODE -ne 0) {
  throw "adb logcat dump failed with code $LASTEXITCODE"
}

$FailurePattern = "FATAL EXCEPTION|AndroidRuntime|VerifyError|Force finishing activity $PackageName|lowmemorykiller.*$PackageName|Process $PackageName .*has died"
$Failures = Select-String -LiteralPath $LogPath -Pattern $FailurePattern
if ($Failures) {
  $Failures | Select-Object -First 40 | ForEach-Object { Write-Error $_.Line }
  throw "Android launch smoke found fatal logs. Full log: $LogPath"
}

Write-Host "Android release smoke passed. PID: $AppPid"
Write-Host "Logcat: $LogPath"
