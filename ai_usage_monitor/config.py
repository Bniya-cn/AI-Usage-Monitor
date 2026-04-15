"""
Configuration and credential storage helpers.
"""

from __future__ import annotations

import base64
import json
import os
from pathlib import Path
from typing import Optional

APP_NAME = "ai_usage_monitor"
CONFIG_PATH = Path.home() / ".ai_usage_monitor" / "config.json"
CREDS_PATH = Path.home() / ".ai_usage_monitor" / "credentials.json"

SERVICES = ["chatgpt_api", "claude_api", "kimi_api", "glm_api", "claude_code"]

_DEFAULT_CONFIG = {
    "sync_interval_minutes": 30,
    "claude_code_sync_interval_minutes": 5,
    "theme": "dark",
    "start_minimized": False,
    "show_tray_notification": True,
}


def _check_keyring() -> bool:
    """
    Kept for compatibility. Keyring is disabled by default for stability.
    """
    return os.environ.get("AI_USAGE_MONITOR_USE_KEYRING", "0") == "1"


_USE_KEYRING: bool = _check_keyring()


def _file_set(key: str, value: str):
    CREDS_PATH.parent.mkdir(parents=True, exist_ok=True)
    data: dict = {}
    if CREDS_PATH.exists():
        try:
            data = json.loads(CREDS_PATH.read_text(encoding="utf-8"))
        except Exception:
            pass
    data[key] = base64.b64encode(value.encode("utf-8")).decode("utf-8")
    CREDS_PATH.write_text(json.dumps(data), encoding="utf-8")


def _file_get(key: str) -> Optional[str]:
    if not CREDS_PATH.exists():
        return None
    try:
        data = json.loads(CREDS_PATH.read_text(encoding="utf-8"))
        raw = data.get(key)
        return base64.b64decode(raw).decode("utf-8") if raw else None
    except Exception:
        return None


def _file_delete(key: str):
    if not CREDS_PATH.exists():
        return
    try:
        data = json.loads(CREDS_PATH.read_text(encoding="utf-8"))
        data.pop(key, None)
        CREDS_PATH.write_text(json.dumps(data), encoding="utf-8")
    except Exception:
        pass


# Kept for compatibility with old code paths
_KEYRING_MAX_BYTES = 2000


def _set(key: str, value: str):
    _file_set(key, value)


def _get(key: str) -> Optional[str]:
    return _file_get(key)


def _delete(key: str):
    _file_delete(key)


def load_config() -> dict:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    if CONFIG_PATH.exists():
        try:
            with open(CONFIG_PATH, encoding="utf-8") as f:
                data = json.load(f)
            for k, v in _DEFAULT_CONFIG.items():
                data.setdefault(k, v)
            return data
        except Exception:
            pass
    return dict(_DEFAULT_CONFIG)


def save_config(cfg: dict):
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)


def set_api_key(service: str, key: str):
    _set(f"{service}_api_key", key)


def get_api_key(service: str) -> Optional[str]:
    # Backward compatibility: ChatGPT API used to be stored as openai_api.
    if service == "chatgpt_api":
        return _get("chatgpt_api_api_key") or _get("openai_api_api_key")
    return _get(f"{service}_api_key")


def delete_api_key(service: str):
    _delete(f"{service}_api_key")


def set_session_data(service: str, data: str):
    _file_set(f"{service}_session", data)


def get_session_data(service: str) -> Optional[str]:
    return _file_get(f"{service}_session")


def delete_session_data(service: str):
    _file_delete(f"{service}_session")


def has_credentials(service: str) -> bool:
    if service == "claude_code":
        return True
    key = get_api_key(service)
    return bool(key and key.strip())


def clear_credentials(service: str):
    delete_api_key(service)
    delete_session_data(service)
