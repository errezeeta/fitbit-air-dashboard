from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from typing import Any


DEFAULT_ISSUER = "fitbit-air-backend"
DEFAULT_AUDIENCE = "fitbit-air-app"
_HEADER = {"alg": "HS256", "typ": "JWT"}


def sign_token(claims: dict[str, Any], *, secret: str) -> str:
    if not secret:
        raise ValueError("signing secret must not be empty")
    header = _encode_json(_HEADER)
    payload = _encode_json(claims)
    signing_input = f"{header}.{payload}".encode("ascii")
    signature = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    return f"{header}.{payload}.{_encode_bytes(signature)}"


def verify_token(
    token: str,
    *,
    secret: str,
    now: int | None = None,
    issuer: str = DEFAULT_ISSUER,
    audience: str = DEFAULT_AUDIENCE,
) -> dict[str, Any]:
    try:
        encoded_header, encoded_payload, encoded_signature = token.split(".")
        header = json.loads(_decode_bytes(encoded_header))
        if not isinstance(header, dict) or header.get("alg") != "HS256":
            raise ValueError("unsupported token algorithm")

        signing_input = f"{encoded_header}.{encoded_payload}".encode("ascii")
        expected_signature = hmac.new(
            secret.encode("utf-8"), signing_input, hashlib.sha256
        ).digest()
        if not hmac.compare_digest(expected_signature, _decode_bytes(encoded_signature)):
            raise ValueError("invalid token signature")

        claims = json.loads(_decode_bytes(encoded_payload))
        if not isinstance(claims, dict):
            raise ValueError("invalid token claims")
        current_time = int(time.time()) if now is None else now
        expiry = claims.get("exp")
        if not isinstance(expiry, int) or expiry <= current_time:
            raise ValueError("token expired or missing expiry")
        if claims.get("iss") != issuer or claims.get("aud") != audience:
            raise ValueError("invalid token issuer or audience")
        issued_at = claims.get("iat")
        if issued_at is not None and (not isinstance(issued_at, int) or issued_at > current_time + 60):
            raise ValueError("invalid token issue time")
        return claims
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, TypeError) as exc:
        if isinstance(exc, ValueError) and str(exc).startswith(("unsupported", "invalid", "token expired")):
            raise
        raise ValueError("invalid token") from exc


def _encode_json(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return _encode_bytes(raw)


def _encode_bytes(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _decode_bytes(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)
