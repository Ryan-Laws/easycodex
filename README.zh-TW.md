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
    <a href="https://github.com/Ryan-Laws/easycodex/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Ryan-Laws/easycodex?label=release&style=flat-square"></a>
    <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-green?style=flat-square"></a>
    <img alt="Windows" src="https://img.shields.io/badge/Windows-relay%20app-0078D4?logo=windows&logoColor=white&style=flat-square">
    <img alt="Android" src="https://img.shields.io/badge/Android-mobile%20APK-3DDC84?logo=android&logoColor=white&style=flat-square">
    <img alt="macOS" src="https://img.shields.io/badge/macOS-relay%20app-000000?logo=apple&logoColor=white&style=flat-square">
    <img alt="Linux-relay" src="https://img.shields.io/badge/Linux-relay%20app-FCC624?logo=linux&logoColor=black&style=flat-square">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white&style=flat-square">
    <img alt="Electron" src="https://img.shields.io/badge/Electron-desktop%20relay-47848F?logo=electron&logoColor=white&style=flat-square">
    <a href="https://visitor-badge.laobi.icu/"><img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex"></a>
  </p>
  <p>
    <a href="https://github.com/Ryan-Laws/easycodex/releases/latest"><strong>下載最新版 EasyCodex</strong></a>
    ·
    <a href="https://github.com/Ryan-Laws/easycodex/releases">全部 Release</a>
  </p>
</div>

EasyCodex 是一個本機優先的 Codex 程式開發智能體遠端控制應用。你在電腦上執行桌面端中繼，在 Android 手機上安裝行動端，掃描 QR Code 連線後，就可以用手機管理 Codex 對話，而真正的智能體執行仍然留在你的電腦上。

目前公開版本已經提供可直接使用的 Windows、macOS、Linux 中繼程式和 Android APK。一般使用者不需要 clone 倉庫才能體驗。

## 為什麼選擇 EasyCodex

OpenAI 已經把 Codex 遠端存取帶到 ChatGPT 手機應用裡，但它目前仍是官方預覽體驗。EasyCodex 的定位不同：它把手機連到你自己電腦上的本機中繼，不要求把手機控制面放到官方託管控制平面裡，並且覆蓋更多桌面宿主系統。

| 對比項 | EasyCodex | OpenAI Codex 手機預覽 |
| --- | --- | --- |
| 桌面宿主系統 | 提供 Windows、macOS、Linux 中繼建置 | OpenAI 2026-05-14 發布說明寫明：手機遠端存取目前可連接執行在 macOS 上的 Codex；OpenAI 部落格說明 Windows 手機連線支援還在 coming soon |
| 手機配對方式 | 透過 QR Code 或 deep link 連接你自己的中繼，並使用本機 relay API Key 驗證 | 整合在 ChatGPT 手機應用裡，需要使用 ChatGPT/OpenAI 帳號體系 |
| API / Provider 彈性 | 手機端不硬編碼官方帳號登入；中繼沿用你電腦上已經設定和登入的 Codex CLI 環境，本機 Codex 支援相容第三方 API 時也可以隨本機設定使用 | 綁定 OpenAI 的 ChatGPT/Codex 帳號體驗 |
| 資料路徑 | 手機透過可信 LAN 或 Tailscale 等私人網路連接你的桌面中繼；倉庫、憑據和執行環境留在本機 | 使用 OpenAI 授權的 ChatGPT 裝置和官方中繼基礎設施 |
| 工作流程能力 | 手機核准、查看 diff/Git 狀態、選擇檔案提交、瀏覽專案和 worktree、傳送附件、開啟本機 CLI 視窗，並帶桌面中繼工作台 | 手機啟動/繼續 thread、核准、改變方向、切換 host，並查看連接宿主的即時上下文 |

目前官方行為來源：[OpenAI 產品文章](https://openai.com/index/work-with-codex-from-anywhere/) 和 [ChatGPT 發布說明](https://help.openai.com/en/articles/6825453-chatgpt-release-notes)。

## 安裝 EasyCodex

從 [最新版 EasyCodex Release](https://github.com/Ryan-Laws/easycodex/releases/latest) 下載目前版本。在 GitHub 頁面上，也可以從倉庫右側的 **Releases** 入口進入發布頁，再選擇最新的 EasyCodex 版本。

| 平台 | 下載 | 用途 |
| --- | --- | --- |
| Windows | `EasyCodex.Relay.Setup.*-x64.exe` | 推薦的 Windows 桌面端中繼安裝包 |
| Windows | `EasyCodex.Relay.Portable.*-x64.exe` | 免安裝可攜版 Windows 中繼 |
| Android | `EasyCodex.Mobile.*.apk` | Android 手機應用 |
| macOS Apple Silicon | `EasyCodex.Relay.*.mac-arm64.dmg` | Apple Silicon 桌面端中繼 |
| macOS Intel | `EasyCodex.Relay.*.mac-x64.dmg` | Intel 桌面端中繼 |
| Linux | `EasyCodex.Relay.*.linux-x64.AppImage` | 免安裝 Linux 中繼 |
| Linux | `EasyCodex.Relay.*.linux-x64.deb` | Debian/Ubuntu 安裝包 |

## 快速開始

1. 在電腦上安裝並登入 OpenAI Codex CLI。
2. 在 Windows、macOS 或 Linux 上安裝並開啟 **EasyCodex Relay**。
3. 在桌面端應用裡點擊啟動中繼。它會顯示中繼狀態、連線位址、API Key 和 QR Code。
4. 在 Android 手機上安裝最新 Release 裡的 `EasyCodex.Mobile.*.apk`。
5. 用手機掃描 QR Code，或在應用內開啟連線連結。
6. 選擇工作區，建立或恢復 Codex 對話，然後用手機控制智能體。

手機不會直接執行 Codex。它透過 WebSocket 連線到桌面端中繼，由中繼在本機倉庫旁邊啟動 `codex app-server`。

## 你可以做什麼

- 建立、恢復、中斷、停止和封存 Codex 智能體對話。
- 查看執行中 agent、活躍 Codex thread、歷史可恢復 thread、佇列 follow-up 和未讀完成狀態。
- 在手機上即時查看 Codex 回覆、reasoning、命令輸出、檔案改動、計畫和子 agent 活動。
- 傳送文字、圖片和檔案附件；附件會保存到目前工作區的 `.easycodex-attachments/`。
- 使用快捷回覆、emoji 面板和 Android 系統語音輸入來編寫 prompt。
- 先審閱計畫，再選擇最佳化計畫或開始執行。
- 瀏覽工作區檔案、檢查 Git 狀態、查看完整/單檔 diff、預覽檔案，並提交選中的改動檔案。
- 瀏覽受信工作區、relay-managed repos 和 Git worktree。
- 在本機操作執行前，透過手機處理核准提示和結構化 user-input 問題。
- 開啟多個手機 CLI 視窗，每個視窗對應獨立的 `codex exec`，支援 resume/review、profile、圖片、額外目錄、JSON 輸出、ephemeral、ignore-rules、sandbox 和 Git repo check 開關。
- 選擇模型、reasoning effort、service tier、cwd、approval policy、sandbox mode 和更新通道。
- 接收本機通知和可選手機推送，並同步每個 agent 的通知級別和最近通知歷史。
- 在 Android 端檢查穩定版/Beta APK 更新，在桌面中繼端檢查 relay/安裝包更新。
- 使用桌面中繼 workbench 查看任務、閱讀對話、傳送 follow-up、處理核准，並查看 Git 狀態/diff。
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

- 用於執行桌面端中繼的 Windows、macOS 或 Linux 電腦
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
├── desktop-relay/   Electron Windows/macOS/Linux 桌面端中繼
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
