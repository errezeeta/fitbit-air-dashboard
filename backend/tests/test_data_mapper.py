import unittest

from backend.app.data_mapper import map_sleep_session


class SleepMapperTest(unittest.TestCase):
    def test_maps_session_summary_and_stage_minutes(self):
        session = {
            "interval": {
                "startTime": "2026-10-13T22:30:00Z",
                "endTime": "2026-10-14T06:45:00Z",
            },
            "stages": [
                {"type": "LIGHT", "startTime": "2026-10-13T22:30:00Z", "endTime": "2026-10-13T23:30:00Z"},
                {"type": "DEEP", "startTime": "2026-10-13T23:30:00Z", "endTime": "2026-10-14T01:00:00Z"},
                {"type": "REM", "startTime": "2026-10-14T01:00:00Z", "endTime": "2026-10-14T02:00:00Z"},
                {"type": "AWAKE", "startTime": "2026-10-14T02:00:00Z", "endTime": "2026-10-14T02:10:00Z"},
                {"type": "LIGHT", "startTime": "2026-10-14T02:10:00Z", "endTime": "2026-10-14T06:45:00Z"},
            ],
            "summary": {
                "minutesInSleepPeriod": "495",
                "minutesAsleep": "485",
                "minutesAwake": "10",
                "stagesSummary": [
                    {"type": "LIGHT", "minutes": "335"},
                    {"type": "DEEP", "minutes": "90"},
                    {"type": "REM", "minutes": "60"},
                    {"type": "AWAKE", "minutes": "10"},
                ],
            },
        }

        mapped = map_sleep_session(session, timezone_name="UTC")

        self.assertEqual(495, mapped["inBedMinutes"])
        self.assertEqual(485, mapped["asleepMinutes"])
        self.assertEqual("22:30", mapped["bedtime"])
        self.assertEqual("06:45", mapped["wakeTime"])
        self.assertEqual(
            {"LIGHT": 335, "DEEP": 90, "REM": 60, "AWAKE": 10},
            {stage["kind"]: stage["minutes"] for stage in mapped["stages"]},
        )

    def test_classic_restless_time_is_not_counted_as_sleep(self):
        session = {
            "interval": {
                "startTime": "2026-10-13T23:00:00Z",
                "endTime": "2026-10-14T00:00:00Z",
            },
            "stages": [
                {"type": "ASLEEP", "startTime": "2026-10-13T23:00:00Z", "endTime": "2026-10-13T23:40:00Z"},
                {"type": "RESTLESS", "startTime": "2026-10-13T23:40:00Z", "endTime": "2026-10-13T23:50:00Z"},
                {"type": "AWAKE", "startTime": "2026-10-13T23:50:00Z", "endTime": "2026-10-14T00:00:00Z"},
            ],
        }

        mapped = map_sleep_session(session, timezone_name="UTC")

        self.assertEqual(40, mapped["asleepMinutes"])
        self.assertEqual(60, mapped["inBedMinutes"])
        self.assertEqual(
            {"LIGHT": 40, "RESTLESS": 10, "AWAKE": 10},
            {stage["kind"]: stage["minutes"] for stage in mapped["stages"]},
        )


    def test_maps_and_sorts_steps_rollup_by_civil_date(self):
        response = {
            "rollupDataPoints": [
                {
                    "civilStartTime": {"date": {"year": 2026, "month": 10, "day": 14}},
                    "steps": {"countSum": "8200"},
                },
                {
                    "civilStartTime": {"date": {"year": 2026, "month": 10, "day": 13}},
                    "steps": {"countSum": "6100"},
                },
            ]
        }

        from backend.app.data_mapper import map_steps_rollup

        mapped = map_steps_rollup(response)

        self.assertEqual(
            [
                {"date": "2026-10-13", "steps": 6100},
                {"date": "2026-10-14", "steps": 8200},
            ],
            mapped,
        )


    def test_maps_and_sorts_daily_resting_heart_rate(self):
        response = {
            "dataPoints": [
                {
                    "dailyRestingHeartRate": {
                        "date": {"year": 2026, "month": 10, "day": 14},
                        "beatsPerMinute": "57",
                    }
                },
                {
                    "dailyRestingHeartRate": {
                        "date": {"year": 2026, "month": 10, "day": 13},
                        "beatsPerMinute": "59",
                    }
                },
            ]
        }

        from backend.app.data_mapper import map_resting_heart_rate

        mapped = map_resting_heart_rate(response)

        self.assertEqual(
            [
                {"date": "2026-10-13", "beatsPerMinute": 59},
                {"date": "2026-10-14", "beatsPerMinute": 57},
            ],
            mapped,
        )


if __name__ == "__main__":
    unittest.main()
