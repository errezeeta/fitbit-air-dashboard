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

import fcntl
import json
import os
import queue
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

# The reply-parsing contract lives with the backend so both paths agree on it.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.app.assistant_reply import (
    ENVELOPE_INSTRUCTIONS,
    dashboard_detail,
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


THINKING_PHASES = (
    (0.0, "Preparando el análisis"),
    (2.5, "Analizando el resumen"),
    (6.0, "Dando forma a la respuesta"),
)


def thinking_stage(elapsed_seconds: float, detail: str) -> dict:
    """A truthful, small progression while Hermes is reasoning before text."""
    elapsed = max(0.0, elapsed_seconds)
    label = THINKING_PHASES[0][1]
    for threshold, candidate in THINKING_PHASES:
        if elapsed < threshold:
            break
        label = candidate
    return {"stage": "pensando", "label": label, "detail": detail}


def ask_hermes(job: dict, on_delta=None, on_stage=None):
    """Run one agent turn and stream text plus honest thinking phases.

    Hermes `stream-json` has real `system` and `text` events. Between those
    milestones it cannot expose private chain-of-thought, so the progress labels
    describe the actual elapsed analysis phase and keep the health-data context
    visible; they never pretend to be token-level reasoning.
    """
    prompt = build_prompt(job)
    environment = dict(os.environ)
    environment["HERMES_HOME"] = HERMES_HOME
    deadline = time.monotonic() + ANSWER_TIMEOUT
    detail = dashboard_detail(job.get("dashboard", {}), job.get("range", "week"))
    if on_stage is not None:
        on_stage("arranque", "Conectando con Hermes", "Preparando la consulta")
    process = subprocess.Popen(
        [HERMES_BIN, "chat", "-q", prompt, "-Q", "--format", "stream-json"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env=environment,
        cwd="/root",
        bufsize=1,
    )

    # Drain the pipe on a reader thread so the main loop can update the stage
    # during the model's quiet thinking time, rather than blocking on readline.
    output: queue.Queue[str | None] = queue.Queue()

    def read_output() -> None:
        try:
            if process.stdout is not None:
                for item in process.stdout:
                    output.put(item)
        finally:
            output.put(None)

    threading.Thread(target=read_output, name="hermes-stream-reader", daemon=True).start()

    raw = ""
    shown = 0
    exit_code = 0
    model_ready_at: float | None = None
    latest_phase: str | None = None
    text_started = False
    eof = False
    try:
        while not eof:
            now = time.monotonic()
            if now > deadline:
                process.kill()
                raise RuntimeError("hermes did not finish in time")
            try:
                line = output.get(timeout=min(0.35, max(0.01, deadline - now)))
            except queue.Empty:
                line = ""

            if line is None:
                eof = True
                continue

            if not line:
                # Before the first visible token, advance through calm status
                # phases instead of leaving "Conectando" on screen for the
                # entire model latency.
                if model_ready_at is not None and not text_started and on_stage is not None:
                    stage = thinking_stage(time.monotonic() - model_ready_at, detail)
                    if stage["label"] != latest_phase:
                        latest_phase = stage["label"]
                        on_stage(stage["stage"], stage["label"], stage["detail"])
                continue

            line = line.strip()
            if not line or line.startswith("session_id:"):
                continue
            try:
                event = json.loads(line)
            except ValueError:
                continue

            if event.get("type") == "system":
                if model_ready_at is None:
                    model_ready_at = time.monotonic()
                    stage = thinking_stage(0, detail)
                    latest_phase = stage["label"]
                    if on_stage is not None:
                        on_stage(stage["stage"], stage["label"], stage["detail"])
                continue

            if event.get("type") != "text" or not isinstance(event.get("text"), str):
                continue
            raw += event["text"]
            if on_delta is None:
                continue
            visible = streamable_prefix(raw)
            if len(visible) > shown:
                if not text_started:
                    text_started = True
                    if on_stage is not None:
                        on_stage("escribiendo", "Redactando la respuesta", "Respuesta en directo")
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
    if reply.chart and on_stage is not None:
        on_stage("grafico", "Dibujando el gráfico", f"{len(reply.chart['points'])} puntos")
    return reply


def main() -> int:
    # One bridge only: two instances double-claim jobs and answer from stale
    # code. The lock makes the second one exit instead of racing. The path must
    # live outside /tmp: a unit with PrivateTmp=true gets its own namespace and
    # would hold an unrelated file.
    lock_path = os.environ.get("BRIDGE_LOCK_FILE", "/root/.hermes/fitbit-air-bridge.lock")
    lock_handle = open(lock_path, "w", encoding="utf-8")  # noqa: SIM115 - held for the process lifetime
    try:
        fcntl.flock(lock_handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        print(f"ya hay otro puente activo (candado {lock_path}); salgo", flush=True)
        return 0

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

        def report_stage(stage: str, label: str, detail: str | None) -> None:
            payload = {"jobId": job["jobId"], "stage": stage, "label": label}
            if detail:
                payload["detail"] = detail
            try:
                call("/bridge/stage", payload, key)
            except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError):
                pass  # progress is nice to have; the answer is what matters

        try:
            reply = ask_hermes(job, on_delta=flush_delta, on_stage=report_stage)
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
