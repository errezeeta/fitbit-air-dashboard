import unittest

from backend.app.tokens import sign_token, verify_token


class SignedTokenTest(unittest.TestCase):
    def test_signed_claims_round_trip(self):
        claims = {
            "iss": "fitbit-air-backend",
            "aud": "fitbit-air-app",
            "sub": "user@example.com",
            "exp": 1_800_000_000,
        }

        token = sign_token(claims, secret="test-signing-key")

        self.assertEqual(
            claims,
            verify_token(token, secret="test-signing-key", now=1_700_000_000),
        )

    def test_rejects_tampering_and_expired_tokens(self):
        token = sign_token(
            {
                "iss": "fitbit-air-backend",
                "aud": "fitbit-air-app",
                "sub": "user@example.com",
                "exp": 1_700_000_001,
            },
            secret="test-signing-key",
        )

        with self.assertRaises(ValueError):
            verify_token(token + "x", secret="test-signing-key", now=1_700_000_000)
        with self.assertRaises(ValueError):
            verify_token(token, secret="test-signing-key", now=1_700_000_001)


if __name__ == "__main__":
    unittest.main()
