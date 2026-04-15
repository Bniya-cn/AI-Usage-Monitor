"""
claude_api.py - Validate Claude API key and create a daily status snapshot.
"""

from __future__ import annotations

from datetime import date

import requests

import config
from .base_collector import BaseCollector, UsageRecord

CLAUDE_BASE = "https://api.anthropic.com/v1"


class ClaudeApiCollector(BaseCollector):
    service_id = "claude_api"
    display_name = "Claude API"

    def is_available(self) -> bool:
        key = config.get_api_key(self.service_id)
        return bool(key and key.strip())

    def collect(self) -> list[UsageRecord]:
        key = config.get_api_key(self.service_id)
        if not key:
            raise ValueError("未配置 Claude API Key，请在设置中添加。")

        headers = {
            "x-api-key": key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }

        resp = requests.get(f"{CLAUDE_BASE}/models", headers=headers, timeout=15)
        if resp.status_code in (401, 403):
            raise ValueError("Claude API Key 无效或无权限。")
        resp.raise_for_status()

        today = date.today().isoformat()
        return [
            UsageRecord(
                service=self.service_id,
                model="claude-api",
                date=today,
                tokens_in=0,
                tokens_out=0,
                requests=0,
                cost_usd=0.0,
                extra={
                    "status": "key_validated",
                    "note": "已校验 Claude API Key。当前版本不抓取网页用量。",
                },
            )
        ]
