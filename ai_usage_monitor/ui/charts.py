"""
Usage chart widget powered by pyqtgraph.
"""

from __future__ import annotations

from datetime import date, timedelta

import pyqtgraph as pg
from PyQt6.QtWidgets import QComboBox, QHBoxLayout, QLabel, QVBoxLayout, QWidget

import database

pg.setConfigOption("background", "#0f0f1a")
pg.setConfigOption("foreground", "#94a3b8")

SERVICE_COLORS = {
    "chatgpt_api": (16, 163, 127),
    "claude_api": (217, 119, 6),
    "kimi_api": (14, 165, 233),
    "glm_api": (34, 197, 94),
    "claude_code": (124, 58, 237),
}

SERVICE_LABELS = {
    "chatgpt_api": "ChatGPT API",
    "claude_api": "Claude API",
    "kimi_api": "KIMI API",
    "glm_api": "GLM API",
    "claude_code": "Claude Code",
}


class UsageChart(QWidget):
    """Usage chart with service/metric/range selectors."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(6)

        ctrl = QHBoxLayout()
        ctrl.addWidget(QLabel("Service:"))
        self.service_combo = QComboBox()
        for sid, label in SERVICE_LABELS.items():
            self.service_combo.addItem(label, sid)
        self.service_combo.currentIndexChanged.connect(self.refresh)
        ctrl.addWidget(self.service_combo)

        ctrl.addWidget(QLabel("Metric:"))
        self.metric_combo = QComboBox()
        self.metric_combo.addItems(["Tokens", "Requests", "Cost ($)"])
        self.metric_combo.currentIndexChanged.connect(self.refresh)
        ctrl.addWidget(self.metric_combo)

        ctrl.addWidget(QLabel("Range:"))
        self.days_combo = QComboBox()
        self.days_combo.addItem("7 days", 7)
        self.days_combo.addItem("30 days", 30)
        self.days_combo.addItem("90 days", 90)
        self.days_combo.setCurrentIndex(1)
        self.days_combo.currentIndexChanged.connect(self.refresh)
        ctrl.addWidget(self.days_combo)

        ctrl.addStretch()
        layout.addLayout(ctrl)

        self.plot_widget = pg.PlotWidget()
        self.plot_widget.showGrid(x=False, y=True, alpha=0.3)
        self.plot_widget.setMinimumHeight(220)
        self.plot_widget.setMouseEnabled(x=True, y=False)
        layout.addWidget(self.plot_widget)

    def set_service(self, service_id: str):
        idx = self.service_combo.findData(service_id)
        if idx >= 0:
            self.service_combo.setCurrentIndex(idx)

    def refresh(self):
        service_id = self.service_combo.currentData()
        metric_idx = self.metric_combo.currentIndex()
        days = int(self.days_combo.currentData() or 30)
        self._draw(service_id, metric_idx, days)

    def _draw(self, service_id: str, metric_idx: int, days: int):
        self.plot_widget.clear()

        rows = database.get_snapshots(service_id, days=days)
        if not rows:
            self.plot_widget.setTitle("No data", color="#64748b")
            return

        today = date.today()
        dates = [(today - timedelta(days=days - 1 - i)).isoformat() for i in range(days)]

        daily = {d: 0.0 for d in dates}
        for row in rows:
            d = row["date"]
            if d not in daily:
                continue
            if metric_idx == 0:
                daily[d] += (row["tokens_in"] or 0) + (row["tokens_out"] or 0)
            elif metric_idx == 1:
                daily[d] += row["requests"] or 0
            else:
                daily[d] += row["cost_usd"] or 0.0

        x = list(range(days))
        y = [daily[d] for d in dates]

        color = SERVICE_COLORS.get(service_id, (100, 100, 200))
        pen = pg.mkPen(color=color, width=2)
        brush = pg.mkBrush(color=(*color, 35))

        area_top = pg.PlotDataItem(x, y, pen=pen)
        area_bottom = pg.PlotDataItem(x, [0] * days, pen=pg.mkPen(None))
        self.plot_widget.addItem(pg.FillBetweenItem(area_top, area_bottom, brush=brush))
        self.plot_widget.plot(
            x,
            y,
            pen=pen,
            symbol="o",
            symbolSize=5,
            symbolBrush=pg.mkBrush(*color),
            symbolPen=pg.mkPen(None),
        )

        tick_step = 1 if days <= 10 else (5 if days <= 30 else 10)
        ticks = [(i, dates[i][5:]) for i in range(0, days, tick_step)]
        self.plot_widget.getAxis("bottom").setTicks([ticks])

        metric_names = ["Tokens", "Requests", "Cost ($)"]
        self.plot_widget.setTitle(
            f"{SERVICE_LABELS.get(service_id, '')} · Last {days} days {metric_names[metric_idx]}",
            color="#e2e8f0",
        )
