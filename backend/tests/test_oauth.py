import unittest
from urllib.parse import parse_qs, urlparse

from backend.app.oauth import build_google_authorization_url


class GoogleOAuthUrlTest(unittest.TestCase):
    def test_requests_only_read_scopes_and_keeps_client_secret_off_url(self):
        url = build_google_authorization_url(
            client_id="public-client-id",
            redirect_uri="https://fitbit-air.run.app/oauth/google/callback",
            signed_state="signed-state",
        )
        query = parse_qs(urlparse(url).query)
        scopes = set(query["scope"][0].split())

        self.assertEqual(
            {
                "openid",
                "email",
                "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
                "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
            },
            scopes,
        )
        self.assertEqual("https://accounts.google.com/o/oauth2/v2/auth", f"{urlparse(url).scheme}://{urlparse(url).netloc}{urlparse(url).path}")
        self.assertEqual("code", query["response_type"][0])
        self.assertEqual("offline", query["access_type"][0])
        self.assertEqual("consent", query["prompt"][0])
        self.assertEqual("signed-state", query["state"][0])
        self.assertNotIn("code_challenge", query)
        self.assertNotIn("code_challenge_method", query)
        self.assertNotIn("client_secret", query)


    def test_signed_oauth_state_binds_the_mobile_nonce_and_pkce_verifier(self):
        from backend.app.oauth import (
            create_oauth_state,
            verify_code_verifier,
            verify_oauth_state,
        )

        verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        import base64
        import hashlib

        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
        state = create_oauth_state(
            app_state="mobile-nonce",
            code_challenge=challenge,
            secret="state-signing-key",
            now=1_700_000_000,
        )

        claims = verify_oauth_state(state, secret="state-signing-key", now=1_700_000_001)

        self.assertEqual("mobile-nonce", claims["app_state"])
        self.assertTrue(verify_code_verifier(claims, verifier))
        self.assertFalse(verify_code_verifier(claims, "wrong-verifier"))
        with self.assertRaises(ValueError):
            verify_oauth_state(state, secret="other-key", now=1_700_000_001)


if __name__ == "__main__":
    unittest.main()
