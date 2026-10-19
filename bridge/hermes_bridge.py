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

from backend.app.assistant_reply import (
    ENVELOPE_INSTRUCTIONS,
    parse_streamed,
    streamable_prefix,
)  # noqa: E402

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


def build_prompt(job: dict) -> str:
    """Fill the template, then append the envelope rules.

    The envelope's own braces must never go through `str.format`: doing that
    raised KeyError('"answer"') on every question.
    """
    header = PROMPT_TEMPLATE.format(
        question=job["question"],
        range_name=job.get("range", "week"),
        timezone=job.get("timezone", "UTC"),
        dashboard=json.dumps(job.get("dashboard", {}), ensure_ascii=False),
    )
    return header + "\n" + ENVELOPE_INSTRUCTIONS + "\n"


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


def ask_hermes(job: dict, on_delta=None):
    """Run one agent turn, forwarding the prose as it is written.

    The CLI's stream-json mode emits a `text` event per token batch, so the
    caller can relay readable text before the turn ends. Anything from the
    delimiter onwards is metadata and is never sent as a delta.
    """
    prompt = build_prompt(job)
    environment = dict(os.environ)
    environment["HERMES_HOME"] = HERMES_HOME
    deadline = time.time() + ANSWER_TIMEOUT
    process = subprocess.Popen(
        [HERMES_BIN, "chat", "-q", prompt, "-Q", "--format", "stream-json"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env=environment,
        cwd="/root",
        bufsize=1,
    )

    raw = ""
    shown = 0
    exit_code = 0
    try:
        for line in process.stdout:  # type: ignore[union-attr]
            if time.time() > deadline:
                process.kill()
                raise RuntimeError("hermes did not finish in time")
            line = line.strip()
            if not line or line.startswith("session_id:"):
                continue
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") != "text" or not isinstance(event.get("text"), str):
                continue
            raw += event["text"]
            if on_delta is None:
                continue
            visible = streamable_prefix(raw)
            if len(visible) > shown:
                on_delta(visible[shown:])
                shown = len(visible)
        exit_code = process.wait(timeout=30)
    finally:
        if process.poll() is None:
            process.kill()

    if exit_code != 0:
        raise RuntimeError(f"hermes exited with code {exit_code}")
    if not raw.strip():
        raise RuntimeError("hermes returned an empty answer")
    reply = parse_streamed(raw)
    if not reply.answer.strip():
        raise RuntimeError("hermes returned an empty answer")
    return reply


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
        pending: list[str] = []

        def flush_delta(chunk: str = "", force: bool = False) -> None:
            """Relay readable text as it is written, in small batches."""
            if chunk:
                pending.append(chunk)
            if not pending:
                return
            if not force and sum(len(part) for part in pending) < 60:
                return
            payload = "".join(pending)
            pending.clear()
            try:
                call("/bridge/chunk", {"jobId": job["jobId"], "text": payload}, key)
            except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as error:
                print(f"no se pudo enviar un trozo: {error}", flush=True)

        try:
            reply = ask_hermes(job, on_delta=flush_delta)
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
            reply = parse_streamed(
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
