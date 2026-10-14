from __future__ import annotations

import json
from urllib.parse import urlparse

import httpx


_SYSTEM_PROMPT = (
    "Eres el asistente de bienestar de Fitbit Air. Responde en español, con tono claro y prudente. "
    "Describe tendencias sin diagnosticar ni sustituir a un profesional sanitario. "
    "Usa únicamente las métricas incluidas en esta petición y señala cuando falten datos."
)


class HermesClient:
    def __init__(
        self,
        *,
        api_base_url: str,
        api_server_key: str,
        http_client: httpx.Client,
    ):
        parsed = urlparse(api_base_url)
        if parsed.scheme != "https" and not (
            parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
        ):
            raise ValueError("Hermes API must use HTTPS outside localhost")
        if not api_server_key:
            raise ValueError("Hermes API server key is required")
        self.api_base_url = api_base_url.rstrip("/")
        self.api_server_key = api_server_key
        self.http_client = http_client

    def ask(self, *, question: str, dashboard: dict) -> str:
        self._require_toolless_api_server()
        user_content = (
            f"Pregunta del usuario: {question}\n\n"
            "Métricas recientes consultadas para esta petición (JSON):\n"
            f"{json.dumps(dashboard, ensure_ascii=False, separators=(',', ':'))}"
        )
        try:
            response = self.http_client.post(
                f"{self.api_base_url}/v1/chat/completions",
                headers={"Authorization": f"Bearer {self.api_server_key}"},
                json={
                    "model": "hermes-agent",
                    "messages": [
                        {"role": "system", "content": _SYSTEM_PROMPT},
                        {"role": "user", "content": user_content},
                    ],
                    "stream": False,
                },
                timeout=45.0,
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
            raise RuntimeError("Hermes assistant request failed") from exc
        if not isinstance(content, str) or not content.strip():
            raise RuntimeError("Hermes returned an empty answer")
        return content.strip()

    def _require_toolless_api_server(self) -> None:
        try:
            response = self.http_client.get(
                f"{self.api_base_url}/v1/toolsets",
                headers={"Authorization": f"Bearer {self.api_server_key}"},
                timeout=10.0,
            )
            response.raise_for_status()
            toolsets = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise RuntimeError("Hermes toolset restriction could not be verified") from exc
        if not isinstance(toolsets, list):
            raise RuntimeError("Hermes toolset response was invalid")
        enabled_tools = [
            tool
            for toolset in toolsets
            if isinstance(toolset, dict) and toolset.get("enabled") is True
            for tool in (toolset.get("tools") or [])
        ]
        if enabled_tools:
            raise PermissionError("Hermes API Server has tools enabled; health data was not sent")
