# EasyCodex

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

**EasyCodex** 是一个用于远程控制 Codex 编程智能体的手机应用。项目包含一个 Android 原生移动端，以及一个本地 Node.js WebSocket 本地中继，用来把手机连接到 `codex app-server`。

移动端应用显示名是 **EasyCodex**。

### 主要功能

- 在手机上控制 Codex 智能体
- 创建和管理多个智能体会话
- 实时查看智能体流式回复
- 智能体忙碌时可将消息加入队列
- 任务完成后接收推送通知
- 扫描二维码连接本地中继
- 通过本地中继浏览文件、查看 diff、检查分支并执行工作区操作

### 项目结构

```text
EasyCodex/
├── mobile/          Android 原生手机应用
├── agent-relay/     Codex 的 Node.js WebSocket 本地中继
└── scripts/         安装和辅助脚本
```

### 环境要求

- Node.js 18 或更高版本
- 已安装并登录 OpenAI Codex CLI
- Android 真机或模拟器
- 电脑和手机在同一网络，或通过 Tailscale 等私有网络连接

### 快速开始

安装并启动本地中继：

```powershell
Set-Location agent-relay
npm install
npm run dev
```

用 Android Studio 打开并运行手机应用。如果本机 PATH 已配置 Gradle 和 Android SDK，也可以运行：

```powershell
Set-Location mobile
gradle assembleDebug
```

用手机相机扫描本地中继在终端里打印的二维码。Android 会打开 EasyCodex，并自动保存 WebSocket 地址和 API Key。

### 常用命令

```powershell
# 本地中继
Set-Location agent-relay
npm run build
npm run dev

# Android 应用
Set-Location mobile
gradle assembleDebug
```

### 安全说明

- 不要提交 API Key、中继密钥、Token 或私有环境文件。
- 本地中继 API Key 应当像密码一样保管。
