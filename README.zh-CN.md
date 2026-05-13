<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex 标识" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>让 Codex 在电脑上运行，用 Android 手机远程掌控。</strong></p>
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

EasyCodex 可以把 Android 手机变成 Codex 编程智能体的远程控制台。Codex 运行时和本地中继仍然留在你自己的电脑和代码仓库旁边，手机端则提供原生移动界面，用来启动会话、和智能体对话、跟进长时间任务、查看文件和 diff，并响应审批请求。

它适合希望 Codex 在本地持续工作，同时又不想一直守在终端窗口前的开发者。

## 项目数据

| 指标 | Badge |
| --- | --- |
| 访问量 | ![Visitors](https://visitor-badge.laobi.icu/badge?page_id=Ryan-Laws.easycodex) |
| Stars | ![GitHub stars](https://img.shields.io/github/stars/Ryan-Laws/easycodex?style=social) |
| Forks | ![GitHub forks](https://img.shields.io/github/forks/Ryan-Laws/easycodex?style=social) |
| 最新 Release | ![GitHub release](https://img.shields.io/github/v/release/Ryan-Laws/easycodex?include_prereleases) |
| License | ![GitHub license](https://img.shields.io/github/license/Ryan-Laws/easycodex) |
| 桌面端发布流程 | ![GitHub Actions workflow status](https://img.shields.io/github/actions/workflow/status/Ryan-Laws/easycodex/release-desktop-relay.yml?branch=main) |

## 为什么使用 EasyCodex

- 让 Codex 智能体继续在电脑上运行，同时用手机控制它们。
- 不必一直坐在电脑前，也能跟进流式回复和长时间编码任务。
- 在原生 Android 界面中管理多个智能体、Codex 线程、工作目录、模型和审批设置。
- 在做决定前查看工作区文件、diff、分支和 Git worktree。
- 敏感执行保持在本地：中继运行在你的仓库旁边，并要求客户端提供 API Key。

## 功能特性

- 创建、恢复、中断和停止多个 Codex 智能体会话。
- 实时流式接收来自 `codex app-server` 的智能体回复。
- 智能体忙碌时可将消息加入队列。
- 在手机上响应 Codex 请求的操作审批。
- 浏览文件、读取工作区内容、查看 Git diff、检查分支，并使用 Git worktree。
- 通过扫描二维码或打开 `easycodex://connect` 深链接完成连接。
- 在任务完成时接收本地中继事件和可选手机通知。
- 使用 Windows 和 macOS 的 Electron 桌面端启动并监控本地中继。
- 偏好命令行时，也可以使用终端优先的安装脚本。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android 应用 | Kotlin、原生 Android、Jetpack Compose、Material 3、OkHttp、Google Code Scanner |
| Agent Relay | Node.js 18+、TypeScript、Express、`ws`、`simple-git`、Codex `app-server` JSON-RPC |
| 桌面端中继 | Electron、electron-builder、本地二维码连接界面 |
| CLI 与安装脚本 | Node.js ESM 脚本，命令对 PowerShell 友好 |

## 架构

```text
Android app <-> Agent Relay <-> codex app-server <-> Codex thread
```

手机不会直接启动 Codex。它会通过 WebSocket 和 relay API Key 连接到本地 Agent Relay。中继负责认证客户端、启动和管理 `codex app-server`、转换 JSON-RPC 事件，并向应用暴露明确的文件、Git、仓库、模型和运行时操作。

## 快速开始

### 1. 使用桌面端中继

```powershell
Set-Location desktop-relay
npm install
npm start
```

桌面端中继会提供一个本地控制窗口，展示中继状态、连接信息和用于手机配对的二维码。

### 2. 使用终端安装流程

在仓库根目录运行：

```powershell
node scripts/setup-and-start.mjs
```

脚本会询问中继端口，在你留空时自动生成 API Key，可选安装依赖，启动中继，并打印二维码连接信息。

### 3. 手动运行全部组件

启动 Agent Relay：

```powershell
Set-Location agent-relay
npm install
npm run dev
```

用 Android Studio 构建或运行 Android 应用。如果本机 `PATH` 已配置 Gradle 和 Android SDK，也可以运行：

```powershell
Set-Location mobile
gradle assembleDebug
```

用手机相机扫描中继打印的二维码。Android 会打开 EasyCodex，并自动保存 WebSocket 地址和 API Key。

## 环境要求

- Node.js 18 或更高版本
- 已安装并登录 OpenAI Codex CLI
- Android 真机或模拟器
- 电脑和手机在同一个可信网络内，或通过 Tailscale 等私有网络连接
- 用于构建移动端的 Android Studio，或已配置好的 Android SDK/Gradle 环境

## 仓库结构

```text
EasyCodex/
├── mobile/          Android 原生手机应用
├── agent-relay/     Codex 的 Node.js Agent Relay
├── desktop-relay/   Electron Windows/macOS 桌面端中继
└── scripts/         CLI 和本地安装辅助脚本
```

## 常用命令

```powershell
# Agent Relay
Set-Location agent-relay
npm run build
npm run dev

# 桌面端中继
Set-Location desktop-relay
npm start
npm run dist:win

# Android 应用
Set-Location mobile
gradle assembleDebug
```

## 安全模型

- 中继要求 WebSocket 客户端和健康检查提供 API Key。
- 请把中继访问视为高权限访问：它可以读取工作区文件、检查 Git 状态，并在工作目录中启动 Codex。
- 日常使用建议放在可信 LAN 或私有网络内。
- 如果要在私有网络之外暴露中继，请通过正确配置的反向代理使用 `wss://`。
- 不要提交 API Key、中继密钥、OpenAI Token 或私有环境文件。

## 文档

- [APP.md](APP.md) 介绍应用架构和本地运行模型。
- [AGENT.md](AGENT.md) 说明由中继管理的 Codex 智能体、WebSocket action 和运行时行为。
- [desktop-relay/README.md](desktop-relay/README.md) 说明桌面端中继的打包和发布构建。

## 贡献

欢迎贡献。请保持改动聚焦，遵循现有项目结构，除非能明确改善功能或维护体验，否则不要引入新的依赖。

提交 Pull Request 前，请针对你修改的区域运行最相关的检查：

```powershell
# Relay
Set-Location agent-relay
npm run build

# Android
Set-Location mobile
gradle assembleDebug

# 桌面端中继
Set-Location desktop-relay
npm run dist:win
```

如果修改了中继智能体行为或 WebSocket action，请更新 [AGENT.md](AGENT.md)。如果修改了应用架构，请更新 [APP.md](APP.md)。

## 许可证

EasyCodex 基于 [MIT License](LICENSE) 发布。
