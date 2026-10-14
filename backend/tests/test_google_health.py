import json
import unittest
from datetime import datetime, timezone
from urllib.parse import parse_qs
from zoneinfo import ZoneInfo

import httpx

from backend.app.google_health import GoogleHealthClient


class GoogleHealthClientTest(unittest.TestCase):
    def test_refreshes_access_token_and_fetches_minimal_dashboard_data(self):
        requests = []

        def handler(request):
            requests.append(request)
            if request.url.host == "oauth2.googleapis.com":
                return httpx.Response(200, json={"access_token": "short-lived-access-token"})
            if request.url.path.endswith("/steps/dataPoints:dailyRollUp"):
                return httpx.Response(
                    200,
                    json={
                        "rollupDataPoints": [
                            {
                                "civilStartTime": {"date": {"year": 2026, "month": 10, "day": 14}},
                                "steps": {"countSum": "6100"},
                            }
                        ]
                    },
                )
            if request.url.path.endswith("/daily-resting-heart-rate/dataPoints"):
                return httpx.Response(
                    200,
                    json={
                        "dataPoints": [
                            {"dailyRestingHeartRate": {"date": {"year": 2026, "month": 10, "day": 13}, "beatsPerMinute": "59"}},
                            {"dailyRestingHeartRate": {"date": {"year": 2026, "month": 10, "day": 14}, "beatsPerMinute": "58"}},
                        ]
                    },
                )
            if request.url.path.endswith("/sleep/dataPoints:reconcile"):
                return httpx.Response(
                    200,
                    json={
                        "dataPoints": [
                            {
                                "sleep": {
                                    "interval": {
                                        "startTime": "2026-10-13T22:00:00Z",
                                        "endTime": "2026-10-14T06:00:00Z",
                                    },
                                    "stages": [],
                                    "summary": {
                                        "minutesInSleepPeriod": "480",
                                        "minutesAsleep": "450",
                                        "minutesAwake": "30",
                                        "stagesSummary": [
                                            {"type": "LIGHT", "minutes": "200"},
                                            {"type": "DEEP", "minutes": "100"},
                                            {"type": "REM", "minutes": "150"},
                                            {"type": "AWAKE", "minutes": "30"},
                                        ],
                                    },
                                }
                            }
                        ]
                    },
                )
            return httpx.Response(404)

        client = httpx.Client(transport=httpx.MockTransport(handler))
        health = GoogleHealthClient(
            client_id="web-client-id",
            client_secret="web-client-secret",
            http_client=client,
        )

        dashboard = health.load_dashboard(
            refresh_token="google-refresh-token",
            timezone_name="UTC",
            days=7,
            now=datetime(2026, 10, 14, 10, 0, tzinfo=timezone.utc),
        )

        self.assertEqual("ready", dashboard["status"])
        self.assertEqual(6100, dashboard["stepsToday"])
        self.assertEqual(58, dashboard["restingHeartRate"])
        self.assertEqual(59, dashboard["previousRestingHeartRate"])
        self.assertEqual(450, dashboard["sleep"]["asleepMinutes"])
        self.assertEqual(480, dashboard["sleep"]["inBedMinutes"])
        self.assertEqual(7, len(dashboard["stepsTrend"]))
        self.assertEqual(7, len(dashboard["sleepTrend"]))
        self.assertNotIn("google-refresh-token", str(dashboard))
        self.assertNotIn("short-lived-access-token", str(dashboard))

        steps_request = next(r for r in requests if r.url.path.endswith("/steps/dataPoints:dailyRollUp"))
        steps_body = json.loads(steps_request.content)
        self.assertEqual({"year": 2026, "month": 10, "day": 8}, steps_body["range"]["start"]["date"])
        self.assertEqual({"year": 2026, "month": 10, "day": 15}, steps_body["range"]["end"]["date"])
        self.assertEqual("users/me/dataSourceFamilies/google-wearables", steps_body["dataSourceFamily"])
        for request in requests:
            if request.url.host == "health.googleapis.com":
                self.assertEqual("Bearer short-lived-access-token", request.headers["Authorization"])

        heart_rate_request = next(r for r in requests if r.url.path.endswith("/daily-resting-heart-rate/dataPoints"))
        heart_rate_filter = heart_rate_request.url.params["filter"]
        # A multi-word data type uses underscores in a filter identifier while
        # the path keeps hyphens. CamelCase is rejected with
        # INVALID_DATA_POINT_FILTER_DATA_TYPE_RESTRICTION.
        self.assertIn('daily_resting_heart_rate.date >= "2026-10-08"', heart_rate_filter)
        self.assertIn('daily_resting_heart_rate.date < "2026-10-15"', heart_rate_filter)
        self.assertNotIn("dailyRestingHeartRate", heart_rate_filter)

        sleep_request = next(r for r in requests if r.url.path.endswith("/sleep/dataPoints:reconcile"))
        sleep_filter = sleep_request.url.params["filter"]
        # Sleep supports only end-time filters; a start-time filter is rejected
        # with INVALID_ARGUMENT. See the filter catalogue in the Health API docs.
        self.assertIn('sleep.interval.civil_end_time >= "2026-10-08"', sleep_filter)
        self.assertIn('sleep.interval.civil_end_time < "2026-10-15"', sleep_filter)
        self.assertNotIn("start_time", sleep_filter)
    def test_logs_googles_own_error_text_without_leaking_the_refresh_token(self):
        def handler(request):
            if request.url.host == "oauth2.googleapis.com":
                return httpx.Response(200, json={"access_token": "access-token-value"})
            return httpx.Response(
                400,
                json={
                    "error": {
                        "code": 400,
                        "status": "INVALID_ARGUMENT",
                        "message": "Invalid filter: sleep.interval.start_time",
                    }
                },
            )

        client = httpx.Client(transport=httpx.MockTransport(handler))
        health = GoogleHealthClient(
            client_id="web-client-id",
            client_secret="web-client-secret",
            http_client=client,
        )

        with self.assertLogs("backend.app.google_health", level="WARNING") as captured:
            with self.assertRaises(RuntimeError):
                health.load_dashboard(
                    refresh_token="refresh-secret-value",
                    timezone_name="UTC",
                    days=7,
                    now=datetime(2026, 10, 14, 10, 0, tzinfo=timezone.utc),
                )

        logged = " ".join(captured.output)
        self.assertIn("Invalid filter: sleep.interval.start_time", logged)
        self.assertIn("400", logged)
        self.assertNotIn("refresh-secret-value", logged)
        self.assertNotIn("access-token-value", logged)
        self.assertNotIn("web-client-secret", logged)


if __name__ == "__main__":
    unittest.main()
