"""
kimi_api.py - Validate Kimi (Moonshot) API key and create a daily status snapshot.
"""

from __future__ import annotations

from datetime import date

import requests

import config
from .base_collector import BaseCollector, UsageRecord

KIMI_BASE = "https://api.moonshot.cn/v1"


class KimiApiCollector(BaseCollector):
    service_id = "kimi_api"
    display_name = "KIMI API"

    def is_available(self) -> bool:
        key = config.get_api_key(self.service_id)
        return bool(key and key.strip())

    def collect(self) -> list[UsageRecord]:
        key = config.get_api_key(self.service_id)
        if not key:
            raise ValueError("未配置 KIMI API Key，请在设置中添加。")

        headers = {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }

        resp = requests.get(f"{KIMI_BASE}/models", headers=headers, timeout=15)
        if resp.status_code in (401, 403):
            raise ValueError("KIMI API Key 无效或无权限。")
        if resp.status_code >= 500:
            resp.raise_for_status()

        today = date.today().isoformat()
        return [
            UsageRecord(
                service=self.service_id,
                model="kimi-api",
                date=today,
                tokens_in=0,
                tokens_out=0,
                requests=0,
                cost_usd=0.0,
                extra={
                    "status": "key_validated",
                    "note": "已校验 KIMI API Key。当前版本不抓取网页用量。",
                },
            )
        ]
