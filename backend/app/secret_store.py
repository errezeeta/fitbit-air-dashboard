from __future__ import annotations

import base64
import re
from urllib.parse import quote

import google.auth
from google.auth.transport.requests import AuthorizedSession


_SECRET_NAME_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,255}$")
_PROJECT_ID_PATTERN = re.compile(r"^[A-Za-z0-9:-]{4,128}$")
_SECRET_MANAGER_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
_SECRET_MANAGER_API = "https://secretmanager.googleapis.com/v1"


class GoogleRefreshTokenSecret:
    """Persist only the Google refresh token in Secret Manager."""

    def __init__(self, *, project_id: str, secret_name: str, session=None):
        if not _PROJECT_ID_PATTERN.fullmatch(project_id):
            raise ValueError("invalid Google Cloud project ID")
        if not _SECRET_NAME_PATTERN.fullmatch(secret_name):
            raise ValueError("invalid Secret Manager secret name")
        self.project_id = project_id
        self.secret_name = secret_name
        self._base_url = (
            f"{_SECRET_MANAGER_API}/projects/{quote(project_id, safe='')}"
            f"/secrets/{quote(secret_name, safe='')}"
        )
        if session is None:
            credentials, _ = google.auth.default(scopes=[_SECRET_MANAGER_SCOPE])
            session = AuthorizedSession(credentials)
        self.session = session

    def write_refresh_token(self, refresh_token: str) -> None:
        encoded = base64.b64encode(refresh_token.encode("utf-8")).decode("ascii")
        try:
            response = self.session.post(
                f"{self._base_url}:addVersion",
                json={"payload": {"data": encoded}},
                timeout=15.0,
            )
            response.raise_for_status()
        except Exception as exc:
            raise RuntimeError("Could not store the Google refresh token") from exc

    def read_refresh_token(self) -> str:
        try:
            response = self.session.get(
                f"{self._base_url}/versions/latest:access", timeout=15.0
            )
            response.raise_for_status()
            encoded = response.json()["payload"]["data"]
            return base64.b64decode(encoded, validate=True).decode("utf-8")
        except Exception as exc:
            raise RuntimeError("Could not read the Google refresh token") from exc
