"""
chatgpt_api.py - Collect usage from OpenAI organization usage endpoints.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

import requests

import config
from .base_collector import BaseCollector, UsageRecord

BASE_URL = "https://api.openai.com/v1"
USAGE_TYPES = [
    "completions",
    "embeddings",
    "images",
    "audio_speeches",
    "audio_transcriptions",
]


class ChatGPTApiCollector(BaseCollector):
    service_id = "chatgpt_api"
    display_name = "ChatGPT API"

    def is_available(self) -> bool:
        key = config.get_api_key(self.service_id)
        return bool(key and key.startswith("sk-"))

    def collect(self) -> list[UsageRecord]:
        key = config.get_api_key(self.service_id)
        if not key:
            raise ValueError("未配置 ChatGPT/OpenAI API Key，请在设置中添加。")

        headers = {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }

        end_date = date.today()
        start_date = end_date - timedelta(days=30)
        aggregated: dict[tuple[str, str], dict] = {}

        for usage_type in USAGE_TYPES:
            try:
                self._fetch_usage_type(headers, usage_type, start_date, end_date, aggregated)
            except requests.HTTPError as e:
                status = e.response.status_code if e.response is not None else None
                if status in (403, 404):
                    # Some org usage categories may be unavailable.
                    continue
                raise

        records: list[UsageRecord] = []
        for (date_str, model), stats in sorted(aggregated.items()):
            records.append(
                UsageRecord(
                    service=self.service_id,
                    model=model,
                    date=date_str,
                    tokens_in=stats["tokens_in"],
                    tokens_out=stats["tokens_out"],
                    requests=stats["requests"],
                    cost_usd=stats["cost_usd"],
                    extra={"usage_types": sorted(stats["usage_types"])},
                )
            )

        return records

    def _fetch_usage_type(
        self,
        headers: dict,
        usage_type: str,
        start_date: date,
        end_date: date,
        aggregated: dict[tuple[str, str], dict],
    ):
        params = {
            "start_time": self._to_unix(start_date),
            "end_time": self._to_unix(end_date),
            "bucket_width": "1d",
            "limit": 31,
        }

        resp = requests.get(
            f"{BASE_URL}/organization/usage/{usage_type}",
            headers=headers,
            params=params,
            timeout=15,
        )

        if resp.status_code == 401:
            raise ValueError("ChatGPT/OpenAI API Key 无效，请重新配置。")
        resp.raise_for_status()

        data = resp.json()
        for bucket in data.get("data", []):
            date_str = self._ts_to_date(int(bucket.get("start_time", 0) or 0))
            for result in bucket.get("results", []):
                model = str(result.get("model") or "unknown")
                tokens_in = int(result.get("input_tokens") or 0)
                tokens_out = int(result.get("output_tokens") or 0)
                req_count = int(result.get("num_model_requests") or 0)
                cost = self._estimate_cost(model, tokens_in, tokens_out, usage_type)

                key = (date_str, model)
                if key not in aggregated:
                    aggregated[key] = {
                        "tokens_in": 0,
                        "tokens_out": 0,
                        "requests": 0,
                        "cost_usd": 0.0,
                        "usage_types": set(),
                    }

                aggregated[key]["tokens_in"] += tokens_in
                aggregated[key]["tokens_out"] += tokens_out
                aggregated[key]["requests"] += req_count
                aggregated[key]["cost_usd"] += cost
                aggregated[key]["usage_types"].add(usage_type)

    @staticmethod
    def _to_unix(d: date) -> int:
        return int(datetime(d.year, d.month, d.day, tzinfo=timezone.utc).timestamp())

    @staticmethod
    def _ts_to_date(ts: int) -> str:
        return datetime.fromtimestamp(ts, tz=timezone.utc).date().isoformat()

    @staticmethod
    def _estimate_cost(model: str, tokens_in: int, tokens_out: int, usage_type: str) -> float:
        pricing = {
            "gpt-4o": (2.5, 10.0),
            "gpt-4o-mini": (0.15, 0.6),
            "o1": (15.0, 60.0),
            "o3": (10.0, 40.0),
            "o3-mini": (1.1, 4.4),
            "gpt-4-turbo": (10.0, 30.0),
            "gpt-4": (30.0, 60.0),
            "gpt-3.5-turbo": (0.5, 1.5),
        }

        if usage_type != "completions":
            return 0.0

        for prefix, (in_price, out_price) in pricing.items():
            if model.startswith(prefix):
                return (tokens_in * in_price + tokens_out * out_price) / 1_000_000

        return (tokens_in * 2.5 + tokens_out * 10.0) / 1_000_000
