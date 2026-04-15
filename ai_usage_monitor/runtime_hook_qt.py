"""
runtime_hook_qt.py
在打包后的 exe 启动时自动运行，设置 Qt 插件搜索路径
"""
import os
import sys

# 兜底环境变量，避免第三方模块在启动阶段对 None 调用 .split()
if os.environ.get("PATH") is None:
    os.environ["PATH"] = ""
if os.environ.get("path") is None:
    os.environ["path"] = os.environ.get("PATH", "")
if os.environ.get("PATHEXT") is None:
    os.environ["PATHEXT"] = ".COM;.EXE;.BAT;.CMD"

# 图形层稳定性兜底：
# 部分 Windows 环境在 QWebEngine/窗口切换时会在 qwindows.dll 崩溃，强制软件渲染可显著降低概率。
os.environ.setdefault("QT_OPENGL", "software")
_chromium_flags = os.environ.get("QTWEBENGINE_CHROMIUM_FLAGS", "")
if "--disable-gpu" not in _chromium_flags:
    os.environ["QTWEBENGINE_CHROMIUM_FLAGS"] = (
        f"{_chromium_flags} --disable-gpu".strip()
    )

if getattr(sys, "frozen", False):
    base = sys._MEIPASS
    qt6_dir = os.path.join(base, "PyQt6", "Qt6")
    plugins_dir = os.path.join(qt6_dir, "plugins")
    if os.path.isdir(plugins_dir):
        os.environ["QT_PLUGIN_PATH"] = plugins_dir
        os.environ["QT_QPA_PLATFORM_PLUGIN_PATH"] = os.path.join(plugins_dir, "platforms")
