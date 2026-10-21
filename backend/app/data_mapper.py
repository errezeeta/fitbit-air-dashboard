from __future__ import annotations

from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo


_STAGE_ORDER = ("LIGHT", "DEEP", "REM", "RESTLESS", "AWAKE")
_ASLEEP_STAGES = {"LIGHT", "DEEP", "REM"}
_MILLIMETERS_PER_METER = 1000.0


def map_sleep_session(session: dict, timezone_name: str = "UTC") -> dict:
    """Convert a Google Health sleep point to the Android dashboard shape."""
    interval = session["interval"]
    start = _parse_timestamp(interval["startTime"])
    end = _parse_timestamp(interval["endTime"])
    if end < start:
        raise ValueError("sleep interval ends before it starts")

    summary = session.get("summary") or {}
    stage_minutes = _stage_minutes(summary, session.get("stages") or [])
    in_bed = _optional_int(summary.get("minutesInSleepPeriod"))
    if in_bed is None:
        in_bed = round((end - start).total_seconds() / 60)

    asleep = _optional_int(summary.get("minutesAsleep"))
    if asleep is None:
        asleep = sum(stage_minutes.get(kind, 0) for kind in _ASLEEP_STAGES)

    zone = ZoneInfo(timezone_name)
    return {
        "asleepMinutes": asleep,
        "inBedMinutes": in_bed,
        "bedtime": start.astimezone(zone).strftime("%H:%M"),
        "wakeTime": end.astimezone(zone).strftime("%H:%M"),
        # The clock time alone cannot be turned into "20 minutes after waking":
        # the app needs the instant, with its offset.
        "endTime": end.isoformat(),
        # Restlessness-derived fields. A missing value means Google did not
        # compute it for that night, which reads better as 0 than as null.
        "awakeMinutes": _optional_int(summary.get("minutesAwake")) or 0,
        "fallAsleepMinutes": _optional_int(summary.get("minutesToFallAsleep")) or 0,
        "afterWakeUpMinutes": _optional_int(summary.get("minutesAfterWakeUp")) or 0,
        "stages": [
            {"kind": kind, "minutes": stage_minutes[kind]}
            for kind in _STAGE_ORDER
            if stage_minutes.get(kind, 0) > 0
        ],
    }


def map_daily_series(
    response: dict,
    *,
    body_key: str,
    value_field: str,
    digits: int = 1,
) -> list[dict]:
    """Flatten any daily Google Health record into ISO-dated numeric points.

    Every ``Daily*`` data type (HRV, SpO2, respiratory rate, VO2 max...) is the
    same shape: a civil date plus one interesting number. One generic reader
    keeps the dashboard payload uniform, so the app renders them all with a
    single card component instead of five bespoke ones.
    """
    points: list[dict] = []
    for point in response.get("dataPoints") or []:
        record = point.get(body_key) or {}
        iso_date = _date_string(record.get("date"))
        raw = record.get(value_field)
        if iso_date is None or raw is None:
            continue
        value = _optional_float(raw)
        if value is None:
            continue
        points.append({"date": iso_date, "value": round(value, digits)})
    points.sort(key=lambda item: item["date"])
    return points


def map_sleep_temperature(response: dict) -> list[dict]:
    """Nightly skin temperature minus the user's own 30-day baseline.

    The absolute nightly value means nothing on its own; the deviation from the
    baseline is the signal (illness, alcohol, overtraining).
    """
    points: list[dict] = []
    for point in response.get("dataPoints") or []:
        record = point.get("dailySleepTemperatureDerivations") or {}
        iso_date = _date_string(record.get("date"))
        nightly = _optional_float(record.get("nightlyTemperatureCelsius"))
        baseline = _optional_float(record.get("baselineTemperatureCelsius"))
        if iso_date is None or nightly is None or baseline is None:
            continue
        points.append({"date": iso_date, "value": round(nightly - baseline, 2)})
    points.sort(key=lambda item: item["date"])
    return points


def map_exercise_sessions(response: dict, limit: int = 10) -> list[dict]:
    """Convert exercise data points to dashboard workout cards, newest first."""
    sessions: list[dict] = []
    for point in response.get("dataPoints") or []:
        exercise = point.get("exercise") or {}
        interval = exercise.get("interval") or {}
        start_value = interval.get("startTime")
        end_value = interval.get("endTime")
        if not start_value or not end_value:
            continue
        try:
            start = _parse_timestamp(start_value)
            end = _parse_timestamp(end_value)
        except ValueError:
            continue
        metrics = exercise.get("metricsSummary") or {}
        minutes = _duration_minutes(exercise.get("activeDuration"))
        if minutes == 0:
            minutes = round((end - start).total_seconds() / 60)
        distance_millimeters = metrics.get("distanceMillimeters")
        sessions.append(
            {
                "startTime": start.isoformat(),
                "date": start.date().isoformat(),
                "type": str(exercise.get("exerciseType") or "OTHER"),
                "name": str(exercise.get("displayName") or ""),
                "activeMinutes": max(minutes, 0),
                "caloriesKcal": round(_optional_float(metrics.get("caloriesKcal")) or 0.0),
                "averageHeartRate": _optional_int(
                    metrics.get("averageHeartRateBeatsPerMinute")
                )
                or 0,
                "distanceMeters": round(
                    (_optional_float(distance_millimeters) or 0.0)
                    / _MILLIMETERS_PER_METER
                ),
            }
        )
    sessions.sort(key=lambda item: item["startTime"], reverse=True)
    return sessions[:limit]


def map_steps_rollup(response: dict) -> list[dict]:
    """Convert Google Health daily step rollups to ISO-dated steps."""
    return _count_rollup(response, value_field="steps", count_field="countSum")


def map_distance_rollup(response: dict) -> list[dict]:
    """Convert Google Health daily distance rollups to ISO-dated meters.

    DistanceRollupValue reports millimetersSum; each day is rounded to whole
    meters to keep the payload small.
    """
    mapped = []
    for point in response.get("rollupDataPoints") or []:
        civil_start = point.get("civilStartTime") or {}
        iso_date = _date_string(civil_start.get("date"))
        millimeters = _optional_int((point.get("distance") or {}).get("millimetersSum"))
        if iso_date is None or millimeters is None:
            continue
        mapped.append(
            {"date": iso_date, "meters": round(millimeters / _MILLIMETERS_PER_METER)}
        )
    mapped.sort(key=lambda item: item["date"])
    return mapped


def _count_rollup(response: dict, *, value_field: str, count_field: str) -> list[dict]:
    mapped = []
    for point in response.get("rollupDataPoints") or []:
        civil_start = point.get("civilStartTime") or {}
        iso_date = _date_string(civil_start.get("date"))
        count = _optional_int((point.get(value_field) or {}).get(count_field))
        if iso_date is None or count is None:
            continue
        mapped.append({"date": iso_date, "steps": count})
    mapped.sort(key=lambda item: item["date"])
    return mapped


def map_resting_heart_rate(response: dict) -> list[dict]:
    """Convert daily resting heart-rate points to ISO-dated values."""
    mapped = []
    for point in response.get("dataPoints") or []:
        value = point.get("dailyRestingHeartRate") or {}
        iso_date = _date_string(value.get("date"))
        beats = _optional_int(value.get("beatsPerMinute"))
        if iso_date is None or beats is None:
            continue
        mapped.append({"date": iso_date, "beatsPerMinute": beats})
    return sorted(mapped, key=lambda item: item["date"])


def _stage_minutes(summary: dict, segments: list[dict]) -> dict[str, int]:
    totals: dict[str, int] = {}
    summarized_stages = summary.get("stagesSummary") or []
    if summarized_stages:
        for item in summarized_stages:
            kind = _dashboard_stage(item.get("type"))
            minutes = _optional_int(item.get("minutes"))
            if kind is not None and minutes is not None and minutes > 0:
                totals[kind] = totals.get(kind, 0) + minutes
        return totals

    for segment in segments:
        kind = _dashboard_stage(segment.get("type"))
        if kind is None:
            continue
        start = _parse_timestamp(segment["startTime"])
        end = _parse_timestamp(segment["endTime"])
        if end < start:
            raise ValueError("sleep stage ends before it starts")
        minutes = round((end - start).total_seconds() / 60)
        if minutes > 0:
            totals[kind] = totals.get(kind, 0) + minutes
    return totals


def _dashboard_stage(stage_type: str | None) -> str | None:
    if stage_type == "ASLEEP":
        return "LIGHT"
    if stage_type in _STAGE_ORDER:
        return stage_type
    return None


def _date_string(value: object) -> str | None:
    if not isinstance(value, dict):
        return None
    try:
        return date(
            int(str(value["year"])),
            int(str(value["month"])),
            int(str(value["day"])),
        ).isoformat()
    except (KeyError, TypeError, ValueError):
        return None


def _optional_int(value: int | str | None) -> int | None:
    if value is None or value == "":
        return None
    return int(value)


def _optional_float(value: float | int | str | None) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _duration_minutes(value: object) -> int:
    """Seconds-as-Duration (``"1234.5s"``) to whole minutes."""
    if not isinstance(value, str) or not value.endswith("s"):
        return 0
    try:
        return round(float(value[:-1]) / 60)
    except ValueError:
        return 0


def _parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed
