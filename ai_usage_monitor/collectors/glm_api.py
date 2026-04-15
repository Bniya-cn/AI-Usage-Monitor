"""
glm_api.py - Validate GLM API key and create a daily status snapshot.
"""

from __future__ import annotations

from datetime import date

import requests

import config
from .base_collector import BaseCollector, UsageRecord

GLM_BASE = "https://open.bigmodel.cn/api/paas/v4"


class GLMApiCollector(BaseCollector):
    service_id = "glm_api"
    display_name = "GLM API"

    def is_available(self) -> bool:
        key = config.get_api_key(self.service_id)
        return bool(key and key.strip())

    def collect(self) -> list[UsageRecord]:
        key = config.get_api_key(self.service_id)
        if not key:
            raise ValueError("未配置 GLM API Key，请在设置中添加。")

        headers = {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }

        # GLM is OpenAI-compatible for many endpoints; /models is used here as a lightweight key check.
        resp = requests.get(f"{GLM_BASE}/models", headers=headers, timeout=15)
        if resp.status_code in (401, 403):
            raise ValueError("GLM API Key 无效或无权限。")
        if resp.status_code >= 500:
            resp.raise_for_status()

        today = date.today().isoformat()
        return [
            UsageRecord(
                service=self.service_id,
                model="glm-api",
                date=today,
                tokens_in=0,
                tokens_out=0,
                requests=0,
                cost_usd=0.0,
                extra={
                    "status": "key_validated",
                    "note": "已校验 GLM API Key。当前版本不抓取网页用量。",
                },
            )
        ]
