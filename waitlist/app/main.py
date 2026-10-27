"""Waitlist service for the 14-day demand test.

One container serves the landing page and the three endpoints the experiment
needs: signup, event counter, and a token-protected read of the numbers. There
is deliberately no tracking SDK, no cookie, and no third-party call: the pitch
is "nothing leaves your device", and the site that makes the pitch should not be
the exception.
"""

from __future__ import annotations

import os
import re
import threading
import time
from collections import deque
from datetime import datetime, timezone
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, Response
from pydantic import BaseModel, field_validator

from waitlist.app.store import SecretManagerStore, SqliteStore, summarise

EMAIL_PATTERN = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,190}\.[A-Za-z]{2,}$")
ALLOWED_INTENTS = {"reserve_pro", "free_only", "no_pay"}
ALLOWED_DEVICES = {"fitbit", "samsung", "garmin", "apple", "other", ""}
ALLOWED_EVENTS = {"page_view", "clicked_reserve"}
MAX_SOURCE = 40
# One IP gets 20 writes a minute: enough for a human filling the form twice,
# enough to stop a loop from filling the secret with junk.
RATE_LIMIT_WRITES = 20
RATE_WINDOW_SECONDS = 60

INDEX_PATH = Path(__file__).resolve().parent.parent / "web" / "index.html"


def _build_store():
    backend = os.environ.get("WAITLIST_STORE", "sqlite").strip().lower()
    if backend == "secretmanager":
        return SecretManagerStore(
            project_id=os.environ["GOOGLE_CLOUD_PROJECT"],
            secret_name=os.environ.get("WAITLIST_SECRET_NAME", "fitbit-air-waitlist"),
        )
    return SqliteStore(os.environ.get("WAITLIST_SQLITE_PATH", ":memory:"))


app = FastAPI(title="waitlist", docs_url=None, redoc_url=None)
store = _build_store()

_rate_lock = threading.Lock()
_rate_state: dict[str, deque] = {}


def _rate_limited(client: str) -> bool:
    now = time.monotonic()
    with _rate_lock:
        hits = _rate_state.setdefault(client, deque())
        while hits and now - hits[0] > RATE_WINDOW_SECONDS:
            hits.popleft()
        if len(hits) >= RATE_LIMIT_WRITES:
            return True
        hits.append(now)
        return False


def _client(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "")
    direct = request.client.host if request.client else "unknown"
    return (forwarded.split(",")[0] or direct).strip()


class Signup(BaseModel):
    email: str
    source: str = ""
    previous_device: str = ""
    intent: str = ""

    @field_validator("email")
    @classmethod
    def clean_email(cls, value: str) -> str:
        email = value.strip().lower()
        if not EMAIL_PATTERN.fullmatch(email):
            raise ValueError("invalid email")
        return email

    @field_validator("source")
    @classmethod
    def clean_source(cls, value: str) -> str:
        # Cut at the first invalid character instead of stripping it: stripping
        # glued "fitbitair?evil=x" into "fitbitairevilx" and quietly merged two
        # channels into one bucket.
        return re.split(r"[^a-z0-9_-]", value.strip().lower(), maxsplit=1)[0][:MAX_SOURCE]

    @field_validator("previous_device")
    @classmethod
    def clean_device(cls, value: str) -> str:
        device = value.strip().lower()
        if device not in ALLOWED_DEVICES:
            raise ValueError("unknown device")
        return device

    @field_validator("intent")
    @classmethod
    def clean_intent(cls, value: str) -> str:
        intent = value.strip().lower()
        if intent and intent not in ALLOWED_INTENTS:
            raise ValueError("unknown intent")
        return intent


class Event(BaseModel):
    type: str
    source: str = ""

    @field_validator("type")
    @classmethod
    def clean_type(cls, value: str) -> str:
        kind = value.strip().lower()
        if kind not in ALLOWED_EVENTS:
            raise ValueError("unknown event")
        return kind

    @field_validator("source")
    @classmethod
    def clean_source(cls, value: str) -> str:
        # Cut at the first invalid character instead of stripping it: stripping
        # glued "fitbitair?evil=x" into "fitbitairevilx" and quietly merged two
        # channels into one bucket.
        return re.split(r"[^a-z0-9_-]", value.strip().lower(), maxsplit=1)[0][:MAX_SOURCE]


@app.get("/", response_class=HTMLResponse)
def landing() -> HTMLResponse:
    return HTMLResponse(INDEX_PATH.read_text(encoding="utf-8"))


@app.get("/favicon.ico")
def favicon() -> Response:
    return Response(status_code=204)


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.post("/api/waitlist")
def join_waitlist(signup: Signup, request: Request) -> JSONResponse:
    if _rate_limited(_client(request)):
        raise HTTPException(status_code=429, detail="too many requests")
    # Duplicates are not reported: the response must not tell a stranger whether
    # an address is already on the list.
    store.add_signup(
        {
            "email": signup.email,
            "source": signup.source,
            "previous_device": signup.previous_device,
            "intent": signup.intent,
            "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }
    )
    return JSONResponse({"ok": True}, status_code=200)


@app.post("/api/events")
def record_event(event: Event, request: Request) -> JSONResponse:
    if _rate_limited(_client(request)):
        raise HTTPException(status_code=429, detail="too many requests")
    store.add_event(event.type, event.source)
    return JSONResponse({"ok": True}, status_code=200)


@app.get("/api/stats")
def stats(token: str = "") -> JSONResponse:
    expected = os.environ.get("WAITLIST_STATS_TOKEN", "")
    # No token configured means the endpoint is off, not open.
    if not expected or token != expected:
        raise HTTPException(status_code=404, detail="not found")
    return JSONResponse(summarise(store.snapshot()))


@app.get("/api/signups")
def signups_csv(token: str = "") -> Response:
    """Plain CSV for reading on a phone; same token as the stats endpoint."""
    expected = os.environ.get("WAITLIST_STATS_TOKEN", "")
    if not expected or token != expected:
        raise HTTPException(status_code=404, detail="not found")
    rows = ["email,source,previous_device,intent,created_at"]
    for record in store.snapshot().get("signups", []):
        rows.append(
            ",".join(
                str(record.get(key, "")).replace(",", " ")
                for key in ("email", "source", "previous_device", "intent", "created_at")
            )
        )
    return Response("\n".join(rows) + "\n", media_type="text/csv")
