<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex 標識" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>讓 Codex 在電腦上執行，用 Android 手機遠端掌控。</strong></p>
  <p>
    <a href="README.md">English</a> |
    <a href="README.zh-CN.md">简体中文</a> |
    <a href="README.zh-TW.md">繁體中文</a>
  </p>
  <p>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/Ryan-Laws/easycodex?style=flat-square"></a>
    <a href="https://github.com/Ryan-Laws/easycodex/releases"><img alt="Release" src="https://img.shields.io/github/v/release/Ryan-Laws/easycodex?include_prereleases&style=flat-square"></a>
    <a href="https://github.com/Ryan-Laws/easycodex/actions/workflows/release-desktop-relay.yml"><img alt="Desktop Relay workflow" src="https://img.shields.io/github/actions/workflow/status/Ryan-Laws/easycodex/release-desktop-relay.yml?branch=main&label=desktop%20relay&style=flat-square"></a>
    <a href="package.json"><img alt="Node.js >=18" src="https://img.shields.io/badge/node-%3E%3D18-339933?logo=node.js&logoColor=white&style=flat-square"></a>
    <a href="mobile/app/build.gradle.kts"><img alt="Android API 26+" src="https://img.shields.io/badge/android-API%2026%2B-3DDC84?logo=android&logoColor=white&style=flat-square"></a>
    <a href="mobile/"><img alt="Kotlin" src="https://img.shields.io/badge/kotlin-native%20android-7F52FF?logo=kotlin&logoColor=white&style=flat-square"></a>
    <a href="mobile/app/build.gradle.kts"><img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square"></a>
    <a href="agent-relay/"><img alt="TypeScript" src="https://img.shields.io/badge/typescript-relay-3178C6?logo=typescript&logoColor=white&style=flat-square"></a>
    <a href="desktop-relay/"><img alt="Electron" src="https://img.shields.io/badge/electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square"></a>
    <a href="https://visitor-badge.laobi.icu/"><img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex"></a>
  </p>
</div>

EasyCodex 可以把 Android 手機變成 Codex 程式開發智能體的遠端控制台。Codex 執行環境和本機中繼仍然留在你自己的電腦和程式碼倉庫旁邊，行動端則提供原生介面，用來啟動對話、和智能體聊天、追蹤長時間任務、查看檔案和 diff，並回應核准請求。

它適合希望 Codex 在本機持續工作，同時又不想一直守在終端視窗前的開發者。

## 專案數據

| 指標 | Badge |
| --- | --- |
| 訪問量 | ![Visitors](https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex) |
| Stars | ![GitHub stars](https://img.shields.io/github/stars/Ryan-Laws/easycodex?style=social) |
| Forks | ![GitHub forks](https://img.shields.io/github/forks/Ryan-Laws/easycodex?style=social) |
| 最新 Release | ![GitHub release](https://img.shields.io/github/v/release/Ryan-Laws/easycodex?include_prereleases) |
| License | ![GitHub license](https://img.shields.io/github/license/Ryan-Laws/easycodex) |
| 桌面端發布流程 | ![GitHub Actions workflow status](https://img.shields.io/github/actions/workflow/status/Ryan-Laws/easycodex/release-desktop-relay.yml?branch=main) |

## 為什麼使用 EasyCodex

- 讓 Codex 智能體繼續在電腦上執行，同時用手機控制它們。
- 不必一直坐在電腦前，也能追蹤串流回覆和長時間程式開發任務。
- 在原生 Android 介面中管理多個智能體、Codex 執行緒、工作目錄、模型和核准設定。
- 在做決定前查看工作區檔案、diff、分支和 Git worktree。
- 敏感執行保持在本機：中繼執行在你的倉庫旁邊，並要求用戶端提供 API Key。

## 功能特色

- 建立、恢復、中斷和停止多個 Codex 智能體對話。
- 即時串流接收來自 `codex app-server` 的智能體回覆。
- 智能體忙碌時可將訊息加入佇列。
- 在手機上回應 Codex 請求的操作核准。
- 瀏覽檔案、讀取工作區內容、查看 Git diff、檢查分支，並使用 Git worktree。
- 透過掃描 QR Code 或開啟 `easycodex://connect` deep link 完成連線。
- 在任務完成時接收本機中繼事件和可選的手機通知。
- 使用 Windows 和 macOS 的 Electron 桌面端啟動並監控本機中繼。
- 偏好命令列時，也可以使用終端優先的安裝腳本。

## 技術棧

| 模組 | 技術 |
| --- | --- |
| Android 應用 | Kotlin、原生 Android、Jetpack Compose、Material 3、OkHttp、Google Code Scanner |
| Agent Relay | Node.js 18+、TypeScript、Express、`ws`、`simple-git`、Codex `app-server` JSON-RPC |
| 桌面端中繼 | Electron、electron-builder、本機 QR Code 連線介面 |
| CLI 與安裝腳本 | Node.js ESM 腳本，命令對 PowerShell 友善 |

## 架構

```text
Android app <-> Agent Relay <-> codex app-server <-> Codex thread
```

手機不會直接啟動 Codex。它會透過 WebSocket 和 relay API Key 連線到本機 Agent Relay。中繼負責驗證用戶端、啟動和管理 `codex app-server`、轉換 JSON-RPC 事件，並向應用程式暴露明確的檔案、Git、倉庫、模型和執行環境操作。

## 快速開始

### 1. 使用桌面端中繼

```powershell
Set-Location desktop-relay
npm install
npm start
```

桌面端中繼會提供一個本機控制視窗，顯示中繼狀態、連線資訊和用於手機配對的 QR Code。

### 2. 使用終端安裝流程

在倉庫根目錄執行：

```powershell
node scripts/setup-and-start.mjs
```

腳本會詢問中繼連接埠，在你留空時自動產生 API Key，可選安裝相依套件，啟動中繼，並列印 QR Code 連線資訊。

### 3. 手動執行全部元件

啟動 Agent Relay：

```powershell
Set-Location agent-relay
npm install
npm run dev
```

用 Android Studio 建置或執行 Android 應用。如果本機 `PATH` 已設定 Gradle 和 Android SDK，也可以執行：

```powershell
Set-Location mobile
gradle assembleDebug
```

用手機相機掃描中繼列印的 QR Code。Android 會開啟 EasyCodex，並自動保存 WebSocket 位址和 API Key。

## 環境需求

- Node.js 18 或更新版本
- 已安裝並登入 OpenAI Codex CLI
- Android 實體裝置或模擬器
- 電腦和手機在同一個可信網路內，或透過 Tailscale 等私人網路連線
- 用於建置行動端的 Android Studio，或已設定好的 Android SDK/Gradle 環境

## 倉庫結構

```text
EasyCodex/
├── mobile/          Android 原生手機應用
├── agent-relay/     Codex 的 Node.js Agent Relay
├── desktop-relay/   Electron Windows/macOS 桌面端中繼
├── scripts/         CLI 和本機安裝輔助腳本
├── docs/            專案筆記和支援文件
└── site/            公開站點資源
```

## 常用指令

```powershell
# Agent Relay
Set-Location agent-relay
npm run build
npm run dev

# 桌面端中繼
Set-Location desktop-relay
npm start
npm run dist:win

# Android 應用
Set-Location mobile
gradle assembleDebug
```

## 安全模型

- 中繼要求 WebSocket 用戶端和健康檢查提供 API Key。
- 請把中繼存取視為高權限存取：它可以讀取工作區檔案、檢查 Git 狀態，並在工作目錄中啟動 Codex。
- 日常使用建議放在可信 LAN 或私人網路內。
- 如果要在私人網路之外暴露中繼，請透過正確設定的反向代理使用 `wss://`。
- 不要提交 API Key、中繼金鑰、OpenAI Token 或私人環境檔案。

## 文件

- [APP.md](APP.md) 介紹應用架構和本機執行模型。
- [AGENT.md](AGENT.md) 說明由中繼管理的 Codex 智能體、WebSocket action 和執行環境行為。
- [desktop-relay/README.md](desktop-relay/README.md) 說明桌面端中繼的打包和發布建置。

## 貢獻

歡迎貢獻。請保持改動聚焦，遵循現有專案結構，除非能明確改善功能或維護體驗，否則不要引入新的相依套件。

提交 Pull Request 前，請針對你修改的區域執行最相關的檢查：

```powershell
# Relay
Set-Location agent-relay
npm run build

# Android
Set-Location mobile
gradle assembleDebug

# 桌面端中繼
Set-Location desktop-relay
npm run dist:win
```

如果修改了中繼智能體行為或 WebSocket action，請更新 [AGENT.md](AGENT.md)。如果修改了應用架構，請更新 [APP.md](APP.md)。

## 授權

EasyCodex 基於 [MIT License](LICENSE) 發布。
