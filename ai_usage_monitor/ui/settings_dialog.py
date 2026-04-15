"""
Settings dialog for API keys and sync options.
"""

from __future__ import annotations

from typing import Dict

from PyQt6.QtCore import pyqtSignal
from PyQt6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

import config
import database


class SettingsDialog(QDialog):
    settings_changed = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("设置 - AI Usage Monitor")
        self.setMinimumSize(560, 480)

        self._key_edits: Dict[str, QLineEdit] = {}
        self._status_labels: Dict[str, QLabel] = {}

        self._setup_ui()
        self._load_current_values()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        tabs = QTabWidget()

        tabs.addTab(
            self._make_api_tab(
                service_id="chatgpt_api",
                title="ChatGPT API",
                placeholder="sk-...",
                hint="使用 OpenAI API Key，支持组织用量读取。",
            ),
            "ChatGPT API",
        )
        tabs.addTab(
            self._make_api_tab(
                service_id="claude_api",
                title="Claude API",
                placeholder="sk-ant-...",
                hint="Anthropic API Key。",
            ),
            "Claude API",
        )
        tabs.addTab(
            self._make_api_tab(
                service_id="kimi_api",
                title="KIMI API",
                placeholder="sk-...",
                hint="Moonshot(KIMI) API Key。",
            ),
            "KIMI API",
        )
        tabs.addTab(
            self._make_api_tab(
                service_id="glm_api",
                title="GLM API",
                placeholder="...",
                hint="智谱 GLM API Key。",
            ),
            "GLM API",
        )
        tabs.addTab(self._make_claude_code_tab(), "Claude Code")
        tabs.addTab(self._make_general_tab(), "通用设置")

        layout.addWidget(tabs)

        btns = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        btns.rejected.connect(self.reject)
        layout.addWidget(btns)

    def _make_api_tab(self, service_id: str, title: str, placeholder: str, hint: str) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(12)

        grp = QGroupBox(f"{title} Key")
        form = QFormLayout(grp)

        edit = QLineEdit()
        edit.setPlaceholderText(placeholder)
        edit.setEchoMode(QLineEdit.EchoMode.Password)
        form.addRow("API Key:", edit)

        status = QLabel("未配置")
        status.setStyleSheet("color: #ef4444;")
        form.addRow("状态:", status)

        save_btn = QPushButton("保存")
        save_btn.clicked.connect(lambda: self._save_api_key(service_id))
        clear_btn = QPushButton("清除")
        clear_btn.clicked.connect(lambda: self._clear_service(service_id))

        btn_row = QHBoxLayout()
        btn_row.addStretch()
        btn_row.addWidget(clear_btn)
        btn_row.addWidget(save_btn)
        form.addRow("", btn_row)

        layout.addWidget(grp)
        layout.addWidget(QLabel(hint, styleSheet="color:#64748b; font-size:11px;"))
        layout.addStretch()

        self._key_edits[service_id] = edit
        self._status_labels[service_id] = status
        return w

    def _make_claude_code_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(10)

        info = QLabel(
            "Claude Code 使用本地会话文件统计，不需要 API Key。\n"
            "该来源会按设置中的扫描间隔自动更新。"
        )
        info.setStyleSheet("color:#cbd5e1; font-size:12px;")
        info.setWordWrap(True)
        layout.addWidget(info)

        force_btn = QPushButton("标记为可用")
        force_btn.clicked.connect(lambda: self._mark_claude_code_ready())
        layout.addWidget(force_btn)
        layout.addStretch()
        return w

    def _mark_claude_code_ready(self):
        database.set_auth_status("claude_code", True)
        self.settings_changed.emit()
        QMessageBox.information(self, "完成", "Claude Code 已标记为可用。")

    def _make_general_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(12)

        cfg = config.load_config()

        form = QFormLayout()
        self.sync_interval_edit = QLineEdit(str(cfg.get("sync_interval_minutes", 30)))
        form.addRow("API 同步间隔（分钟）:", self.sync_interval_edit)

        self.cc_interval_edit = QLineEdit(str(cfg.get("claude_code_sync_interval_minutes", 5)))
        form.addRow("Claude Code 扫描间隔（分钟）:", self.cc_interval_edit)

        layout.addLayout(form)

        save_btn = QPushButton("保存通用设置")
        save_btn.clicked.connect(self._save_general)
        layout.addWidget(save_btn)

        layout.addStretch()
        return w

    def _save_api_key(self, service_id: str):
        edit = self._key_edits[service_id]
        key = edit.text().strip()
        if not key:
            QMessageBox.warning(self, "提示", "请输入 API Key。")
            return

        config.set_api_key(service_id, key)
        database.set_auth_status(service_id, True)

        lbl = self._status_labels[service_id]
        lbl.setText("已保存")
        lbl.setStyleSheet("color: #22c55e;")
        self.settings_changed.emit()

    def _save_general(self):
        cfg = config.load_config()
        try:
            cfg["sync_interval_minutes"] = int(self.sync_interval_edit.text())
            cfg["claude_code_sync_interval_minutes"] = int(self.cc_interval_edit.text())
        except ValueError:
            QMessageBox.warning(self, "格式错误", "请输入有效的整数分钟数。")
            return

        config.save_config(cfg)
        QMessageBox.information(self, "已保存", "通用设置已保存，将在下次启动后按新间隔运行。")

    def _clear_service(self, service: str):
        config.clear_credentials(service)
        database.set_auth_status(service, False)
        self._load_current_values()
        self.settings_changed.emit()

    def _load_current_values(self):
        for service_id, edit in self._key_edits.items():
            key = config.get_api_key(service_id)
            status_lbl = self._status_labels[service_id]
            if key:
                edit.setText(key)
                status_lbl.setText("已配置")
                status_lbl.setStyleSheet("color: #22c55e;")
            else:
                edit.clear()
                status_lbl.setText("未配置")
                status_lbl.setStyleSheet("color: #ef4444;")
