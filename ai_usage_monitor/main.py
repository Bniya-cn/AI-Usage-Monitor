"""
Application entry point.
"""

from __future__ import annotations

import os
import sys
import traceback
from datetime import datetime
from pathlib import Path

from PyQt6.QtCore import QCoreApplication, Qt
from PyQt6.QtWidgets import QApplication

# Ensure local imports work when running as script.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ.setdefault("QT_OPENGL", "software")


def _install_crash_logger():
    log_file = Path.home() / ".ai_usage_monitor" / "crash.log"
    log_file.parent.mkdir(parents=True, exist_ok=True)

    def _write_exc(prefix: str, exc_type, exc_value, exc_tb):
        try:
            with open(log_file, "a", encoding="utf-8") as f:
                f.write(f"\n[{datetime.now().isoformat()}] {prefix}\n")
                traceback.print_exception(exc_type, exc_value, exc_tb, file=f)
        except Exception:
            pass

    def _sys_hook(exc_type, exc_value, exc_tb):
        _write_exc("sys.excepthook", exc_type, exc_value, exc_tb)
        sys.__excepthook__(exc_type, exc_value, exc_tb)

    sys.excepthook = _sys_hook

    if hasattr(sys, "unraisablehook"):
        old_unraisablehook = sys.unraisablehook

        def _unraisable(unraisable):
            _write_exc(
                "sys.unraisablehook",
                type(unraisable.exc_value),
                unraisable.exc_value,
                unraisable.exc_traceback,
            )
            old_unraisablehook(unraisable)

        sys.unraisablehook = _unraisable


def main():
    _install_crash_logger()
    QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseSoftwareOpenGL, True)

    QApplication.setHighDpiScaleFactorRoundingPolicy(
        Qt.HighDpiScaleFactorRoundingPolicy.PassThrough
    )

    app = QApplication(sys.argv)
    app.setApplicationName("AI Usage Monitor")
    app.setOrganizationName("LocalTools")
    app.setQuitOnLastWindowClosed(False)

    import database
    import scheduler
    from collectors.claude_code import ClaudeCodeCollector
    from ui.main_window import MainWindow
    from ui.tray_icon import TrayIcon

    database.init_db()

    window = MainWindow()
    window.show()

    tray = TrayIcon(window, app)
    _ = tray

    scheduler.start()

    # Initial one-time Claude Code scan.
    try:
        cc = ClaudeCodeCollector()
        if cc.is_available():
            for r in cc.collect():
                database.upsert_snapshot(
                    service=r.service,
                    model=r.model,
                    date_str=r.date,
                    tokens_in=r.tokens_in,
                    tokens_out=r.tokens_out,
                    requests=r.requests,
                    cost_usd=r.cost_usd,
                    extra=r.extra,
                )
            database.set_auth_status("claude_code", True)
        window._refresh_cards()
    except Exception:
        pass

    ret = app.exec()
    scheduler.stop()
    sys.exit(ret)


if __name__ == "__main__":
    main()
