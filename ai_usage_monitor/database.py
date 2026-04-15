"""
database.py - SQLite 数据库操作层
负责创建表结构、读写用量快照、认证状态等
"""

import sqlite3
import json
import os
from datetime import datetime, date
from pathlib import Path
from typing import Optional


DB_PATH = Path.home() / ".ai_usage_monitor" / "usage.db"


def get_connection() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH), timeout=2.0)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=2000")
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    """初始化数据库，创建所有表"""
    with get_connection() as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS usage_snapshots (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                service     TEXT NOT NULL,
                model       TEXT,
                date        TEXT NOT NULL,
                tokens_in   INTEGER DEFAULT 0,
                tokens_out  INTEGER DEFAULT 0,
                requests    INTEGER DEFAULT 0,
                cost_usd    REAL DEFAULT 0,
                extra_json  TEXT,
                synced_at   TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS auth_status (
                service     TEXT PRIMARY KEY,
                is_auth     INTEGER DEFAULT 0,
                last_check  TEXT,
                error_msg   TEXT
            );

            CREATE INDEX IF NOT EXISTS idx_snapshots_service_date
                ON usage_snapshots(service, date);
        """)


def upsert_snapshot(
    service: str,
    model: Optional[str],
    date_str: str,
    tokens_in: int = 0,
    tokens_out: int = 0,
    requests: int = 0,
    cost_usd: float = 0.0,
    extra: Optional[dict] = None,
):
    """插入或更新一条用量快照（按 service+model+date 去重）"""
    synced_at = datetime.now().isoformat()
    extra_json = json.dumps(extra, ensure_ascii=False) if extra else None
    with get_connection() as conn:
        # 先尝试找已有记录
        existing = conn.execute(
            "SELECT id FROM usage_snapshots WHERE service=? AND model IS ? AND date=?",
            (service, model, date_str),
        ).fetchone()
        if existing:
            conn.execute(
                """UPDATE usage_snapshots
                   SET tokens_in=?, tokens_out=?, requests=?, cost_usd=?,
                       extra_json=?, synced_at=?
                   WHERE id=?""",
                (tokens_in, tokens_out, requests, cost_usd, extra_json, synced_at, existing["id"]),
            )
        else:
            conn.execute(
                """INSERT INTO usage_snapshots
                   (service, model, date, tokens_in, tokens_out, requests, cost_usd, extra_json, synced_at)
                   VALUES (?,?,?,?,?,?,?,?,?)""",
                (service, model, date_str, tokens_in, tokens_out, requests, cost_usd, extra_json, synced_at),
            )


def get_snapshots(service: str, days: int = 30) -> list[dict]:
    """获取某服务最近 N 天的用量快照列表"""
    from datetime import timedelta
    start = (date.today() - timedelta(days=days)).isoformat()
    with get_connection() as conn:
        rows = conn.execute(
            """SELECT * FROM usage_snapshots
               WHERE service=? AND date>=?
               ORDER BY date ASC, model ASC""",
            (service, start),
        ).fetchall()
    return [dict(r) for r in rows]


def get_summary(service: str) -> dict:
    """获取某服务本月汇总数据"""
    month_start = date.today().replace(day=1).isoformat()
    with get_connection() as conn:
        row = conn.execute(
            """SELECT
                 SUM(tokens_in)  AS tokens_in,
                 SUM(tokens_out) AS tokens_out,
                 SUM(requests)   AS requests,
                 SUM(cost_usd)   AS cost_usd,
                 MAX(synced_at)  AS last_sync
               FROM usage_snapshots
               WHERE service=? AND date>=?""",
            (service, month_start),
        ).fetchone()
    return dict(row) if row else {}


def set_auth_status(service: str, is_auth: bool, error_msg: str = ""):
    last_check = datetime.now().isoformat()
    with get_connection() as conn:
        conn.execute(
            """INSERT INTO auth_status (service, is_auth, last_check, error_msg)
               VALUES (?,?,?,?)
               ON CONFLICT(service) DO UPDATE SET
                 is_auth=excluded.is_auth,
                 last_check=excluded.last_check,
                 error_msg=excluded.error_msg""",
            (service, int(is_auth), last_check, error_msg),
        )


def get_auth_status(service: str) -> dict:
    with get_connection() as conn:
        row = conn.execute(
            "SELECT * FROM auth_status WHERE service=?", (service,)
        ).fetchone()
    return dict(row) if row else {"service": service, "is_auth": 0, "error_msg": ""}
