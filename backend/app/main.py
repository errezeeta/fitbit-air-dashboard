from __future__ import annotations

import os
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Literal
from urllib.parse import urlencode
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import httpx
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.responses import RedirectResponse
from pydantic import BaseModel, Field

from backend.app.auth_service import GoogleAuthService
from backend.app.google_health import GoogleHealthClient
from backend.app.hermes_client import HermesClient
from backend.app.oauth import (
    build_google_authorization_url,
    create_oauth_state,
    verify_oauth_state,
)
from backend.app.secret_store import GoogleRefreshTokenSecret
from backend.app.tokens import verify_token


MOBILE_CALLBACK_URI = "fitbitair://oauth/callback"


@dataclass(frozen=True)
class AppSettings:
    google_client_id: str = ""
    google_client_secret: str = ""
    google_redirect_uri: str = ""
    allowed_email: str = ""
    app_signing_secret: str = ""
    google_project_id: str = ""
    refresh_token_secret_name: str = ""
    hermes_api_base_url: str = ""
    hermes_api_server_key: str = ""

    @classmethod
    def from_environment(cls) -> AppSettings:
        return cls(
            google_client_id=os.getenv("GOOGLE_OAUTH_CLIENT_ID", ""),
            google_client_secret=os.getenv("GOOGLE_OAUTH_CLIENT_SECRET", ""),
            google_redirect_uri=os.getenv("GOOGLE_OAUTH_REDIRECT_URI", ""),
            allowed_email=os.getenv("GOOGLE_ALLOWED_EMAIL", ""),
            app_signing_secret=os.getenv("APP_SIGNING_SECRET", ""),
            google_project_id=os.getenv("GOOGLE_CLOUD_PROJECT", ""),
            refresh_token_secret_name=os.getenv(
                "GOOGLE_REFRESH_TOKEN_SECRET_NAME", ""
            ),
            hermes_api_base_url=os.getenv("HERMES_API_BASE_URL", ""),
            hermes_api_server_key=os.getenv("HERMES_API_SERVER_KEY", ""),
        )


class OAuthExchangeRequest(BaseModel):
    handoff: str = Field(min_length=1, max_length=4096)
    app_state: str = Field(min_length=16, max_length=128)
    code_verifier: str = Field(min_length=43, max_length=128)


class AssistantRequest(BaseModel):
    question: str = Field(min_length=1, max_length=800)
    range: Literal["week", "month"] = "week"
    timezone: str = Field(default="UTC", min_length=1, max_length=100)


def create_app(
    *,
    settings: AppSettings | None = None,
    refresh_token_store=None,
    auth_service=None,
    health_client=None,
    hermes_client=None,
    http_client: httpx.Client | None = None,
) -> FastAPI:
    settings = settings or AppSettings.from_environment()
    owned_http_client = http_client is None
    http_client = http_client or httpx.Client()

    if refresh_token_store is None and settings.google_project_id and settings.refresh_token_secret_name:
        refresh_token_store = GoogleRefreshTokenSecret(
            project_id=settings.google_project_id,
            secret_name=settings.refresh_token_secret_name,
        )

    oauth_ready = all(
        (
            settings.google_client_id,
            settings.google_client_secret,
            settings.google_redirect_uri,
            settings.allowed_email,
            settings.app_signing_secret,
            refresh_token_store,
        )
    )
    if auth_service is None and oauth_ready:
        auth_service = GoogleAuthService(
            client_id=settings.google_client_id,
            client_secret=settings.google_client_secret,
            redirect_uri=settings.google_redirect_uri,
            allowed_email=settings.allowed_email,
            state_signing_secret=settings.app_signing_secret,
            session_signing_secret=settings.app_signing_secret,
            refresh_token_store=refresh_token_store,
            http_client=http_client,
            id_token_verifier=_verify_google_id_token,
        )

    health_ready = all(
        (
            settings.google_client_id,
            settings.google_client_secret,
            settings.app_signing_secret,
            settings.allowed_email,
            refresh_token_store,
        )
    )
    if health_client is None and health_ready:
        health_client = GoogleHealthClient(
            client_id=settings.google_client_id,
            client_secret=settings.google_client_secret,
            http_client=http_client,
            refresh_token_store=refresh_token_store,
        )
    if hermes_client is None and settings.hermes_api_base_url and settings.hermes_api_server_key:
        hermes_client = HermesClient(
            api_base_url=settings.hermes_api_base_url,
            api_server_key=settings.hermes_api_server_key,
            http_client=http_client,
        )

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        try:
            yield
        finally:
            if owned_http_client:
                http_client.close()

    app = FastAPI(
        title="Fitbit Air private health backend",
        version="0.3.0",
        lifespan=lifespan,
    )

    @app.get("/healthz")
    def healthz() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/api/status")
    def api_status() -> dict[str, bool]:
        return {
            "googleOAuthConfigured": bool(oauth_ready),
            "healthConfigured": bool(health_ready),
            "assistantConfigured": hermes_client is not None,
        }

    @app.get("/auth/google/start")
    def google_auth_start(
        app_state: str = Query(min_length=16, max_length=128),
        code_challenge: str = Query(pattern=r"^[A-Za-z0-9_-]{43}$"),
    ) -> dict[str, str]:
        if not oauth_ready:
            raise HTTPException(status_code=503, detail="Google OAuth is not configured")
        state = create_oauth_state(
            app_state=app_state,
            code_challenge=code_challenge,
            secret=settings.app_signing_secret,
        )
        authorization_url = build_google_authorization_url(
            client_id=settings.google_client_id,
            redirect_uri=settings.google_redirect_uri,
            signed_state=state,
        )
        return {"authorizationUrl": authorization_url, "state": state}

    @app.get("/oauth/google/callback")
    def google_oauth_callback(
        code: str | None = Query(default=None, max_length=4096),
        state: str | None = Query(default=None, max_length=4096),
        error: str | None = Query(default=None, max_length=256),
    ) -> RedirectResponse:
        if error:
            response = RedirectResponse(
                f"{MOBILE_CALLBACK_URI}?error=authorization_cancelled", status_code=303
            )
            _set_private_redirect_headers(response)
            return response
        if not code or not state:
            raise HTTPException(status_code=400, detail="OAuth callback is incomplete")
        try:
            state_claims = verify_oauth_state(state, secret=settings.app_signing_secret)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="OAuth state is invalid or expired") from exc
        if auth_service is None:
            raise HTTPException(status_code=503, detail="Google OAuth is not configured")
        try:
            handoff = auth_service.complete_google_callback(code=code, state=state)
        except PermissionError as exc:
            raise HTTPException(status_code=403, detail="Google account authorization failed") from exc
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="OAuth callback is invalid") from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail="Google token exchange failed") from exc
        target = f"{MOBILE_CALLBACK_URI}?{urlencode({'handoff': handoff, 'app_state': state_claims['app_state']})}"
        response = RedirectResponse(target, status_code=303)
        _set_private_redirect_headers(response)
        return response

    @app.post("/auth/google/exchange")
    def google_auth_exchange(request: OAuthExchangeRequest) -> dict[str, str]:
        if auth_service is None:
            raise HTTPException(status_code=503, detail="Google OAuth is not configured")
        try:
            return auth_service.exchange_handoff(
                handoff=request.handoff,
                app_state=request.app_state,
                code_verifier=request.code_verifier,
            )
        except PermissionError as exc:
            raise HTTPException(status_code=403, detail="Google account authorization failed") from exc
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="OAuth request is invalid or expired") from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail="Google token exchange failed") from exc

    @app.get("/api/dashboard")
    def get_dashboard(
        range_name: Literal["week", "month"] = Query(default="week", alias="range"),
        timezone_name: str = Query(default="UTC", alias="timezone", min_length=1, max_length=100),
        authorization: str | None = Header(default=None),
    ) -> dict:
        if health_client is None or refresh_token_store is None:
            raise HTTPException(status_code=503, detail="Google Health is not configured")
        session = _session_claims(authorization, settings)
        if session.get("email", "").casefold() != settings.allowed_email.casefold():
            raise HTTPException(status_code=403, detail="Google account is not authorized")
        if session.get("scope") != "dashboard":
            raise HTTPException(status_code=403, detail="Session does not allow dashboard access")
        try:
            ZoneInfo(timezone_name)
        except (ZoneInfoNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=400, detail="Timezone is invalid") from exc
        try:
            refresh_token = refresh_token_store.read_refresh_token()
            return health_client.load_dashboard(
                refresh_token=refresh_token,
                timezone_name=timezone_name,
                days=7 if range_name == "week" else 30,
            )
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail="Google Health request failed") from exc

    @app.post("/api/assistant")
    def ask_assistant(
        request: AssistantRequest,
        authorization: str | None = Header(default=None),
    ) -> dict[str, str]:
        if hermes_client is None:
            raise HTTPException(status_code=503, detail="Hermes is not configured")
        if health_client is None or refresh_token_store is None:
            raise HTTPException(status_code=503, detail="Google Health is not configured")
        session = _session_claims(authorization, settings)
        if session.get("email", "").casefold() != settings.allowed_email.casefold():
            raise HTTPException(status_code=403, detail="Google account is not authorized")
        if session.get("scope") != "dashboard":
            raise HTTPException(status_code=403, detail="Session does not allow assistant access")
        try:
            ZoneInfo(request.timezone)
        except (ZoneInfoNotFoundError, ValueError) as exc:
            raise HTTPException(status_code=400, detail="Timezone is invalid") from exc
        try:
            refresh_token = refresh_token_store.read_refresh_token()
            dashboard = health_client.load_dashboard(
                refresh_token=refresh_token,
                timezone_name=request.timezone,
                days=7 if request.range == "week" else 30,
            )
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail="Google Health request failed") from exc
        try:
            answer = hermes_client.ask(question=request.question, dashboard=dashboard)
        except PermissionError as exc:
            raise HTTPException(status_code=503, detail="Hermes API tool access is not restricted") from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail="Hermes assistant request failed") from exc
        return {"answer": answer, "dataRange": request.range}

    return app


def _session_claims(authorization: str | None, settings: AppSettings) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Bearer session token required")
    token = authorization.removeprefix("Bearer ").strip()
    try:
        return verify_token(token, secret=settings.app_signing_secret)
    except ValueError as exc:
        raise HTTPException(status_code=401, detail="Session token is invalid or expired") from exc


def _verify_google_id_token(token: str, audience: str) -> dict:
    from google.auth.transport.requests import Request
    from google.oauth2 import id_token

    return id_token.verify_oauth2_token(token, Request(), audience=audience)


def _set_private_redirect_headers(response: RedirectResponse) -> None:
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"
    response.headers["Referrer-Policy"] = "no-referrer"


app = create_app()
