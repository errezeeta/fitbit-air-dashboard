from __future__ import annotations

import concurrent.futures
import logging
from datetime import date, datetime, timedelta
from typing import Protocol
from zoneinfo import ZoneInfo

import httpx

from backend.app.data_mapper import (
    map_distance_rollup,
    map_resting_heart_rate,
    map_sleep_session,
    map_steps_rollup,
)

logger = logging.getLogger(__name__)


GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
GOOGLE_HEALTH_BASE_URL = "https://health.googleapis.com/v4"
GOOGLE_WEARABLES = "users/me/dataSourceFamilies/google-wearables"
MAX_SLEEP_PAGES = 5
_GOOGLE_REQUEST_WORKERS = 4


class RotatingRefreshTokenStore(Protocol):
    def write_refresh_token(self, refresh_token: str) -> None: ...


class GoogleHealthClient:
    def __init__(
        self,
        *,
        client_id: str,
        client_secret: str,
        http_client: httpx.Client,
        refresh_token_store: RotatingRefreshTokenStore | None = None,
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self.http_client = http_client
        self.refresh_token_store = refresh_token_store

    def load_dashboard(
        self,
        *,
        refresh_token: str,
        timezone_name: str,
        days: int,
        now: datetime | None = None,
    ) -> dict:
        if days not in (7, 30):
            raise ValueError("dashboard range must be 7 or 30 days")
        zone = ZoneInfo(timezone_name)
        current = now or datetime.now(zone)
        if current.tzinfo is None:
            current = current.replace(tzinfo=zone)
        today = current.astimezone(zone).date()
        start_day = today - timedelta(days=days - 1)
        end_day = today + timedelta(days=1)

        access_token = self._refresh_access_token(refresh_token)
        # The dashboard needs four Google round trips; run them concurrently so
        # a cold Cloud Run request stays inside one network latency instead of
        # four. httpx.Client is safe to share across threads.
        with concurrent.futures.ThreadPoolExecutor(
            max_workers=_GOOGLE_REQUEST_WORKERS
        ) as pool:
            pending = {
                "steps": pool.submit(self._steps_rollup, access_token, start_day, end_day, days),
                "heart_rate": pool.submit(
                    self._daily_resting_heart_rate, access_token, start_day, end_day
                ),
                "sleep": pool.submit(self._sleep_sessions, access_token, start_day, end_day),
                "distance": pool.submit(
                    self._steps_rollup,
                    access_token,
                    start_day,
                    end_day,
                    data_type="distance",
                ),
            }
            steps_response = pending["steps"].result()
            heart_rate_response = pending["heart_rate"].result()
            sleep_response = pending["sleep"].result()
            distance_response = pending["distance"].result()
        return _map_dashboard(
            sleep_response=sleep_response,
            steps_response=steps_response,
            distance_response=distance_response,
            heart_rate_response=heart_rate_response,
            timezone_name=timezone_name,
            today=today,
            start_day=start_day,
            days=days,
        )

    def _refresh_access_token(self, refresh_token: str) -> str:
        try:
            response = self.http_client.post(
                GOOGLE_TOKEN_ENDPOINT,
                data={
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "refresh_token": refresh_token,
                    "grant_type": "refresh_token",
                },
                timeout=15.0,
            )
            response.raise_for_status()
            result = response.json()
        except httpx.HTTPStatusError as exc:
            logger.warning(
                "google access-token refresh rejected: %s %s",
                exc.response.status_code,
                _error_detail(exc.response),
            )
            raise RuntimeError("Google Health access-token refresh failed") from exc
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("google access-token refresh failed: %s", type(exc).__name__)
            raise RuntimeError("Google Health access-token refresh failed") from exc
        access_token = result.get("access_token")
        if not isinstance(access_token, str) or not access_token:
            raise RuntimeError("Google did not return an access token")
        rotated_refresh_token = result.get("refresh_token")
        if isinstance(rotated_refresh_token, str) and self.refresh_token_store:
            self.refresh_token_store.write_refresh_token(rotated_refresh_token)
        return access_token

    def _steps_rollup(
        self,
        access_token: str,
        start_day: date,
        end_day: date,
        days: int = 0,
        *,
        data_type: str = "steps",
    ) -> dict:
        url = (
            f"{GOOGLE_HEALTH_BASE_URL}/users/me/dataTypes/"
            f"{data_type}/dataPoints:dailyRollUp"
        )
        base_body = {
            "range": {
                "start": {"date": _date_object(start_day)},
                "end": {"date": _date_object(end_day)},
            },
            "windowSizeDays": 1,
            # Distance is not capped at 14 days, but Google rejects pages above
            # the data-type maximum, so never ask for more than 31 windows.
            "pageSize": max(min(days, 31), 1),
            "dataSourceFamily": GOOGLE_WEARABLES,
        }
        points = []
        page_token = None
        for _ in range(2):
            body = dict(base_body)
            if page_token:
                body["pageToken"] = page_token
            response = self._authorized_request("POST", url, access_token, json=body)
            points.extend(response.get("rollupDataPoints") or [])
            page_token = response.get("nextPageToken")
            if not page_token:
                break
        return {"rollupDataPoints": points}

    def _daily_resting_heart_rate(
        self, access_token: str, start_day: date, end_day: date
    ) -> dict:
        url = f"{GOOGLE_HEALTH_BASE_URL}/users/me/dataTypes/daily-resting-heart-rate/dataPoints"
        # The path keeps hyphens but a multi-word filter identifier uses
        # underscores; CamelCase is rejected as an unknown data type.
        query_filter = (
            f'daily_resting_heart_rate.date >= "{start_day.isoformat()}" AND '
            f'daily_resting_heart_rate.date < "{end_day.isoformat()}"'
        )
        response = self._authorized_request(
            "GET",
            url,
            access_token,
            params={
                "filter": query_filter,
                "pageSize": 100,
                "dataSourceFamily": GOOGLE_WEARABLES,
            },
        )
        return response

    def _sleep_sessions(
        self, access_token: str, start_day: date, end_day: date
    ) -> dict:
        url = f"{GOOGLE_HEALTH_BASE_URL}/users/me/dataTypes/sleep/dataPoints:reconcile"
        # Sleep rejects every filter except its end-time fields. Filtering on
        # sleep.interval.start_time returns INVALID_ARGUMENT, which surfaces to
        # the app as a generic data-loading failure.
        query_filter = (
            f'sleep.interval.civil_end_time >= "{start_day.isoformat()}" AND '
            f'sleep.interval.civil_end_time < "{end_day.isoformat()}"'
        )
        points = []
        page_token = None
        for _ in range(MAX_SLEEP_PAGES):
            params = {"filter": query_filter, "pageSize": 25}
            if page_token:
                params["pageToken"] = page_token
            response = self._authorized_request(
                "GET", url, access_token, params=params
            )
            points.extend(response.get("dataPoints") or [])
            page_token = response.get("nextPageToken")
            if not page_token:
                return {"dataPoints": points}
        if page_token:
            raise RuntimeError("Google Health returned too many sleep records")
        return {"dataPoints": points}

    def _authorized_request(
        self,
        method: str,
        url: str,
        access_token: str,
        *,
        params: dict | None = None,
        json: dict | None = None,
    ) -> dict:
        try:
            response = self.http_client.request(
                method,
                url,
                params=params,
                json=json,
                headers={"Authorization": f"Bearer {access_token}"},
                timeout=20.0,
            )
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise ValueError("invalid Google Health response")
            return payload
        except httpx.HTTPStatusError as exc:
            logger.warning(
                "google health %s %s rejected: %s %s",
                method,
                url,
                exc.response.status_code,
                _error_detail(exc.response),
            )
            raise RuntimeError("Google Health data request failed") from exc
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning(
                "google health %s %s failed: %s", method, url, type(exc).__name__
            )
            raise RuntimeError("Google Health data request failed") from exc


def _map_dashboard(
    *,
    sleep_response: dict,
    steps_response: dict,
    distance_response: dict | None = None,
    heart_rate_response: dict,
    timezone_name: str,
    today: date,
    start_day: date,
    days: int,
) -> dict:
    zone = ZoneInfo(timezone_name)
    steps = map_steps_rollup(steps_response)
    steps_by_day = {point["date"]: point["steps"] for point in steps}
    steps_trend = [
        {"date": (start_day + timedelta(days=index)).isoformat(),
         "steps": steps_by_day.get((start_day + timedelta(days=index)).isoformat(), 0)}
        for index in range(days)
    ]

    distance_points = map_distance_rollup(distance_response) if distance_response else []
    distance_by_day = {point["date"]: point["meters"] for point in distance_points}
    distance_trend = [
        {"date": (start_day + timedelta(days=index)).isoformat(),
         "meters": distance_by_day.get((start_day + timedelta(days=index)).isoformat(), 0)}
        for index in range(days)
        # Ask only for days that actually have a distance rollup, so the app
        # does not bar-chart empty days as if they were zero-distance days.
        if distance_by_day.get((start_day + timedelta(days=index)).isoformat(), 0) > 0
    ]

    heart_rate = map_resting_heart_rate(heart_rate_response)
    heart_rate = [
        point for point in heart_rate
        if start_day.isoformat() <= point["date"] <= today.isoformat()
    ]

    sleep_by_day: dict[str, tuple[bool, int, dict]] = {}
    for point in sleep_response.get("dataPoints") or []:
        session = point.get("sleep") or {}
        interval = session.get("interval") or {}
        end_value = interval.get("endTime")
        if not end_value:
            continue
        end_time = datetime.fromisoformat(end_value.replace("Z", "+00:00"))
        day = end_time.astimezone(zone).date()
        if day < start_day or day > today:
            continue
        mapped = map_sleep_session(session, timezone_name=timezone_name)
        metadata = session.get("metadata") or {}
        is_main_sleep = metadata.get("mainSleep") is True
        rank = (is_main_sleep, mapped["asleepMinutes"])
        key = day.isoformat()
        previous = sleep_by_day.get(key)
        if previous is None or rank > (previous[0], previous[1]):
            sleep_by_day[key] = (rank[0], rank[1], mapped)

    default_sleep = {
        "asleepMinutes": 0,
        "inBedMinutes": 0,
        "bedtime": "",
        "wakeTime": "",
        "stages": [],
    }
    sleep_trend = []
    for index in range(days):
        day = (start_day + timedelta(days=index)).isoformat()
        sleep_trend.append({"date": day, **(sleep_by_day.get(day, (False, 0, default_sleep))[2])})

    latest_sleep = next(
        (entry for entry in reversed(sleep_trend) if entry["asleepMinutes"] > 0),
        default_sleep,
    )
    rhr_values = [point["beatsPerMinute"] for point in heart_rate]
    status = "ready" if steps or heart_rate or sleep_by_day else "empty"
    return {
        "status": status,
        "sleep": latest_sleep,
        "stepsToday": steps_by_day.get(today.isoformat(), 0),
        "restingHeartRate": rhr_values[-1] if rhr_values else 0,
        "previousRestingHeartRate": rhr_values[-2] if len(rhr_values) > 1 else None,
        "stepsTrend": steps_trend,
        "sleepTrend": sleep_trend,
        "heartRateTrend": heart_rate,
        "distanceTrend": distance_trend,
        "distanceTodayMeters": distance_by_day.get(today.isoformat(), 0),
    }


def _date_object(value: date) -> dict[str, int]:
    return {"year": value.year, "month": value.month, "day": value.day}


def _error_detail(response: httpx.Response) -> str:
    """Google's own error text, for logs. Never contains tokens."""
    try:
        payload = response.json()
    except ValueError:
        return response.text[:300]
    if isinstance(payload, dict):
        error = payload.get("error")
        if isinstance(error, dict):
            fields = (error.get("code"), error.get("status"), error.get("message"))
            return " ".join(str(field) for field in fields if field)[:300]
        if error is not None:
            return str(error)[:300]
    return str(payload)[:300]
