import base64
import hashlib
import unittest

import httpx

from backend.app.auth_service import GoogleAuthService
from backend.app.oauth import create_oauth_state
from backend.app.tokens import verify_token


class InMemoryRefreshTokenStore:
    def __init__(self):
        self.value = None

    def write_refresh_token(self, refresh_token):
        self.value = refresh_token


class GoogleAuthServiceTest(unittest.TestCase):
    def _service(self, *, store, email="javier@example.com", token_body=None):
        request_body = {}

        def token_handler(request):
            request_body.update(httpx.QueryParams(request.content.decode()))
            return httpx.Response(
                200,
                json=token_body or {
                    "access_token": "google-access-secret",
                    "refresh_token": "google-refresh-secret",
                    "id_token": "signed-id-token",
                    "token_type": "Bearer",
                },
            )

        client = httpx.Client(transport=httpx.MockTransport(token_handler))
        service = GoogleAuthService(
            client_id="web-client-id",
            client_secret="web-client-secret",
            redirect_uri="https://fitbit-air.run.app/oauth/google/callback",
            allowed_email=email,
            state_signing_secret="state-signing-key",
            session_signing_secret="session-signing-key",
            refresh_token_store=store,
            http_client=client,
            id_token_verifier=lambda token, audience: {
                "aud": audience,
                "email": "javier@example.com",
                "email_verified": True,
                "sub": "google-subject",
            },
        )
        return service, request_body

    def _state_and_verifier(self):
        verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        challenge = base64.urlsafe_b64encode(
            hashlib.sha256(verifier.encode()).digest()
        ).rstrip(b"=").decode()
        state = create_oauth_state(
            app_state="mobile-nonce",
            code_challenge=challenge,
            secret="state-signing-key",
            now=1_700_000_000,
        )
        return state, verifier, challenge

    def test_callback_stores_google_tokens_server_side_and_returns_short_handoff(self):
        state, verifier, challenge = self._state_and_verifier()
        store = InMemoryRefreshTokenStore()
        service, token_request_body = self._service(store=store)

        handoff = service.complete_google_callback(
            code="one-time-google-code",
            state=state,
            now=1_700_000_001,
        )
        claims = verify_token(
            handoff,
            secret="state-signing-key",
            now=1_700_000_002,
            issuer="fitbit-air-handoff",
            audience="fitbit-air-mobile-handoff",
        )

        self.assertEqual("google-refresh-secret", store.value)
        self.assertEqual("javier@example.com", claims["email"])
        self.assertEqual("mobile-nonce", claims["app_state"])
        self.assertEqual(challenge, claims["code_challenge"])
        self.assertEqual(1_700_000_301, claims["exp"])
        self.assertNotIn("google-access-secret", handoff)
        self.assertNotIn("google-refresh-secret", handoff)
        self.assertEqual("web-client-secret", token_request_body["client_secret"])
        self.assertEqual("authorization_code", token_request_body["grant_type"])
        self.assertEqual("one-time-google-code", token_request_body["code"])
        self.assertNotIn("code_verifier", token_request_body)

    def test_mobile_handoff_requires_pkce_before_issuing_an_app_session(self):
        state, verifier, _ = self._state_and_verifier()
        store = InMemoryRefreshTokenStore()
        service, _ = self._service(store=store)
        handoff = service.complete_google_callback(
            code="one-time-google-code",
            state=state,
            now=1_700_000_001,
        )

        with self.assertRaises(PermissionError):
            service.exchange_handoff(
                handoff=handoff,
                app_state="mobile-nonce",
                code_verifier="wrong-verifier",
                now=1_700_000_002,
            )

        result = service.exchange_handoff(
            handoff=handoff,
            app_state="mobile-nonce",
            code_verifier=verifier,
            now=1_700_000_002,
        )

        self.assertEqual({"sessionToken", "email"}, set(result))
        self.assertEqual("javier@example.com", result["email"])
        self.assertNotIn("google-access-secret", str(result))
        self.assertNotIn("google-refresh-secret", str(result))

    def test_google_account_must_match_allowlist_before_refresh_token_is_stored(self):
        state, _, _ = self._state_and_verifier()
        store = InMemoryRefreshTokenStore()
        service, _ = self._service(store=store, email="other@example.com")

        with self.assertRaises(PermissionError):
            service.complete_google_callback(
                code="one-time-google-code",
                state=state,
                now=1_700_000_001,
            )

        self.assertIsNone(store.value)


if __name__ == "__main__":
    unittest.main()
