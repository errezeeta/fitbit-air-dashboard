import base64
import unittest

from backend.app.secret_store import GoogleRefreshTokenSecret


class FakeResponse:
    def __init__(self, payload=None):
        self.payload = payload or {}

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


class FakeHttpSession:
    def __init__(self, secret_value):
        self.secret_value = secret_value
        self.calls = []

    def post(self, url, *, json, timeout):
        self.calls.append(("POST", url, json, timeout))
        return FakeResponse({"name": "version/1"})

    def get(self, url, *, timeout):
        self.calls.append(("GET", url, None, timeout))
        return FakeResponse(
            {"payload": {"data": base64.b64encode(self.secret_value.encode()).decode()}}
        )


class GoogleRefreshTokenSecretTest(unittest.TestCase):
    def test_refresh_token_is_persisted_and_read_through_secret_manager_api(self):
        session = FakeHttpSession("google-refresh-token")
        secret = GoogleRefreshTokenSecret(
            project_id="fitbit-air-project",
            secret_name="fitbit-air-google-refresh-token",
            session=session,
        )

        secret.write_refresh_token("google-refresh-token")
        recovered = secret.read_refresh_token()

        self.assertEqual("google-refresh-token", recovered)
        self.assertEqual(2, len(session.calls))
        method, url, body, timeout = session.calls[0]
        self.assertEqual("POST", method)
        self.assertTrue(url.endswith("/secrets/fitbit-air-google-refresh-token:addVersion"))
        self.assertEqual(
            base64.b64encode(b"google-refresh-token").decode(),
            body["payload"]["data"],
        )
        self.assertEqual(15.0, timeout)
        self.assertIn("/versions/latest:access", session.calls[1][1])


if __name__ == "__main__":
    unittest.main()
