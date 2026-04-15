"""
Reusable service card widget.
"""

from __future__ import annotations

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QColor
from PyQt6.QtWidgets import QFrame, QGraphicsDropShadowEffect, QHBoxLayout, QLabel, QVBoxLayout

SERVICE_COLORS = {
    "chatgpt_api": "#10a37f",
    "claude_api": "#d97706",
    "kimi_api": "#0ea5e9",
    "glm_api": "#22c55e",
    "claude_code": "#7c3aed",
}

SERVICE_ICONS = {
    "chatgpt_api": "C",
    "claude_api": "A",
    "kimi_api": "K",
    "glm_api": "G",
    "claude_code": "CC",
}


class ServiceCard(QFrame):
    """Single service usage card."""

    sync_requested = pyqtSignal(str)
    clicked = pyqtSignal(str)

    def __init__(self, service_id: str, display_name: str, parent=None):
        super().__init__(parent)
        self.service_id = service_id
        self.display_name = display_name
        self._selected = False
        self._hovered = False
        self._busy = False
        self._setup_ui()
        self._apply_style()

    def _setup_ui(self):
        color = SERVICE_COLORS.get(self.service_id, "#6b7280")
        icon = SERVICE_ICONS.get(self.service_id, "?")

        self.setObjectName("ServiceCard")
        self.setMinimumWidth(220)
        self.setMinimumHeight(152)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(18)
        shadow.setOffset(0, 4)
        shadow.setColor(QColor(0, 0, 0, 120))
        self.setGraphicsEffect(shadow)
        self._shadow = shadow

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(6)

        title_row = QHBoxLayout()
        self.title_label = QLabel(f"[{icon}]  {self.display_name}")
        self.title_label.setStyleSheet(f"color: {color}; font-weight: 700; font-size: 13px;")
        title_row.addWidget(self.title_label)
        title_row.addStretch()

        self.status_dot = QLabel("●")
        self.status_dot.setStyleSheet("color: #6b7280; font-size: 10px;")
        title_row.addWidget(self.status_dot)
        layout.addLayout(title_row)

        self.primary_value = QLabel("--")
        self.primary_value.setStyleSheet("color: #e2e8f0; font-size: 22px; font-weight: 800;")
        layout.addWidget(self.primary_value)

        self.primary_label = QLabel("This Month Tokens")
        self.primary_label.setStyleSheet("color: #94a3b8; font-size: 11px;")
        layout.addWidget(self.primary_label)

        secondary_row = QHBoxLayout()
        self.secondary_left = QLabel("")
        self.secondary_left.setStyleSheet("color: #64748b; font-size: 11px;")
        self.secondary_right = QLabel("")
        self.secondary_right.setStyleSheet("color: #64748b; font-size: 11px;")
        secondary_row.addWidget(self.secondary_left)
        secondary_row.addStretch()
        secondary_row.addWidget(self.secondary_right)
        layout.addLayout(secondary_row)

        layout.addStretch()

        self.sync_label = QLabel("Never synced")
        self.sync_label.setStyleSheet("color: #475569; font-size: 10px;")
        layout.addWidget(self.sync_label)

    def _apply_style(self):
        color = SERVICE_COLORS.get(self.service_id, "#6b7280")
        border_color = color if self._selected or self._hovered else "#334155"
        border_width = 2 if self._selected else 1
        left_width = 5 if self._selected else 4
        bg = "#1f2937" if self._hovered else "#1e1e2e"

        self.setStyleSheet(
            f"""
            QFrame#ServiceCard {{
                background-color: {bg};
                border: {border_width}px solid {border_color};
                border-left: {left_width}px solid {color};
                border-radius: 10px;
                padding: 4px;
            }}
            """
        )
        self._shadow.setBlurRadius(24 if (self._hovered or self._selected) else 18)

    def set_selected(self, selected: bool):
        self._selected = bool(selected)
        self._apply_style()

    def set_busy(self, busy: bool):
        self._busy = bool(busy)
        if busy:
            self.status_dot.setStyleSheet("color: #f59e0b; font-size: 10px;")
        self._apply_style()

    def enterEvent(self, event):  # noqa: N802
        self._hovered = True
        self._apply_style()
        super().enterEvent(event)

    def leaveEvent(self, event):  # noqa: N802
        self._hovered = False
        self._apply_style()
        super().leaveEvent(event)

    def mousePressEvent(self, event):  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit(self.service_id)
        super().mousePressEvent(event)

    def update_data(self, summary: dict, auth_ok: bool, last_sync: str = ""):
        if not auth_ok:
            self.status_dot.setStyleSheet("color: #ef4444; font-size: 10px;")
            self.primary_value.setText("Not Auth")
            self.primary_label.setText("Please configure API Key")
            self.secondary_left.setText("")
            self.secondary_right.setText("")
            return

        if not self._busy:
            self.status_dot.setStyleSheet("color: #22c55e; font-size: 10px;")

        tokens_in = int(summary.get("tokens_in") or 0)
        tokens_out = int(summary.get("tokens_out") or 0)
        total_tokens = tokens_in + tokens_out
        requests = int(summary.get("requests") or 0)
        cost = float(summary.get("cost_usd") or 0.0)

        if self.service_id == "claude_code":
            self.primary_value.setText(f"{requests:,}")
            self.primary_label.setText("This Month Sessions")
            self.secondary_left.setText(f"Tokens: {total_tokens:,}")
            self.secondary_right.setText("")
        else:
            if total_tokens >= 1_000_000:
                display = f"{total_tokens / 1_000_000:.1f}M"
            elif total_tokens >= 1_000:
                display = f"{total_tokens / 1_000:.1f}K"
            else:
                display = str(total_tokens)
            self.primary_value.setText(display)
            self.primary_label.setText("This Month Tokens")
            self.secondary_left.setText(f"Cost: ${cost:.2f}")
            self.secondary_right.setText(f"Req: {requests:,}")

        if last_sync:
            self.sync_label.setText(f"Synced: {last_sync[:16]}")
        else:
            self.sync_label.setText("Never synced")
