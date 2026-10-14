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
        "stages": [
            {"kind": kind, "minutes": stage_minutes[kind]}
            for kind in _STAGE_ORDER
            if stage_minutes.get(kind, 0) > 0
        ],
    }


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


def _parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed
