"""Storage for the waitlist experiment.

Two backends behind one interface:

- ``SqliteStore`` for local runs and the test suite.
- ``SecretManagerStore`` in production, because this project already has Secret
  Manager wired for the health backend and creating Firestore for a 14-day test
  is more infrastructure than the test is worth.

Limit worth knowing: Secret Manager caps one version at 64 KiB, which is roughly
500 signups at this record size. If the list outgrows that, move to Firestore.
"""

from __future__ import annotations

import base64
import json
import sqlite3
import threading
from datetime import datetime, timezone
from typing import Protocol

SECRET_PAYLOAD_VERSION = 1


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class WaitlistStore(Protocol):
    def add_signup(self, record: dict) -> bool: ...

    def add_event(self, kind: str, source: str) -> None: ...

    def snapshot(self) -> dict: ...


class SqliteStore:
    def __init__(self, path: str = ":memory:"):
        self._lock = threading.Lock()
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS signups (
                email TEXT PRIMARY KEY,
                source TEXT,
                previous_device TEXT,
                intent TEXT,
                created_at TEXT
            )
            """
        )
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT,
                source TEXT,
                created_at TEXT
            )
            """
        )
        self._connection.commit()

    def add_signup(self, record: dict) -> bool:
        with self._lock:
            cursor = self._connection.execute(
                "INSERT OR IGNORE INTO signups"
                " (email, source, previous_device, intent, created_at)"
                " VALUES (?, ?, ?, ?, ?)",
                (
                    record["email"],
                    record.get("source", ""),
                    record.get("previous_device", ""),
                    record.get("intent", ""),
                    record.get("created_at") or _now(),
                ),
            )
            self._connection.commit()
            return cursor.rowcount == 1

    def add_event(self, kind: str, source: str) -> None:
        with self._lock:
            self._connection.execute(
                "INSERT INTO events (kind, source, created_at) VALUES (?, ?, ?)",
                (kind, source, _now()),
            )
            self._connection.commit()

    def snapshot(self) -> dict:
        with self._lock:
            rows = self._connection.execute(
                "SELECT email, source, previous_device, intent, created_at FROM signups"
            ).fetchall()
            events = self._connection.execute(
                "SELECT kind, source FROM events"
            ).fetchall()
        return {
            "signups": [
                {
                    "email": row[0],
                    "source": row[1],
                    "previous_device": row[2],
                    "intent": row[3],
                    "created_at": row[4],
                }
                for row in rows
            ],
            "events": _count_events(events),
        }


class SecretManagerStore:
    """Append-only JSON document in a single Secret Manager secret.

    Every write reads the latest version, adds one record and pushes a new
    version. Two simultaneous signups can lose one, which at this volume is an
    acceptable trade against standing up a database for a two-week test.
    """

    def __init__(self, *, project_id: str, secret_name: str, session=None):
        self.project_id = project_id
        self.secret_name = secret_name
        self._base_url = (
            f"https://secretmanager.googleapis.com/v1/projects/{project_id}"
            f"/secrets/{secret_name}"
        )
        if session is None:
            import google.auth
            from google.auth.transport.requests import AuthorizedSession

            credentials, _ = google.auth.default(
                scopes=["https://www.googleapis.com/auth/cloud-platform"]
            )
            session = AuthorizedSession(credentials)
        self.session = session

    def _read(self) -> dict:
        response = self.session.get(f"{self._base_url}/versions/latest:access", timeout=15)
        if response.status_code == 404:
            return {"version": SECRET_PAYLOAD_VERSION, "signups": {}, "events": {}}
        response.raise_for_status()
        encoded = response.json()["payload"]["data"]
        decoded = json.loads(base64.b64decode(encoded).decode("utf-8"))
        decoded.setdefault("signups", {})
        decoded.setdefault("events", {})
        return decoded

    def _write(self, document: dict) -> None:
        payload = json.dumps(document, separators=(",", ":"), ensure_ascii=False)
        encoded = base64.b64encode(payload.encode("utf-8")).decode("ascii")
        response = self.session.post(
            f"{self._base_url}:addVersion",
            json={"payload": {"data": encoded}},
            timeout=15,
        )
        response.raise_for_status()

    def add_signup(self, record: dict) -> bool:
        document = self._read()
        email = record["email"]
        if email in document["signups"]:
            return False
        document["signups"][email] = {
            "source": record.get("source", ""),
            "previous_device": record.get("previous_device", ""),
            "intent": record.get("intent", ""),
            "created_at": record.get("created_at") or _now(),
        }
        self._write(document)
        return True

    def add_event(self, kind: str, source: str) -> None:
        document = self._read()
        key = f"{kind}|{source or 'direct'}"
        document["events"][key] = document["events"].get(key, 0) + 1
        self._write(document)

    def snapshot(self) -> dict:
        document = self._read()
        return {
            "signups": [{"email": email, **record} for email, record in document["signups"].items()],
            "events": document["events"],
        }


def _count_events(rows) -> dict:
    counts: dict[str, int] = {}
    for kind, source in rows:
        counts[f"{kind}|{source or 'direct'}"] = counts.get(f"{kind}|{source or 'direct'}", 0) + 1
    return counts


def summarise(snapshot: dict) -> dict:
    """The numbers the experiment's decision table actually needs."""
    signups = snapshot.get("signups", [])
    events = snapshot.get("events", {})

    def bucket(key: str) -> dict[str, int]:
        counts: dict[str, int] = {}
        for record in signups:
            value = record.get(key) or "unknown"
            counts[value] = counts.get(value, 0) + 1
        return dict(sorted(counts.items(), key=lambda item: -item[1]))

    total = len(signups)
    reserve_clicks = sum(
        count for key, count in events.items() if key.startswith("clicked_reserve")
    )
    reserve_intent = sum(1 for record in signups if record.get("intent") == "reserve_pro")
    return {
        "signups_total": total,
        "by_source": bucket("source"),
        "by_device": bucket("previous_device"),
        "by_intent": bucket("intent"),
        "reserve_clicks": reserve_clicks,
        "reserve_intent": reserve_intent,
        "reserve_rate_of_signups": round(reserve_intent / total * 100, 1) if total else 0.0,
        "click_rate_of_signups": round(reserve_clicks / total * 100, 1) if total else 0.0,
        "events": events,
    }
