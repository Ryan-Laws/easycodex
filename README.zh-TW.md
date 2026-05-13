# EasyCodex

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

**EasyCodex** 是一個用來遠端控制 Codex 程式開發智能體的手機應用。專案包含一個 Android 原生行動端，以及一個本機 Node.js WebSocket 本機中繼，用來把手機連接到 `codex app-server`。

行動端應用顯示名稱是 **EasyCodex**。

### 主要功能

- 在手機上控制 Codex 智能體
- 建立並管理多個智能體對話
- 即時查看智能體串流回覆
- 智能體忙碌時可將訊息加入佇列
- 任務完成後接收推播通知
- 掃描 QR Code 連接本機中繼
- 透過本機中繼瀏覽檔案、查看 diff、檢查分支並執行工作區操作

### 專案結構

```text
EasyCodex/
├── mobile/          Android 原生手機應用
├── agent-relay/     Codex 的 Node.js WebSocket 本機中繼
└── scripts/         安裝與輔助腳本
```

### 環境需求

- Node.js 18 或更新版本
- 已安裝並登入 OpenAI Codex CLI
- Android 實體裝置或模擬器
- 電腦與手機在同一個網路，或透過 Tailscale 等私人網路連線

### 快速開始

安裝並啟動本機中繼：

```powershell
Set-Location agent-relay
npm install
npm run dev
```

用 Android Studio 開啟並執行手機應用。如果本機 PATH 已設定 Gradle 和 Android SDK，也可以執行：

```powershell
Set-Location mobile
gradle assembleDebug
```

用手機相機掃描本機中繼在終端裡列印的 QR Code。Android 會開啟 EasyCodex，並自動保存 WebSocket 位址與 API Key。

### 常用指令

```powershell
# 本機中繼
Set-Location agent-relay
npm run build
npm run dev

# Android 應用
Set-Location mobile
gradle assembleDebug
```

### 安全說明

- 不要提交 API Key、中繼金鑰、Token 或私人環境檔案。
- 本機中繼 API Key 應像密碼一樣妥善保管。
