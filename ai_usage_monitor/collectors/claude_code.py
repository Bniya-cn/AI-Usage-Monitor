"""
claude_code.py - 解析本地 Claude Code 会话文件
数据来源：~/.claude/projects/*/sessions-index.json 与 *.jsonl
"""

import json
import os
from datetime import datetime, date
from pathlib import Path
from typing import Optional

from .base_collector import BaseCollector, UsageRecord

CLAUDE_DIR = Path.home() / ".claude" / "projects"


class ClaudeCodeCollector(BaseCollector):
    service_id = "claude_code"
    display_name = "Claude Code"

    def is_available(self) -> bool:
        return CLAUDE_DIR.exists()

    def collect(self) -> list[UsageRecord]:
        if not CLAUDE_DIR.exists():
            return []

        daily: dict[str, dict] = {}

        for project_dir in CLAUDE_DIR.iterdir():
            if not project_dir.is_dir():
                continue

            # 优先解析 sessions-index.json；同时也解析 .jsonl 获取 token 数据
            index_file = project_dir / "sessions-index.json"
            if index_file.exists():
                self._parse_sessions_index(index_file, daily)

            # 无论 index 是否存在，都解析 .jsonl 以获取 token 用量
            for jsonl_file in project_dir.glob("*.jsonl"):
                self._parse_jsonl(jsonl_file, daily)

        records = []
        for date_str, stats in sorted(daily.items()):
            records.append(UsageRecord(
                service=self.service_id,
                model=None,
                date=date_str,
                tokens_in=stats.get("tokens_in", 0),
                tokens_out=stats.get("tokens_out", 0),
                requests=stats.get("requests", 0),
                cost_usd=0.0,
                extra={
                    "sessions": stats.get("sessions", 0),
                    "projects": stats.get("projects", 0),
                },
            ))
        return records

    def _parse_sessions_index(self, index_file: Path, daily: dict):
        try:
            with open(index_file, encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            return

        project_name = index_file.parent.name

        # 兼容多种格式：列表 / {"sessions": [...]} / {"data": [...]}
        if isinstance(data, list):
            sessions = data
        elif isinstance(data, dict):
            sessions = (data.get("sessions") or data.get("data") or
                        list(data.values()) if data else [])
            if isinstance(sessions, dict):
                sessions = list(sessions.values())
        else:
            return

        for session in sessions:
            if not isinstance(session, dict):
                continue
            # 兼容多种时间戳字段名
            ts = (session.get("lastUpdated") or session.get("updatedAt") or
                  session.get("createdAt") or session.get("created_at") or
                  session.get("timestamp") or "")
            date_str = self._extract_date(str(ts))
            if not date_str:
                continue

            bucket = daily.setdefault(date_str, {
                "tokens_in": 0, "tokens_out": 0,
                "requests": 0, "sessions": 0, "projects": set()
            })
            bucket["sessions"] += 1
            bucket["projects"].add(project_name)
            msg_count = (session.get("messageCount") or session.get("message_count") or
                         session.get("numMessages") or 0)
            bucket["requests"] += int(msg_count)

        for v in daily.values():
            if isinstance(v.get("projects"), set):
                v["projects"] = len(v["projects"])

    def _parse_jsonl(self, jsonl_file: Path, daily: dict):
        try:
            with open(jsonl_file, encoding="utf-8", errors="ignore") as f:
                lines = f.readlines()
        except Exception:
            return

        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue

            if not isinstance(obj, dict):
                continue

            # 兼容多种时间戳字段名
            ts = (obj.get("timestamp") or obj.get("created_at") or
                  obj.get("createdAt") or obj.get("time") or "")
            date_str = self._extract_date(str(ts))
            if not date_str:
                continue

            bucket = daily.setdefault(date_str, {
                "tokens_in": 0, "tokens_out": 0,
                "requests": 0, "sessions": 0, "projects": 0
            })

            # 提取 token 用量 — 兼容嵌套结构
            usage = (obj.get("usage") or obj.get("token_usage") or
                     obj.get("message", {}).get("usage") or {})
            if isinstance(usage, dict):
                bucket["tokens_in"]  += (usage.get("input_tokens", 0) or
                                         usage.get("prompt_tokens", 0) or
                                         usage.get("cache_read_input_tokens", 0))
                bucket["tokens_out"] += (usage.get("output_tokens", 0) or
                                         usage.get("completion_tokens", 0))

            # 统计消息条数（assistant 回复 或 包含 usage 的条目）
            role = obj.get("role") or obj.get("type") or ""
            if role in ("assistant", "tool_result") or usage:
                bucket["requests"] += 1

    @staticmethod
    def _extract_date(ts: str) -> Optional[str]:
        if not ts:
            return None
        try:
            # 尝试 ISO 格式 2024-01-15T10:30:00Z
            dt = datetime.fromisoformat(ts.replace("Z", "+00:00"))
            return dt.date().isoformat()
        except Exception:
            pass
        # 尝试直接截取 YYYY-MM-DD
        if len(ts) >= 10 and ts[4] == "-" and ts[7] == "-":
            return ts[:10]
        return None
