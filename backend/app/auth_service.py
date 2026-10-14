from __future__ import annotations

import time
from typing import Callable, Protocol

import httpx

from backend.app.oauth import verify_code_verifier, verify_oauth_state
from backend.app.tokens import (
    DEFAULT_AUDIENCE,
    DEFAULT_ISSUER,
    sign_token,
    verify_token,
)


GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
HANDOFF_ISSUER = "fitbit-air-handoff"
HANDOFF_AUDIENCE = "fitbit-air-mobile-handoff"
HANDOFF_TTL_SECONDS = 5 * 60
SESSION_TTL_SECONDS = 30 * 24 * 60 * 60


class RefreshTokenStore(Protocol):
    def write_refresh_token(self, refresh_token: str) -> None: ...


IdTokenVerifier = Callable[[str, str], dict]


class GoogleAuthService:
    def __init__(
        self,
        *,
        client_id: str,
        client_secret: str,
        redirect_uri: str,
        allowed_email: str,
        state_signing_secret: str,
        session_signing_secret: str,
        refresh_token_store: RefreshTokenStore,
        http_client: httpx.Client,
        id_token_verifier: IdTokenVerifier,
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self.redirect_uri = redirect_uri
        self.allowed_email = allowed_email.casefold()
        self.state_signing_secret = state_signing_secret
        self.session_signing_secret = session_signing_secret
        self.refresh_token_store = refresh_token_store
        self.http_client = http_client
        self.id_token_verifier = id_token_verifier

    def complete_google_callback(
        self, *, code: str, state: str, now: int | None = None
    ) -> str:
        if not code:
            raise ValueError("authorization code is required")
        state_claims = verify_oauth_state(
            state, secret=self.state_signing_secret, now=now
        )

        try:
            response = self.http_client.post(
                GOOGLE_TOKEN_ENDPOINT,
                data={
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "code": code,
                    "grant_type": "authorization_code",
                    "redirect_uri": self.redirect_uri,
                },
                timeout=15.0,
            )
            response.raise_for_status()
            token_response = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise RuntimeError("Google OAuth code exchange failed") from exc

        id_token = token_response.get("id_token")
        refresh_token = token_response.get("refresh_token")
        if not isinstance(id_token, str) or not isinstance(refresh_token, str):
            raise RuntimeError("Google did not return the required OAuth tokens")

        try:
            identity = self.id_token_verifier(id_token, self.client_id)
        except Exception as exc:
            raise PermissionError("Google identity token is invalid") from exc

        email = identity.get("email")
        if (
            not isinstance(email, str)
            or email.casefold() != self.allowed_email
            or identity.get("email_verified") is not True
        ):
            raise PermissionError("Google account is not authorized for this app")
        subject = identity.get("sub")
        if not isinstance(subject, str) or not subject:
            raise PermissionError("Google identity token has no subject")

        self.refresh_token_store.write_refresh_token(refresh_token)
        issued_at = int(time.time()) if now is None else now
        return sign_token(
            {
                "iss": HANDOFF_ISSUER,
                "aud": HANDOFF_AUDIENCE,
                "sub": subject,
                "email": email,
                "app_state": state_claims["app_state"],
                "code_challenge": state_claims["code_challenge"],
                "iat": issued_at,
                "exp": issued_at + HANDOFF_TTL_SECONDS,
            },
            secret=self.state_signing_secret,
        )

    def exchange_handoff(
        self,
        *,
        handoff: str,
        app_state: str,
        code_verifier: str,
        now: int | None = None,
    ) -> dict[str, str]:
        claims = verify_token(
            handoff,
            secret=self.state_signing_secret,
            now=now,
            issuer=HANDOFF_ISSUER,
            audience=HANDOFF_AUDIENCE,
        )
        email = claims.get("email")
        subject = claims.get("sub")
        if (
            claims.get("app_state") != app_state
            or not isinstance(email, str)
            or email.casefold() != self.allowed_email
            or not isinstance(subject, str)
            or not subject
            or not verify_code_verifier(claims, code_verifier)
        ):
            raise PermissionError("Mobile OAuth handoff verification failed")

        issued_at = int(time.time()) if now is None else now
        session_token = sign_token(
            {
                "iss": DEFAULT_ISSUER,
                "aud": DEFAULT_AUDIENCE,
                "sub": subject,
                "email": email,
                "iat": issued_at,
                "exp": issued_at + SESSION_TTL_SECONDS,
                "scope": "dashboard",
            },
            secret=self.session_signing_secret,
        )
        return {"sessionToken": session_token, "email": email}
