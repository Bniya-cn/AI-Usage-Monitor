"""
Main dashboard window.
"""

from __future__ import annotations

import threading
from datetime import datetime
from typing import Any, Callable

from PyQt6.QtCore import QObject, QThread, QTimer, pyqtSignal
from PyQt6.QtGui import QFont
from PyQt6.QtWidgets import (
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QProgressBar,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

import database
from .charts import UsageChart
from .service_card import ServiceCard
from .settings_dialog import SettingsDialog

SERVICES = [
    ("chatgpt_api", "ChatGPT API"),
    ("claude_api", "Claude API"),
    ("kimi_api", "KIMI API"),
    ("glm_api", "GLM API"),
    ("claude_code", "Claude Code"),
]


class SyncWorker(QObject):
    """Background sync worker."""

    finished = pyqtSignal(str, bool, str)  # service_id, success, error
    all_done = pyqtSignal()

    def __init__(self, services_to_sync: list[str], per_service_timeout_sec: int = 25):
        super().__init__()
        self._services = services_to_sync
        self._per_service_timeout_sec = max(5, int(per_service_timeout_sec))

    def _call_with_timeout(self, fn: Callable[[], Any], action_name: str):
        result_box: dict[str, Any] = {}
        error_box: dict[str, Exception] = {}
        done = threading.Event()

        def _runner():
            try:
                result_box["value"] = fn()
            except Exception as exc:  # pragma: no cover
                error_box["error"] = exc
            finally:
                done.set()

        t = threading.Thread(target=_runner, daemon=True, name=f"sync-{action_name}")
        t.start()

        if not done.wait(self._per_service_timeout_sec):
            raise TimeoutError(f"{action_name} timed out after {self._per_service_timeout_sec}s")
        if "error" in error_box:
            raise error_box["error"]
        return result_box.get("value")

    def run(self):
        try:
            self._do_sync()
        finally:
            self.all_done.emit()

    def _do_sync(self):
        from collectors.chatgpt_api import ChatGPTApiCollector
        from collectors.claude_api import ClaudeApiCollector
        from collectors.claude_code import ClaudeCodeCollector
        from collectors.glm_api import GLMApiCollector
        from collectors.kimi_api import KimiApiCollector

        collector_map = {
            "chatgpt_api": ChatGPTApiCollector(),
            "claude_api": ClaudeApiCollector(),
            "kimi_api": KimiApiCollector(),
            "glm_api": GLMApiCollector(),
            "claude_code": ClaudeCodeCollector(),
        }

        for svc_id in self._services:
            try:
                collector = collector_map.get(svc_id)
                if not collector:
                    continue

                available = bool(self._call_with_timeout(collector.is_available, f"{svc_id}.is_available"))
                if not available:
                    database.set_auth_status(svc_id, False, "missing_credentials")
                    self.finished.emit(svc_id, False, "missing_credentials")
                    continue

                records = self._call_with_timeout(collector.collect, f"{svc_id}.collect") or []
                for r in records:
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

                database.set_auth_status(svc_id, True)
                self.finished.emit(svc_id, True, "")
            except Exception as e:
                err_msg = str(e)
                auth_error_keywords = (
                    "invalid",
                    "unauthorized",
                    "not authenticated",
                    "401",
                    "403",
                    "missing_credentials",
                )
                if any(kw in err_msg.lower() for kw in auth_error_keywords):
                    database.set_auth_status(svc_id, False, err_msg[:200])
                self.finished.emit(svc_id, False, err_msg[:200])


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("AI Usage Monitor")
        self.setMinimumSize(980, 680)

        self._cards: dict[str, ServiceCard] = {}
        self._sync_thread: QThread | None = None
        self._worker = None
        self._sync_btn: QPushButton | None = None
        self._sync_failures: list[str] = []
        self._sync_timed_out = False
        self._sync_total = 0
        self._sync_done = 0
        self._selected_service = SERVICES[0][0]

        self._sync_watchdog = QTimer(self)
        self._sync_watchdog.setSingleShot(True)
        self._sync_watchdog.timeout.connect(self._on_sync_timeout)

        self._refresh_timer = QTimer(self)
        self._refresh_timer.timeout.connect(self._refresh_cards)
        self._refresh_timer.start(60_000)

        self._apply_dark_style()
        self._setup_ui()
        self._refresh_cards()
        self._select_service(self._selected_service)

    def _apply_dark_style(self):
        self.setStyleSheet(
            """
            QMainWindow, QWidget {
                background-color: #0f0f1a;
                color: #e2e8f0;
                font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
            }
            QPushButton {
                background-color: #1e1e2e;
                color: #e2e8f0;
                border: 1px solid #334155;
                border-radius: 6px;
                padding: 6px 14px;
            }
            QPushButton:hover { background-color: #2d2d3f; }
            QPushButton:pressed { background-color: #3d3d55; }
            QLabel { color: #e2e8f0; }
            QProgressBar {
                border: 1px solid #334155;
                border-radius: 5px;
                background: #111827;
                color: #cbd5e1;
                text-align: center;
            }
            QProgressBar::chunk {
                background-color: #2563eb;
                border-radius: 4px;
            }
            """
        )

    def _setup_ui(self):
        central = QWidget()
        self.setCentralWidget(central)

        root = QVBoxLayout(central)
        root.setContentsMargins(16, 12, 16, 12)
        root.setSpacing(12)

        header = QHBoxLayout()
        title = QLabel("AI Usage Monitor")
        title.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        header.addWidget(title)
        header.addStretch()

        self.sync_status_label = QLabel("")
        self.sync_status_label.setStyleSheet("color: #94a3b8; font-size: 11px;")
        header.addWidget(self.sync_status_label)

        self._sync_btn = QPushButton("Sync Now")
        self._sync_btn.setStyleSheet("background:#1d4ed8; color:white; padding:6px 14px; border-radius:6px;")
        self._sync_btn.clicked.connect(self._start_sync)
        header.addWidget(self._sync_btn)

        settings_btn = QPushButton("Settings")
        settings_btn.clicked.connect(self._open_settings)
        header.addWidget(settings_btn)
        root.addLayout(header)

        self.progress = QProgressBar()
        self.progress.setVisible(False)
        root.addWidget(self.progress)

        cards_widget = QWidget()
        grid = QGridLayout(cards_widget)
        grid.setSpacing(10)

        for i, (svc_id, display_name) in enumerate(SERVICES):
            card = ServiceCard(svc_id, display_name)
            card.clicked.connect(self._select_service)
            self._cards[svc_id] = card
            grid.addWidget(card, i // 3, i % 3)

        root.addWidget(cards_widget)

        line = QFrame()
        line.setFrameShape(QFrame.Shape.HLine)
        line.setStyleSheet("color: #1e293b;")
        root.addWidget(line)

        self.chart = UsageChart()
        root.addWidget(self.chart)

    def _select_service(self, service_id: str):
        self._selected_service = service_id
        for sid, card in self._cards.items():
            card.set_selected(sid == service_id)
        self.chart.set_service(service_id)
        self.chart.refresh()

    def _refresh_cards(self):
        for svc_id, _ in SERVICES:
            summary = database.get_summary(svc_id)
            auth = database.get_auth_status(svc_id)
            is_auth = bool(auth.get("is_auth")) or (svc_id == "claude_code")
            last_sync = summary.get("last_sync", "")
            card = self._cards[svc_id]
            card.update_data(summary, is_auth, last_sync)

    def _start_sync(self):
        if self._sync_thread and self._sync_thread.isRunning():
            self.sync_status_label.setText("Sync already running")
            return

        self._sync_btn.setEnabled(False)
        self._sync_btn.setText("Syncing...")
        self.sync_status_label.setText("")
        self._sync_failures.clear()
        self._sync_timed_out = False

        services = [svc_id for svc_id, _ in SERVICES]
        self._sync_total = len(services)
        self._sync_done = 0
        self.progress.setMaximum(self._sync_total)
        self.progress.setValue(0)
        self.progress.setFormat("0 / %m")
        self.progress.setVisible(True)

        for card in self._cards.values():
            card.set_busy(True)

        self._worker = SyncWorker(services)
        thread = QThread(self)
        self._worker.moveToThread(thread)
        thread.started.connect(self._worker.run)
        self._worker.finished.connect(self._on_service_synced)
        self._worker.all_done.connect(self._on_all_synced)
        self._worker.all_done.connect(thread.quit)
        thread.finished.connect(thread.deleteLater)
        self._sync_thread = thread
        thread.start()

        self._sync_watchdog.start(120_000)

    def _on_service_synced(self, svc_id: str, success: bool, error_msg: str):
        self._sync_done += 1
        self.progress.setValue(self._sync_done)
        self.progress.setFormat(f"{self._sync_done} / {self._sync_total}")

        if (not success) and error_msg:
            self._sync_failures.append(f"{svc_id}: {error_msg}")

        summary = database.get_summary(svc_id)
        auth = database.get_auth_status(svc_id)
        is_auth = bool(auth.get("is_auth")) or (svc_id == "claude_code")
        last_sync = summary.get("last_sync", "")
        self._cards[svc_id].set_busy(False)
        self._cards[svc_id].update_data(summary, is_auth, last_sync)

    def _on_all_synced(self):
        self._sync_watchdog.stop()
        now = datetime.now().strftime("%H:%M")

        if self._sync_timed_out:
            self.sync_status_label.setText(f"Sync recovered ({now})")
        elif self._sync_failures:
            self.sync_status_label.setText(f"{len(self._sync_failures)} service(s) failed ({now})")
        else:
            self.sync_status_label.setText(f"Last synced: {now}")

        if self._sync_btn:
            self._sync_btn.setEnabled(True)
            self._sync_btn.setText("Sync Now")

        self.progress.setValue(self._sync_total)
        self._refresh_cards()
        self._sync_timed_out = False
        self._worker = None
        self._sync_thread = None

    def _on_sync_timeout(self):
        if self._sync_thread and self._sync_thread.isRunning():
            self.sync_status_label.setText("Sync timeout: UI recovered, you can retry.")
            if self._sync_btn:
                self._sync_btn.setEnabled(True)
                self._sync_btn.setText("Sync Now")
            self._sync_timed_out = True

    def _open_settings(self):
        dlg = SettingsDialog(self)
        dlg.settings_changed.connect(self._refresh_cards)
        dlg.exec()
