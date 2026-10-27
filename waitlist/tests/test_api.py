import os
import unittest

os.environ["WAITLIST_STORE"] = "sqlite"
os.environ["WAITLIST_STATS_TOKEN"] = "test-token"

from fastapi.testclient import TestClient

from waitlist.app.main import app, store


class WaitlistApiTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.before = store.snapshot()

    def _signups(self):
        return len(store.snapshot()["signups"]) - len(self.before["signups"])

    def test_landing_page_is_served(self):
        response = self.client.get("/")
        self.assertEqual(200, response.status_code)
        self.assertIn("Your health data", response.text)
        # The pitch is the product: the page must not ship a third-party script.
        self.assertNotIn("googletagmanager", response.text)
        self.assertNotIn("google-analytics", response.text)

    def test_signup_is_stored_with_source_and_intent(self):
        response = self.client.post(
            "/api/waitlist",
            json={
                "email": "Someone@Example.COM",
                "source": "fitbitair",
                "previous_device": "fitbit",
                "intent": "reserve_pro",
            },
        )
        self.assertEqual(200, response.status_code)
        self.assertEqual({"ok": True}, response.json())
        self.assertEqual(1, self._signups())
        record = next(
            item for item in store.snapshot()["signups"] if item["email"] == "someone@example.com"
        )
        self.assertEqual("fitbitair", record["source"])
        self.assertEqual("reserve_pro", record["intent"])

    def test_duplicate_email_is_accepted_but_not_counted_twice(self):
        payload = {"email": "repeat@example.com", "source": "qs", "previous_device": "garmin"}
        self.assertEqual(200, self.client.post("/api/waitlist", json=payload).status_code)
        second = self.client.post("/api/waitlist", json=payload)
        # No signal about whether the address was already there.
        self.assertEqual(200, second.status_code)
        self.assertEqual({"ok": True}, second.json())
        self.assertEqual(1, self._signups())

    def test_invalid_email_is_rejected(self):
        for bad in ("no-at-sign", "a@b", "a b@c.com", ""):
            response = self.client.post("/api/waitlist", json={"email": bad})
            self.assertEqual(422, response.status_code, bad)

    def test_unknown_intent_and_device_are_rejected(self):
        self.assertEqual(
            422,
            self.client.post(
                "/api/waitlist", json={"email": "x@y.com", "intent": "maybe_later"}
            ).status_code,
        )
        self.assertEqual(
            422,
            self.client.post(
                "/api/waitlist", json={"email": "x@y.com", "previous_device": "whoop"}
            ).status_code,
        )

    def test_source_is_sanitised(self):
        self.client.post(
            "/api/waitlist",
            json={"email": "utm@example.com", "source": "FitbitAir?evil=<script>"},
        )
        record = next(
            item for item in store.snapshot()["signups"] if item["email"] == "utm@example.com"
        )
        self.assertEqual("fitbitair", record["source"])

    def test_reserve_click_is_counted_separately(self):
        before = store.snapshot()["events"].get("clicked_reserve|fitbitair", 0)
        self.client.post("/api/events", json={"type": "clicked_reserve", "source": "fitbitair"})
        after = store.snapshot()["events"].get("clicked_reserve|fitbitair", 0)
        self.assertEqual(before + 1, after)

    def test_unknown_event_is_rejected(self):
        self.assertEqual(
            422, self.client.post("/api/events", json={"type": "purchase"}).status_code
        )

    def test_stats_require_the_token(self):
        self.assertEqual(404, self.client.get("/api/stats").status_code)
        self.assertEqual(404, self.client.get("/api/stats?token=wrong").status_code)
        response = self.client.get("/api/stats?token=test-token")
        self.assertEqual(200, response.status_code)
        body = response.json()
        for key in ("signups_total", "by_source", "by_intent", "reserve_clicks", "events"):
            self.assertIn(key, body)

    def test_csv_export_uses_the_same_token(self):
        self.assertEqual(404, self.client.get("/api/signups").status_code)
        response = self.client.get("/api/signups?token=test-token")
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.text.startswith("email,source,previous_device,intent,created_at"))

    def test_summary_counts_match_the_decision_table(self):
        self.client.post(
            "/api/waitlist",
            json={
                "email": "metrics@example.com",
                "source": "fitbitair",
                "intent": "reserve_pro",
            },
        )
        self.client.post("/api/events", json={"type": "clicked_reserve", "source": "fitbitair"})
        body = self.client.get("/api/stats?token=test-token").json()
        self.assertGreaterEqual(body["signups_total"], 1)
        self.assertGreaterEqual(body["reserve_intent"], 1)
        self.assertGreaterEqual(body["reserve_clicks"], 1)
        self.assertGreater(body["reserve_rate_of_signups"], 0)


if __name__ == "__main__":
    unittest.main()
