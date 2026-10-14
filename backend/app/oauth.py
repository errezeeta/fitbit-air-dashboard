import base64
import hashlib
import hmac
import re
import time
from urllib.parse import urlencode

from backend.app.tokens import sign_token, verify_token


GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_HEALTH_SCOPES = (
    "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
    "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
    "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
)
OAUTH_STATE_ISSUER = "fitbit-air-oauth"
OAUTH_STATE_AUDIENCE = "fitbit-air-mobile"
OAUTH_STATE_TTL_SECONDS = 600
_CODE_VERIFIER_PATTERN = re.compile(r"^[A-Za-z0-9._~-]{43,128}$")


def create_oauth_state(
    *,
    app_state: str,
    code_challenge: str,
    secret: str,
    now: int | None = None,
) -> str:
    if not app_state or not code_challenge:
        raise ValueError("app state and code challenge are required")
    issued_at = int(time.time()) if now is None else now
    return sign_token(
        {
            "iss": OAUTH_STATE_ISSUER,
            "aud": OAUTH_STATE_AUDIENCE,
            "app_state": app_state,
            "code_challenge": code_challenge,
            "iat": issued_at,
            "exp": issued_at + OAUTH_STATE_TTL_SECONDS,
        },
        secret=secret,
    )


def verify_oauth_state(
    state: str, *, secret: str, now: int | None = None
) -> dict:
    claims = verify_token(
        state,
        secret=secret,
        now=now,
        issuer=OAUTH_STATE_ISSUER,
        audience=OAUTH_STATE_AUDIENCE,
    )
    if not isinstance(claims.get("app_state"), str) or not isinstance(
        claims.get("code_challenge"), str
    ):
        raise ValueError("invalid OAuth state")
    return claims


def verify_code_verifier(claims: dict, verifier: str) -> bool:
    challenge = claims.get("code_challenge")
    if not isinstance(challenge, str) or not _CODE_VERIFIER_PATTERN.fullmatch(verifier):
        return False
    actual = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode("ascii")).digest())
    actual = actual.rstrip(b"=").decode("ascii")
    return hmac.compare_digest(actual, challenge)


def build_google_authorization_url(
    *,
    client_id: str,
    redirect_uri: str,
    signed_state: str,
) -> str:
    """Build a server-side OAuth URL with only the dashboard's read scopes."""
    query = urlencode(
        {
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": " ".join(("openid", "email", *GOOGLE_HEALTH_SCOPES)),
            "access_type": "offline",
            "prompt": "consent",
            "state": signed_state,
        }
    )
    return f"{GOOGLE_AUTHORIZATION_ENDPOINT}?{query}"
