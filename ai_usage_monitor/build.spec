# -*- mode: python ; coding: utf-8 -*-
import glob
import os

from PyInstaller.utils.hooks import collect_all, collect_submodules

block_cipher = None


def find_pyqt6_dir():
    try:
        import PyQt6

        return os.path.dirname(PyQt6.__file__)
    except ImportError:
        raise SystemExit("ERROR: PyQt6 is not installed. Run: pip install PyQt6")


pyqt6_dir = find_pyqt6_dir()
qt6_dir = os.path.join(pyqt6_dir, "Qt6")

pyqt6_binaries = []
pyqt6_datas = []

for pyd in glob.glob(os.path.join(pyqt6_dir, "*.pyd")):
    pyqt6_binaries.append((pyd, "PyQt6"))

if os.path.isdir(qt6_dir):
    for root, dirs, files in os.walk(qt6_dir):
        _ = dirs
        for f in files:
            full = os.path.join(root, f)
            rel_dir = os.path.relpath(os.path.dirname(full), pyqt6_dir)
            dest = os.path.join("PyQt6", rel_dir)
            if f.lower().endswith((".dll", ".exe")):
                pyqt6_binaries.append((full, dest))
            else:
                pyqt6_datas.append((full, dest))

pyqtgraph_datas, pyqtgraph_binaries, pyqtgraph_hiddenimports = collect_all("pyqtgraph")
apscheduler_hidden = collect_submodules("apscheduler")
pyqt6_hidden = collect_submodules("PyQt6")

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=pyqt6_binaries + pyqtgraph_binaries,
    datas=pyqt6_datas + pyqtgraph_datas,
    hiddenimports=[
        "collectors.chatgpt_api",
        "collectors.claude_api",
        "collectors.kimi_api",
        "collectors.glm_api",
        "collectors.claude_code",
        "ui.main_window",
        "ui.settings_dialog",
        "ui.service_card",
        "ui.charts",
        "ui.tray_icon",
        "PyQt6.QtWidgets",
        "PyQt6.QtCore",
        "PyQt6.QtGui",
        "sqlite3",
        "anthropic",
        "openai",
        "requests",
        "pyqtgraph",
        "numpy",
    ]
    + pyqt6_hidden
    + pyqtgraph_hiddenimports
    + apscheduler_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=["runtime_hook_qt.py"],
    excludes=["tkinter", "matplotlib", "PIL", "IPython", "jupyter"],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="AI_Usage_Monitor",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="AI_Usage_Monitor",
)
