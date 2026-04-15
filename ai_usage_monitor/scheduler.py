"""
Background scheduler for periodic sync jobs.
"""

from __future__ import annotations

import os

import config
import database

# Avoid tzlocal registry edge-cases on some Windows environments.
os.environ.setdefault("TZ", "Etc/UTC")

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    from apscheduler.triggers.interval import IntervalTrigger

    _SCHED_IMPORT_ERROR: Exception | None = None
except Exception as e:  # pragma: no cover
    BackgroundScheduler = None  # type: ignore[assignment]
    IntervalTrigger = None  # type: ignore[assignment]
    _SCHED_IMPORT_ERROR = e

_scheduler: BackgroundScheduler | None = None  # type: ignore[valid-type]


def _run_sync(service_ids: list[str]):
    """Run collectors and write snapshots."""
    from collectors.chatgpt_api import ChatGPTApiCollector
    from collectors.claude_api import ClaudeApiCollector
    from collectors.claude_code import ClaudeCodeCollector
    from collectors.glm_api import GLMApiCollector
    from collectors.kimi_api import KimiApiCollector

    collector_map = {
        "chatgpt_api": ChatGPTApiCollector(),
        "claude_api": ClaudeApiCollector(),
        "kimi_api": KimiApiCollector(),
        "glm_api": GLMApiCollector(),
        "claude_code": ClaudeCodeCollector(),
    }

    for svc_id in service_ids:
        collector = collector_map.get(svc_id)
        if not collector or not collector.is_available():
            continue
        try:
            records = collector.collect()
            for r in records:
                database.upsert_snapshot(
                    service=r.service,
                    model=r.model,
                    date_str=r.date,
                    tokens_in=r.tokens_in,
                    tokens_out=r.tokens_out,
                    requests=r.requests,
                    cost_usd=r.cost_usd,
                    extra=r.extra,
                )
            database.set_auth_status(svc_id, True)
        except Exception as e:
            database.set_auth_status(svc_id, False, str(e))


def start(on_sync_done=None):
    """Start background scheduler."""
    global _scheduler
    if _SCHED_IMPORT_ERROR is not None:
        return

    if _scheduler and _scheduler.running:
        return

    cfg = config.load_config()
    api_interval = max(5, int(cfg.get("sync_interval_minutes", 30)))
    cc_interval = max(1, int(cfg.get("claude_code_sync_interval_minutes", 5)))

    _scheduler = BackgroundScheduler(timezone="UTC")

    api_services = ["chatgpt_api", "claude_api", "kimi_api", "glm_api"]
    _scheduler.add_job(
        _run_sync,
        trigger=IntervalTrigger(minutes=api_interval),
        args=[api_services],
        id="api_sync",
        replace_existing=True,
        misfire_grace_time=60,
    )

    _scheduler.add_job(
        _run_sync,
        trigger=IntervalTrigger(minutes=cc_interval),
        args=[["claude_code"]],
        id="cc_sync",
        replace_existing=True,
        misfire_grace_time=30,
    )

    _scheduler.start()


def stop():
    global _scheduler
    if _scheduler and _scheduler.running:
        _scheduler.shutdown(wait=False)
        _scheduler = None
