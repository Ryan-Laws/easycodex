<div align="center">
  <img src="mobile/app/src/main/res/drawable-nodpi/easy_code_app_icon.png" alt="EasyCodex 标识" width="96" height="96">

  <h1>EasyCodex</h1>
  <p><strong>在电脑上启动 Codex，用 Android 手机远程控制。</strong></p>
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
    <a href="https://github.com/Ryan-Laws/easycodex/releases/latest"><strong>下载最新版 EasyCodex</strong></a>
    ·
    <a href="https://github.com/Ryan-Laws/easycodex/releases">全部 Release</a>
  </p>
</div>

EasyCodex 是一个本地优先的 Codex 编程智能体远程控制应用。你在电脑上运行桌面端中继，在 Android 手机上安装移动端，扫码连接后，就可以用手机管理 Codex 会话，而真正的智能体执行仍然留在你的电脑上。

当前公开版本已经提供可直接使用的 Windows、macOS、Linux 中继程序和 Android APK。普通用户不需要克隆仓库才能体验。

## 为什么选择 EasyCodex

OpenAI 已经把 Codex 远程访问带到 ChatGPT 手机应用里，但它目前仍是官方预览体验。EasyCodex 的定位不同：它把手机连接到你自己电脑上的本地中继，不要求把手机控制面放到官方托管控制平面里，并且覆盖更多桌面宿主系统。

| 对比项 | EasyCodex | OpenAI Codex 手机预览 |
| --- | --- | --- |
| 桌面宿主系统 | 提供 Windows、macOS、Linux 中继构建 | OpenAI 2026-05-14 发布说明写明：手机远程访问目前可连接运行在 macOS 上的 Codex；OpenAI 博客说明 Windows 手机连接支持还在 coming soon |
| 手机配对方式 | 通过二维码或 deep link 连接你自己的中继，并使用本地 relay API Key 鉴权 | 集成在 ChatGPT 手机应用里，需要使用 ChatGPT/OpenAI 账号体系 |
| API / Provider 灵活性 | 手机端不硬编码官方账号登录；中继沿用你电脑上已经配置和登录的 Codex CLI 环境，本地 Codex 支持兼容第三方 API 时也可以随本机配置使用 | 绑定 OpenAI 的 ChatGPT/Codex 账号体验 |
| 数据路径 | 手机通过可信 LAN 或 Tailscale 等私有网络连接你的桌面中继；仓库、凭据和执行环境留在本机 | 使用 OpenAI 授权的 ChatGPT 设备和官方中继基础设施 |
| 工作流能力 | 手机审批、查看 diff/Git 状态、选择文件提交、浏览项目和 worktree、发送附件、打开本地 CLI 窗口，并带桌面中继工作台 | 手机启动/继续 thread、审批、改变方向、切换 host，并查看连接宿主的实时上下文 |

当前官方行为来源：[OpenAI 产品文章](https://openai.com/index/work-with-codex-from-anywhere/) 和 [ChatGPT 发布说明](https://help.openai.com/en/articles/6825453-chatgpt-release-notes)。

## 安装 EasyCodex

从 [最新版 EasyCodex Release](https://github.com/Ryan-Laws/easycodex/releases/latest) 下载当前版本。在 GitHub 页面上，也可以从仓库右侧的 **Releases** 入口进入发布页，再选择最新的 EasyCodex 版本。

| 平台 | 下载 | 用途 |
| --- | --- | --- |
| Windows | `EasyCodex.Relay.Setup.*-x64.exe` | 推荐的 Windows 桌面端中继安装包 |
| Windows | `EasyCodex.Relay.Portable.*-x64.exe` | 免安装便携版 Windows 中继 |
| Android | `EasyCodex.Mobile.*.apk` | Android 手机应用 |
| macOS Apple Silicon | `EasyCodex.Relay.*.mac-arm64.dmg` | Apple Silicon 桌面端中继 |
| macOS Intel | `EasyCodex.Relay.*.mac-x64.dmg` | Intel 桌面端中继 |
| Linux | `EasyCodex.Relay.*.linux-x64.AppImage` | 免安装 Linux 中继 |
| Linux | `EasyCodex.Relay.*.linux-x64.deb` | Debian/Ubuntu 安装包 |

## 快速开始

1. 在电脑上安装并登录 OpenAI Codex CLI。
2. 在 Windows、macOS 或 Linux 上安装并打开 **EasyCodex Relay**。
3. 在桌面端应用里点击启动中继。它会显示中继状态、连接地址、API Key 和二维码。
4. 在 Android 手机上安装最新 Release 里的 `EasyCodex.Mobile.*.apk`。
5. 用手机扫描二维码，或在应用内打开连接链接。
6. 选择工作区，创建或恢复 Codex 会话，然后用手机控制智能体。

手机不会直接运行 Codex。它通过 WebSocket 连接到桌面端中继，由中继在本机仓库旁边启动 `codex app-server`。

手机接力和手机单独发起是两种不同模式。接力已有 Codex thread 时，电脑 Codex App 仍是主视图；从手机新开的任务由 EasyCodex relay / `codex app-server` 管理，电脑 Codex App 可能只能从共享 Codex 状态里看到部分历史，不能保证完整展示 app-server 或子 agent 细节。

## 你可以做什么

- 创建、恢复、中断、停止和归档 Codex 智能体会话。
- 查看运行中 agent、活跃 Codex thread、历史可恢复 thread、排队 follow-up 和未读完成状态。
- 在手机上实时查看 Codex 回复、reasoning、命令输出、文件改动、计划和子 agent 活动。
- 发送文本、图片和文件附件；附件会保存到当前工作区的 `.easycodex-attachments/`。
- 使用快捷回复、emoji 面板和 Android 系统语音输入来编写 prompt。
- 先审阅计划，再选择优化计划或开始执行。
- 浏览工作区文件、检查 Git 状态、查看完整/单文件 diff、预览文件，并提交选中的改动文件。
- 浏览受信工作区、relay-managed repos 和 Git worktree。
- 在本地操作执行前，通过手机处理审批提示和结构化 user-input 问题；主 agent 支持默认审核、Codex 自动审核和完全开放三种权限模式，完全开放时不再弹权限审核。
- 打开多个手机 CLI 窗口，每个窗口对应独立的 `codex exec`，支持 resume/review、profile、图片、额外目录、JSON 输出、ephemeral、ignore-rules、sandbox 和 Git repo check 开关。
- 选择模型、reasoning effort、service tier、cwd、主 agent 权限模式、CLI sandbox mode 和更新通道。
- 接收本地通知和可选手机推送，并同步每个 agent 的通知级别和最近通知历史。
- 在 Android 端检查稳定版/Beta APK 更新，在桌面中继端检查 relay/安装包更新。
- 使用桌面中继 workbench 查看任务、阅读对话、发送 follow-up、处理审批，并查看 Git 状态/diff。
- 通过二维码或 `easycodex://connect` 深链接快速配对。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android 应用 | Kotlin、原生 Android、Jetpack Compose、Material 3、OkHttp、Google Code Scanner |
| 桌面端中继 | Electron、electron-builder、内置本地中继启动器 |
| Agent Relay | Node.js 18+、TypeScript、Express、`ws`、`simple-git`、Codex `app-server` JSON-RPC |
| 开发工具 | PowerShell 友好的 Node.js 脚本和 GitHub Actions 发布构建 |

## 架构

```text
Android app <-> EasyCodex Relay <-> codex app-server <-> Codex thread
```

中继采用本地优先设计。它使用 relay API Key 验证移动端客户端，启动和管理 Codex 进程，转换 Codex JSON-RPC 事件，并向应用暴露明确的文件、Git、工作区操作。

## 环境要求

- 用于运行桌面端中继的 Windows、macOS 或 Linux 电脑
- Android 真机或模拟器
- 电脑上已安装并登录 OpenAI Codex CLI
- 手机和电脑在同一个可信网络内，或通过 Tailscale 等私有网络连接

## 从源码构建

大多数用户应该从 Release 页面安装。只有在开发 EasyCodex 本身时才需要这些命令。

```powershell
# 桌面端中继
Set-Location desktop-relay
npm install
npm start

# Agent Relay
Set-Location agent-relay
npm install
npm run build

# Android 应用
Set-Location mobile
gradle assembleDebug
```

## 仓库结构

```text
EasyCodex/
├── mobile/          Android 原生手机应用
├── agent-relay/     Codex 的 Node.js Agent Relay
├── desktop-relay/   Electron Windows/macOS/Linux 桌面端中继
└── scripts/         CLI 和本地安装辅助脚本
```

## 安全模型

- 中继要求 WebSocket 客户端和健康检查提供 API Key。
- 请把中继访问视为高权限访问：它可以读取工作区文件、检查 Git 状态，并在工作目录中启动 Codex。
- 日常使用建议放在可信 LAN 或私有网络内。
- 不要提交 API Key、中继密钥、OpenAI Token、本地 agent 状态或私有环境文件。

## 文档

- [APP.md](APP.md) 介绍应用架构和本地运行模型。
- [AGENT.md](AGENT.md) 说明由中继管理的 Codex 智能体、WebSocket action 和运行时行为。
- [desktop-relay/README.md](desktop-relay/README.md) 说明桌面端中继的打包和发布构建。

## 许可证

EasyCodex 基于 [MIT License](LICENSE) 发布。
