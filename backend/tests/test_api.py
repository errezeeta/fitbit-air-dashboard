import time
import unittest

import httpx

from backend.app.main import AppSettings, create_app
from backend.app.oauth import create_oauth_state
from backend.app.tokens import DEFAULT_AUDIENCE, DEFAULT_ISSUER, sign_token


class FakeRefreshTokenStore:
    def read_refresh_token(self):
        return "server-side-google-refresh-token"

    def write_refresh_token(self, value):
        self.written = value


class FakeAuthService:
    def __init__(self):
        self.callback = None
        self.exchange = None

    def complete_google_callback(self, *, code, state):
        self.callback = {"code": code, "state": state}
        return "short-lived-handoff-ticket"

    def exchange_handoff(self, *, handoff, app_state, code_verifier):
        self.exchange = {
            "handoff": handoff,
            "app_state": app_state,
            "code_verifier": code_verifier,
        }
        return {"sessionToken": "app-session-token", "email": "javier@example.com"}


class FakeHealthClient:
    def __init__(self):
        self.call = None

    def load_dashboard(self, **kwargs):
        self.call = kwargs
        return {"status": "ready", "stepsToday": 6100}


class FakeHermesClient:
    def __init__(self):
        self.call = None

    def ask(self, *, question, dashboard):
        self.call = {"question": question, "dashboard": dashboard}
        return "Tu resumen muestra 6.100 pasos hoy."


class BackendApiTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.settings = AppSettings(
            google_client_id="web-client-id",
            google_client_secret="server-only-client-secret",
            google_redirect_uri="https://fitbit-air.run.app/oauth/google/callback",
            allowed_email="javier@example.com",
            app_signing_secret="server-only-session-secret",
            google_project_id="fitbit-air-project",
            refresh_token_secret_name="fitbit-air-google-refresh-token",
        )
        self.refresh_store = FakeRefreshTokenStore()
        self.auth_service = FakeAuthService()
        self.health_client = FakeHealthClient()
        self.hermes_client = FakeHermesClient()
        app = create_app(
            settings=self.settings,
            refresh_token_store=self.refresh_store,
            auth_service=self.auth_service,
            health_client=self.health_client,
            hermes_client=self.hermes_client,
        )
        self.client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        )

    async def asyncTearDown(self):
        await self.client.aclose()

    async def test_dashboard_requires_session_and_passes_server_token_to_health_client(self):
        response = await self.client.get("/api/dashboard?range=week&timezone=Europe/Madrid")
        self.assertEqual(401, response.status_code)

        now = int(time.time())
        session = sign_token(
            {
                "iss": DEFAULT_ISSUER,
                "aud": DEFAULT_AUDIENCE,
                "sub": "google-subject",
                "email": "javier@example.com",
                "iat": now,
                "exp": now + 300,
                "scope": "dashboard",
            },
            secret="server-only-session-secret",
        )
        response = await self.client.get(
            "/api/dashboard?range=week&timezone=Europe/Madrid",
            headers={"Authorization": f"Bearer {session}"},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(6100, response.json()["stepsToday"])
        self.assertIsNotNone(self.health_client.call)
        self.assertEqual("server-side-google-refresh-token", self.health_client.call["refresh_token"])
        self.assertEqual(7, self.health_client.call["days"])
        self.assertEqual("Europe/Madrid", self.health_client.call["timezone_name"])
        self.assertNotIn("server-side-google-refresh-token", response.text)
        self.assertNotIn("server-only-client-secret", response.text)

    async def test_dashboard_rejects_a_session_for_another_google_account(self):
        now = int(time.time())
        session = sign_token(
            {
                "iss": DEFAULT_ISSUER,
                "aud": DEFAULT_AUDIENCE,
                "sub": "another-google-subject",
                "email": "other@example.com",
                "iat": now,
                "exp": now + 300,
                "scope": "dashboard",
            },
            secret="server-only-session-secret",
        )

        response = await self.client.get(
            "/api/dashboard?range=month&timezone=UTC",
            headers={"Authorization": f"Bearer {session}"},
        )

        self.assertEqual(403, response.status_code)
        self.assertIsNone(self.health_client.call)


    async def test_assistant_fetches_health_data_server_side_and_forwards_it_to_hermes(self):
        now = int(time.time())
        session = sign_token(
            {
                "iss": DEFAULT_ISSUER,
                "aud": DEFAULT_AUDIENCE,
                "sub": "google-subject",
                "email": "javier@example.com",
                "iat": now,
                "exp": now + 300,
                "scope": "dashboard",
            },
            secret="server-only-session-secret",
        )

        response = await self.client.post(
            "/api/assistant",
            json={"question": "¿Cuántos pasos hice?", "range": "week", "timezone": "UTC"},
            headers={"Authorization": f"Bearer {session}"},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual("Tu resumen muestra 6.100 pasos hoy.", response.json()["answer"])
        self.assertEqual("¿Cuántos pasos hice?", self.hermes_client.call["question"])
        self.assertEqual({"status": "ready", "stepsToday": 6100}, self.hermes_client.call["dashboard"])
        self.assertNotIn("server-side-google-refresh-token", response.text)


    async def test_google_callback_returns_only_a_short_handoff_not_google_code(self):
        state = create_oauth_state(
            app_state="mobile-state-nonce",
            code_challenge="a" * 43,
            secret="server-only-session-secret",
        )
        response = await self.client.get(
            "/oauth/google/callback",
            params={"code": "one-time-google-code", "state": state},
            follow_redirects=False,
        )

        self.assertEqual(303, response.status_code)
        location = response.headers["location"]
        self.assertTrue(location.startswith("fitbitair://oauth/callback?"))
        self.assertIn("short-lived-handoff-ticket", location)
        self.assertIn("mobile-state-nonce", location)
        self.assertNotIn("one-time-google-code", location)
        self.assertNotIn(state, location)
        self.assertEqual("no-store", response.headers["cache-control"])
        self.assertEqual("one-time-google-code", self.auth_service.callback["code"])

    async def test_handoff_exchange_returns_only_the_app_session_token(self):
        response = await self.client.post(
            "/auth/google/exchange",
            json={
                "handoff": "short-lived-handoff-ticket",
                "app_state": "mobile-state-nonce",
                "code_verifier": "a" * 43,
            },
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {"sessionToken": "app-session-token", "email": "javier@example.com"},
            response.json(),
        )
        self.assertNotIn("google-refresh-token", response.text)
        self.assertEqual("short-lived-handoff-ticket", self.auth_service.exchange["handoff"])


    async def test_status_exposes_only_configuration_flags(self):
        response = await self.client.get("/api/status")

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {
                "googleOAuthConfigured": True,
                "healthConfigured": True,
                "assistantConfigured": True,
            },
            response.json(),
        )
        self.assertNotIn("server-only-client-secret", response.text)
        self.assertNotIn("server-only-session-secret", response.text)
    async def test_health_endpoint_avoids_the_path_cloud_run_intercepts(self):
        response = await self.client.get("/api/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual({"status": "ok"}, response.json())


if __name__ == "__main__":
    unittest.main()
