"""
tray_icon.py - 系统托盘图标与右键菜单
"""

from PyQt6.QtWidgets import QSystemTrayIcon, QMenu, QApplication
from PyQt6.QtGui import QIcon, QPixmap, QColor, QPainter
from PyQt6.QtCore import QSize


def _make_colored_icon(color: str = "#7c3aed") -> QIcon:
    """生成一个简单的彩色圆形图标"""
    size = 32
    pixmap = QPixmap(size, size)
    pixmap.fill(QColor("transparent"))
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setBrush(QColor(color))
    painter.setPen(QColor("transparent"))
    painter.drawEllipse(4, 4, size - 8, size - 8)
    painter.end()
    return QIcon(pixmap)


class TrayIcon(QSystemTrayIcon):

    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self._main_window = main_window

        self.setIcon(_make_colored_icon())
        self.setToolTip("AI Usage Monitor — 运行中")

        menu = QMenu()

        show_action = menu.addAction("📊  打开主窗口")
        show_action.triggered.connect(self._show_main)

        sync_action = menu.addAction("⟳  立即同步")
        sync_action.triggered.connect(self._trigger_sync)

        menu.addSeparator()

        quit_action = menu.addAction("✕  退出")
        quit_action.triggered.connect(QApplication.quit)

        self.setContextMenu(menu)
        self.activated.connect(self._on_activated)
        self.show()

    def _show_main(self):
        self._main_window.showNormal()
        self._main_window.activateWindow()
        self._main_window.raise_()

    def _trigger_sync(self):
        self._main_window._start_sync()

    def _on_activated(self, reason):
        if reason == QSystemTrayIcon.ActivationReason.DoubleClick:
            self._show_main()

    def notify(self, title: str, message: str):
        self.showMessage(title, message, QSystemTrayIcon.MessageIcon.Information, 3000)
