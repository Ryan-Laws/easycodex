<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex 標識" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>在電腦上啟動 Codex，用 Android 手機遠端控制。</strong></p>
  <p>
    <a href="README.md">English</a> |
    <a href="README.zh-CN.md">简体中文</a> |
    <a href="README.zh-TW.md">繁體中文</a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.0"><img alt="Release v0.1.0" src="https://img.shields.io/badge/release-v0.1.0-2f80ed?style=flat-square"></a>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-green?style=flat-square"></a>
    <img alt="Windows" src="https://img.shields.io/badge/Windows-relay%20app-0078D4?logo=windows&logoColor=white&style=flat-square">
    <img alt="Android" src="https://img.shields.io/badge/Android-mobile%20APK-3DDC84?logo=android&logoColor=white&style=flat-square">
    <img alt="macOS" src="https://img.shields.io/badge/macOS-relay%20app-000000?logo=apple&logoColor=white&style=flat-square">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white&style=flat-square">
    <img alt="Electron" src="https://img.shields.io/badge/Electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square">
    <a href="https://visitor-badge.laobi.icu/"><img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex"></a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.0"><strong>下載 EasyCodex 0.1.0</strong></a>
    ·
    <a href="https://github.com/Ryan-Laws/easycodex/releases">全部 Release</a>
  </p>
</div>

EasyCodex 是一個本機優先的 Codex 程式開發智能體遠端控制應用。你在電腦上執行桌面端中繼，在 Android 手機上安裝行動端，掃描 QR Code 連線後，就可以用手機管理 Codex 對話，而真正的智能體執行仍然留在你的電腦上。

目前公開版本已經提供可直接使用的 Windows 中繼程式和 Android APK。一般使用者不需要 clone 倉庫才能體驗。

## 安裝 EasyCodex

從 [EasyCodex 0.1.0](https://github.com/Ryan-Laws/easycodex/releases/tag/v0.1.0) 下載目前版本。在 GitHub 頁面上，也可以從倉庫右側的 **Releases** 入口進入發布頁，再選擇最新的 EasyCodex 版本。

| 平台 | 下載 | 用途 |
| --- | --- | --- |
| Windows | [`EasyCodex.Relay.Setup.0.1.0.exe`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.0/EasyCodex.Relay.Setup.0.1.0.exe) | 推薦的 Windows 桌面端中繼安裝包 |
| Windows | [`EasyCodex.Relay.0.1.0.exe`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.0/EasyCodex.Relay.0.1.0.exe) | 免安裝可攜版 Windows 中繼 |
| Android | [`EasyCodex.Mobile.0.1.0.apk`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.0/EasyCodex.Mobile.0.1.0.apk) | Android 手機應用 |
| macOS | [`EasyCodex.Relay-0.1.0-arm64.dmg`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.0/EasyCodex.Relay-0.1.0-arm64.dmg) | Apple Silicon 桌面端中繼 |
| macOS | [`EasyCodex.Relay-0.1.0-arm64-mac.zip`](https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.0/EasyCodex.Relay-0.1.0-arm64-mac.zip) | macOS 中繼壓縮包 |

## 快速開始

1. 在電腦上安裝並登入 OpenAI Codex CLI。
2. 在 Windows 或 macOS 上安裝並開啟 **EasyCodex Relay**。
3. 在桌面端應用裡點擊啟動中繼。它會顯示中繼狀態、連線位址、API Key 和 QR Code。
4. 在 Android 手機上安裝 `EasyCodex.Mobile.0.1.0.apk`。
5. 用手機掃描 QR Code，或在應用內開啟連線連結。
6. 選擇工作區，建立或恢復 Codex 對話，然後用手機控制智能體。

手機不會直接執行 Codex。它透過 WebSocket 連線到桌面端中繼，由中繼在本機倉庫旁邊啟動 `codex app-server`。

## 你可以做什麼

- 建立、恢復、中斷和停止 Codex 智能體對話。
- 在手機上即時查看 Codex 串流回覆。
- 智能體忙碌時繼續佇列傳送訊息。
- 在本機操作執行前，透過手機處理核准提示。
- 瀏覽工作區檔案、檢查 Git 狀態、查看 diff，並使用分支和 worktree。
- 在任務完成時接收本機中繼事件和可選的手機通知。
- 透過 QR Code 或 `easycodex://connect` deep link 快速配對。

## 技術棧

| 模組 | 技術 |
| --- | --- |
| Android 應用 | Kotlin、原生 Android、Jetpack Compose、Material 3、OkHttp、Google Code Scanner |
| 桌面端中繼 | Electron、electron-builder、內建本機中繼啟動器 |
| Agent Relay | Node.js 18+、TypeScript、Express、`ws`、`simple-git`、Codex `app-server` JSON-RPC |
| 開發工具 | PowerShell 友善的 Node.js 腳本和 GitHub Actions 發布建置 |

## 架構

```text
Android app <-> EasyCodex Relay <-> codex app-server <-> Codex thread
```

中繼採用本機優先設計。它使用 relay API Key 驗證行動端用戶端，啟動和管理 Codex 程序，轉換 Codex JSON-RPC 事件，並向應用程式暴露明確的檔案、Git、工作區操作。

## 環境需求

- 用於執行桌面端中繼的 Windows 或 macOS 電腦
- Android 實體裝置或模擬器
- 電腦上已安裝並登入 OpenAI Codex CLI
- 手機和電腦在同一個可信網路內，或透過 Tailscale 等私人網路連線

## 從源碼建置

大多數使用者應該從 Release 頁面安裝。只有在開發 EasyCodex 本身時才需要這些命令。

```powershell
# 桌面端中繼
Set-Location desktop-relay
npm install
npm start

# Agent Relay
Set-Location agent-relay
npm install
npm run build

# Android 應用
Set-Location mobile
gradle assembleDebug
```

## 倉庫結構

```text
EasyCodex/
├── mobile/          Android 原生手機應用
├── agent-relay/     Codex 的 Node.js Agent Relay
├── desktop-relay/   Electron Windows/macOS 桌面端中繼
└── scripts/         CLI 和本機安裝輔助腳本
```

## 安全模型

- 中繼要求 WebSocket 用戶端和健康檢查提供 API Key。
- 請把中繼存取視為高權限存取：它可以讀取工作區檔案、檢查 Git 狀態，並在工作目錄中啟動 Codex。
- 日常使用建議放在可信 LAN 或私人網路內。
- 不要提交 API Key、中繼金鑰、OpenAI Token、本機 agent 狀態或私人環境檔案。

## 文件

- [APP.md](APP.md) 介紹應用架構和本機執行模型。
- [AGENT.md](AGENT.md) 說明由中繼管理的 Codex 智能體、WebSocket action 和執行環境行為。
- [desktop-relay/README.md](desktop-relay/README.md) 說明桌面端中繼的打包和發布建置。

## 授權

EasyCodex 基於 [MIT License](LICENSE) 發布。
