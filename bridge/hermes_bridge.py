#!/usr/bin/env python3
"""Puente entre el backend de Cloud Run y Hermes para la pestaña IA.

Esta máquina no tiene puerto entrante abierto, así que no se la puede llamar:
es ella la que pregunta si hay trabajo pendiente. Por cada pregunta recogida
lanza un Hermes con el perfil `fitbit-air-bridge` (sin ninguna herramienta
activa) y devuelve la respuesta por el mismo canal.

Uso:
    HERMES_BRIDGE_KEY_FILE=/ruta/clave backend/hermes_bridge.py
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

# The reply-parsing contract lives with the backend so both paths agree on it.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.app.assistant_reply import ENVELOPE_INSTRUCTIONS, parse_reply  # noqa: E402

BACKEND_URL = os.environ.get(
    "FITBIT_BACKEND_URL",
    "https://fitbit-air-backend-i3pz5b53aq-na.a.run.app",
).rstrip("/")
KEY_FILE = os.environ.get("HERMES_BRIDGE_KEY_FILE", "/root/.hermes/fitbit-air-bridge.key")
POLL_SECONDS = float(os.environ.get("BRIDGE_POLL_SECONDS", "2"))
ANSWER_TIMEOUT = float(os.environ.get("BRIDGE_ANSWER_TIMEOUT", "240"))
HERMES_HOME = os.environ.get("BRIDGE_HERMES_HOME", "/root/.hermes/profiles/fitbit-air-bridge")
HERMES_BIN = os.environ.get("BRIDGE_HERMES_BIN", "hermes")

PROMPT_TEMPLATE = """Eres el asistente de salud de mi app personal, que muestra mis datos de Google Health.

Reglas:
- Responde en español, en 2 a 4 frases, citando solo cifras que aparezcan en el resumen.
- No des consejo médico, diagnósticos ni tratamientos.
- Si la pregunta pide un dato que no está en el resumen, dilo claramente.

Pregunta: {question}

Resumen de mis métricas ({range_name}, zona horaria {timezone}) en JSON:
{dashboard}
"""

PROMPT = PROMPT_TEMPLATE + "\n" + ENVELOPE_INSTRUCTIONS


def read_key() -> str:
    with open(KEY_FILE, encoding="utf-8") as handle:
        return handle.read().strip()


def call(path: str, payload: dict | None, key: str, method: str = "POST") -> dict:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{BACKEND_URL}{path}",
        data=body,
        method=method,
        headers={
            "X-Bridge-Key": key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8") or "{}")


def ask_hermes(job: dict):
    """Run one agent turn and return the parsed reply (prose + optional extras)."""
    prompt = PROMPT.format(
        question=job["question"],
        range_name=job.get("range", "week"),
        timezone=job.get("timezone", "UTC"),
        dashboard=json.dumps(job.get("dashboard", {}), ensure_ascii=False),
    )
    environment = dict(os.environ)
    environment["HERMES_HOME"] = HERMES_HOME
    completed = subprocess.run(
        [HERMES_BIN, "chat", "-q", prompt, "-Q"],
        capture_output=True,
        text=True,
        timeout=ANSWER_TIMEOUT,
        env=environment,
        cwd="/root",
    )
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "").strip().splitlines()
        raise RuntimeError(detail[-1] if detail else "hermes exited with an error")
    answer = (completed.stdout or "").strip()
    # `hermes chat -Q` prints the answer followed by a session_id footer.
    lines = [line for line in answer.splitlines() if not line.startswith("session_id:")]
    cleaned = "\n".join(lines).strip()
    if not cleaned:
        raise RuntimeError("hermes returned an empty answer")
    return parse_reply(cleaned)


def main() -> int:
    key = read_key()
    print(f"puente activo → {BACKEND_URL} (sondeo cada {POLL_SECONDS:g}s)", flush=True)
    while True:
        try:
            claimed = call("/bridge/claim", None, key)
        except urllib.error.HTTPError as error:
            print(f"claim rechazado: HTTP {error.code}", flush=True)
            time.sleep(10)
            continue
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            print(f"sin conexión con el backend: {error}", flush=True)
            time.sleep(5)
            continue

        job = claimed.get("job")
        if not job:
            time.sleep(POLL_SECONDS)
            continue

        started = time.time()
        try:
            reply = ask_hermes(job)
            extras = []
            if reply.chart:
                extras.append(f"chart {reply.chart['kind']}×{len(reply.chart['points'])}")
            if reply.suggestions:
                extras.append(f"{len(reply.suggestions)} sugerencias")
            print(
                f"respondida {job['jobId'][:8]} en {time.time() - started:.1f}s"
                + (f" ({', '.join(extras)})" if extras else ""),
                flush=True,
            )
        except Exception as error:  # noqa: BLE001 - el puente nunca debe morir
            reply = parse_reply(
                "No he podido generar la respuesta en este momento. "
                "Inténtalo de nuevo en unos minutos."
            )
            print(f"error al responder {job['jobId'][:8]}: {error}", flush=True)

        try:
            call(
                "/bridge/answer",
                {
                    "jobId": job["jobId"],
                    "answer": reply.answer,
                    "chart": reply.chart,
                    "suggestions": reply.suggestions,
                },
                key,
            )
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as error:
            print(f"no se pudo entregar la respuesta: {error}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
