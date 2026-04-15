# AI Usage Monitor

> **Note**: This project was built entirely with AI Coding tools (Claude Code / Cursor), not hand-coded.
>
> **说明**：本项目完全由 AI 编程工具（Claude Code / Cursor）开发，非个人手写代码。

A desktop application to monitor and visualize your AI API usage across multiple services — ChatGPT, Claude, Kimi (Moonshot), and GLM (Zhipu).

一款桌面应用，用于监控和可视化你在多个 AI 服务上的 API 用量——支持 ChatGPT、Claude、Kimi（月之暗面）和 GLM（智谱）。

---

## Features / 功能特性

- **Multi-service monitoring** — Track usage for ChatGPT API, Claude API, Kimi API, GLM API, and Claude Code (local log parsing)
- **Real-time dashboard** — View token usage, request counts, and estimated costs at a glance
- **Usage charts** — Visualize daily/weekly/monthly trends with interactive graphs
- **Auto-sync** — Periodically fetches the latest usage data in the background (configurable interval)
- **System tray** — Runs minimized in the tray; desktop notifications for quota alerts
- **Dark theme** — Clean, modern dark UI built with PyQt6

---

- **多服务监控** — 追踪 ChatGPT API、Claude API、Kimi API、GLM API 以及 Claude Code（本地日志解析）的用量
- **实时仪表盘** — 一目了然查看 Token 用量、请求次数和预估费用
- **用量图表** — 通过交互式图表可视化每日/每周/每月趋势
- **自动同步** — 后台定时拉取最新用量数据（可自定义间隔）
- **系统托盘** — 最小化到托盘运行，配额警告桌面通知
- **深色主题** — 基于 PyQt6 的简洁现代深色界面

## Quick Start / 快速开始

### Option A: Download the release (Windows) / 下载发布版

1. Go to [Releases](https://github.com/Bniya-cn/AI-Usage-Monitor/releases) and download `AI_Usage_Monitor_Windows_x64.zip`
2. Extract and run `AI_Usage_Monitor.exe`
3. Configure your API keys in **Settings**

---

1. 前往 [Releases](https://github.com/Bniya-cn/AI-Usage-Monitor/releases) 下载 `AI_Usage_Monitor_Windows_x64.zip`
2. 解压后运行 `AI_Usage_Monitor.exe`
3. 在 **设置** 中配置你的 API 密钥

### Option B: Run from source / 从源码运行

```bash
# Clone the repository / 克隆仓库
git clone https://github.com/Bniya-cn/AI-Usage-Monitor.git
cd AI-Usage-Monitor

# Install dependencies / 安装依赖
pip install -r requirements.txt

# Run / 运行
python main.py
```

## Project Structure / 项目结构

```
ai_usage_monitor/
├── main.py                 # Entry point / 程序入口
├── config.py               # Configuration & credential storage / 配置与凭据管理
├── database.py             # SQLite database layer / SQLite 数据库层
├── scheduler.py            # Background sync scheduler / 后台同步调度器
├── requirements.txt        # Python dependencies / Python 依赖
├── build.bat               # PyInstaller build script / 打包脚本
├── build.spec              # PyInstaller spec file / 打包配置
├── runtime_hook_qt.py      # Qt runtime hook / Qt 运行时钩子
├── collectors/             # API data collectors / API 数据采集器
│   ├── base_collector.py   # Base class / 基类
│   ├── chatgpt_api.py      # ChatGPT (OpenAI) collector
│   ├── claude_api.py       # Claude (Anthropic) collector
│   ├── claude_code.py      # Claude Code local log collector
│   ├── kimi_api.py         # Kimi (Moonshot) collector
│   └── glm_api.py          # GLM (Zhipu) collector
└── ui/                     # User interface / 用户界面
    ├── main_window.py      # Main window / 主窗口
    ├── charts.py           # Usage charts / 用量图表
    ├── service_card.py     # Service status cards / 服务状态卡片
    ├── settings_dialog.py  # Settings dialog / 设置对话框
    └── tray_icon.py        # System tray icon / 系统托盘图标
```

## Supported Services / 支持的服务

| Service / 服务 | Method / 方式 | What's tracked / 追踪内容 |
|---|---|---|
| ChatGPT API | OpenAI Usage API | Tokens, cost, requests / Token数、费用、请求数 |
| Claude API | Anthropic API | Tokens, cost, requests |
| Kimi API | Moonshot API | Tokens, cost, requests |
| GLM API | Zhipu API | Tokens, cost, requests |
| Claude Code | Local log parsing / 本地日志解析 | Tokens, cost per session / 每次会话的Token和费用 |

## Configuration / 配置说明

User data is stored in `~/.ai_usage_monitor/`:

用户数据保存在 `~/.ai_usage_monitor/` 目录下：

- `config.json` — App settings (sync interval, theme, etc.) / 应用设置（同步间隔、主题等）
- `credentials.json` — API keys (base64 encoded) / API 密钥（base64 编码）
- `usage.db` — SQLite database for usage history / 用量历史 SQLite 数据库

## Tech Stack / 技术栈

- Python 3.10+
- PyQt6 — GUI framework / 图形界面框架
- pyqtgraph — Charts & data visualization / 图表和数据可视化
- APScheduler — Background task scheduling / 后台任务调度
- SQLite — Local data storage / 本地数据存储
- PyInstaller — Windows packaging / Windows 打包

## License / 许可证

[MIT License](LICENSE)

## Disclaimer / 免责声明

This project was developed using AI Coding tools as an experiment in AI-assisted software development. It is provided as-is for educational and personal use.

本项目使用 AI 编程工具开发，作为 AI 辅助软件开发的实验项目。仅供学习和个人使用，按原样提供。
