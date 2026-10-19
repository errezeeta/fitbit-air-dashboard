"""The reply envelope the assistant may use, and its parser.

The agent answers in prose, and *chooses* whether a quick chart or a few
follow-up chips help. Both are optional, so the plain-text path always works:
if the model ignores the envelope, or returns broken JSON, this module degrades
to the text instead of failing the request.

Envelope:

    {"answer": "...",
     "chart": {"kind": "bars", "title": "...", "unit": "pasos",
               "points": [{"label": "L", "value": 8420, "date": "2026-10-12"}],
               "goal": 8000},
     "suggestions": ["...", "..."]}
"""

from __future__ import annotations

import json
import re
from typing import Any

from backend.app.assistant_queue import AssistantReply

MAX_CHART_POINTS = 31
MAX_SUGGESTIONS = 4
MAX_TITLE_CHARS = 60
MAX_UNIT_CHARS = 16
MAX_LABEL_CHARS = 8
MAX_SUGGESTION_CHARS = 60
CHART_KINDS = ("bars", "line")

# Appended to whatever persona the runtime uses, so both the bridge profile and
# the direct Hermes API path ask for the same shape.
ENVELOPE_INSTRUCTIONS = """
Formato de respuesta (importante):
- Responde SIEMPRE con un único objeto JSON, sin texto alrededor ni ```:
  {"answer": "<tu respuesta en 2 a 4 frases>", "chart": <objeto o null>, "suggestions": [<hasta 3 frases cortas>]}
- "chart": úsalo solo si un gráfico deja la respuesta más clara. Si no aporta, pon null.
  Forma: {"kind": "bars"|"line", "title": "<título corto>", "unit": "<unidad>",
          "points": [{"label": "<etiqueta corta>", "value": <número>, "date": "<YYYY-MM-DD>"}],
          "goal": <número o null>}
  Usa como máximo 31 puntos y SOLO números que aparezcan en el resumen de métricas
  que se te ha dado: no inventes ni calcules cifras nuevas.
- "suggestions": 0 a 3 preguntas cortas de seguimiento que el usuario podría tocar.
- Nunca incluyas consejo médico ni diagnósticos.
""".strip()


def parse_reply(raw: str) -> AssistantReply:
    """Turn the model's raw text into prose plus optional extras."""
    text = (raw or "").strip()
    if not text:
        return AssistantReply(answer="", suggestions=[])
    payload = _extract_json_object(text)
    if payload is None:
        return AssistantReply(answer=_strip_fences(text), suggestions=[])

    answer = payload.get("answer")
    if not isinstance(answer, str) or not answer.strip():
        # The model used the envelope but forgot the prose: show the raw text
        # rather than an empty bubble.
        return AssistantReply(answer=_strip_fences(text), suggestions=[])
    return AssistantReply(
        answer=answer.strip(),
        chart=normalize_chart(payload.get("chart")),
        suggestions=normalize_suggestions(payload.get("suggestions")),
    )


def normalize_chart(candidate: Any) -> dict | None:
    """Keep a chart only if it is small, finite and chartable."""
    if not isinstance(candidate, dict):
        return None
    kind = candidate.get("kind")
    if kind not in CHART_KINDS:
        return None
    points = candidate.get("points")
    if not isinstance(points, list) or not 2 <= len(points) <= MAX_CHART_POINTS:
        return None

    cleaned: list[dict] = []
    for point in points:
        if not isinstance(point, dict):
            return None
        value = point.get("value")
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            return None
        if value != value or value in (float("inf"), float("-inf")):  # NaN / inf
            return None
        label = point.get("label")
        date = point.get("date")
        cleaned.append(
            {
                "label": label.strip()[:MAX_LABEL_CHARS] if isinstance(label, str) else "",
                "value": round(float(value), 2),
                "date": date if isinstance(date, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) else None,
            }
        )

    goal = candidate.get("goal")
    if isinstance(goal, bool) or not isinstance(goal, (int, float)):
        goal = None

    title = candidate.get("title")
    unit = candidate.get("unit")
    return {
        "kind": kind,
        "title": title.strip()[:MAX_TITLE_CHARS] if isinstance(title, str) else "",
        "unit": unit.strip()[:MAX_UNIT_CHARS] if isinstance(unit, str) else "",
        "points": cleaned,
        "goal": None if goal is None else round(float(goal), 2),
    }


def normalize_suggestions(candidate: Any) -> list[str]:
    if isinstance(candidate, str):
        candidate = [candidate]
    if not isinstance(candidate, list):
        return []
    cleaned = [
        item.strip()[:MAX_SUGGESTION_CHARS]
        for item in candidate
        if isinstance(item, str) and item.strip()
    ]
    return cleaned[:MAX_SUGGESTIONS]


def _strip_fences(text: str) -> str:
    fenced = re.fullmatch(r"```[a-zA-Z]*\s*(.*?)\s*```", text, flags=re.DOTALL)
    return fenced.group(1).strip() if fenced else text


def _extract_json_object(text: str) -> dict | None:
    """First JSON object in the text, tolerating prose or fences around it."""
    candidate = _strip_fences(text)
    if not candidate.startswith("{"):
        match = re.search(r"\{.*\}", candidate, flags=re.DOTALL)
        if match is None:
            return None
        candidate = match.group(0)
    try:
        decoded = json.loads(candidate)
    except ValueError:
        return None
    return decoded if isinstance(decoded, dict) else None
