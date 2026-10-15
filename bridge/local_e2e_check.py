"""End-to-end local check of the inverse bridge, no production secrets involved.

Builds the app with a fake health client and a known signing secret, serves it
on localhost, runs the real bridge script against it, and asks a question the
way the phone would.
"""

from __future__ import annotations

import asyncio
import os
import subprocess
import sys
import threading
import time

import httpx
import uvicorn

sys.path.insert(0, "/root/fitbit-air-app")

from backend.app.main import AppSettings, create_app  # noqa: E402
from backend.app.tokens import DEFAULT_AUDIENCE, DEFAULT_ISSUER, sign_token  # noqa: E402

PORT = 8099
KEY = "clave-de-prueba-local-no-reutilizable"
SIGNING = "secreto-de-firma-local"
KEY_FILE = "/tmp/bridge-test.key"


class FakeHealthClient:
    def load_dashboard(self, **kwargs):
        return {
            "status": "ready",
            "stepsToday": 8421,
            "restingHeartRate": 57,
            "previousRestingHeartRate": 59,
            "sleep": {"asleepMinutes": 452, "inBedMinutes": 486},
            "stepsTrend": [{"date": "2026-10-14", "steps": 8421}],
        }


def main() -> int:
    with open(KEY_FILE, "w", encoding="utf-8") as handle:
        handle.write(KEY)
    os.chmod(KEY_FILE, 0o600)

    settings = AppSettings(
        google_client_id="id", google_client_secret="secret",
        google_redirect_uri="https://example.invalid/cb",
        allowed_email="javier@example.com", app_signing_secret=SIGNING,
        google_project_id="p", refresh_token_secret_name="n",
        hermes_bridge_secret=KEY, hermes_bridge_wait_seconds=200.0,
    )
    app = create_app(
        settings=settings,
        refresh_token_store=type("S", (), {"read_refresh_token": lambda self: "rt"})(),
        auth_service=None,
        health_client=FakeHealthClient(),
        hermes_client=None,
    )

    server = uvicorn.Server(
        uvicorn.Config(app, host="127.0.0.1", port=PORT, log_level="warning")
    )
    threading.Thread(target=server.run, daemon=True).start()
    for _ in range(80):
        try:
            httpx.get(f"http://127.0.0.1:{PORT}/api/health", timeout=1)
            break
        except Exception:
            time.sleep(0.25)

    environment = dict(os.environ)
    environment.update(
        {
            "FITBIT_BACKEND_URL": f"http://127.0.0.1:{PORT}",
            "HERMES_BRIDGE_KEY_FILE": KEY_FILE,
            "BRIDGE_POLL_SECONDS": "0.5",
        }
    )
    bridge = subprocess.Popen(
        [sys.executable, "/root/fitbit-air-app/bridge/hermes_bridge.py"],
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )

    now = int(time.time())
    session = sign_token(
        {
            "iss": DEFAULT_ISSUER, "aud": DEFAULT_AUDIENCE, "sub": "u1",
            "email": "javier@example.com", "iat": now, "exp": now + 600,
            "scope": "dashboard",
        },
        secret=SIGNING,
    )

    print("→ preguntando como lo haría la app…", flush=True)
    started = time.time()
    response = httpx.post(
        f"http://127.0.0.1:{PORT}/api/assistant",
        headers={"Authorization": f"Bearer {session}"},
        json={
            "question": "¿Cuántos pasos he dado hoy y cómo dormí?",
            "range": "week",
            "timezone": "Europe/Madrid",
        },
        timeout=260,
    )
    elapsed = time.time() - started
    print(f"HTTP {response.status_code} tras {elapsed:.1f}s", flush=True)
    print("RESPUESTA:", response.json().get("answer", response.text)[:600], flush=True)

    bridge.terminate()
    try:
        out = bridge.communicate(timeout=10)[0]
    except subprocess.TimeoutExpired:
        bridge.kill()
        out = ""
    print("--- log del puente ---", flush=True)
    print((out or "").strip()[:800], flush=True)
    server.should_exit = True
    return 0 if response.status_code == 200 else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(asyncio.to_thread(main)))
