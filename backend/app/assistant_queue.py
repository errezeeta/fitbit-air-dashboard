from __future__ import annotations

import threading
import time
import uuid
from dataclasses import dataclass


@dataclass
class PendingQuestion:
    job_id: str
    question: str
    range_name: str
    timezone: str
    dashboard: dict
    created_at: float
    claimed_at: float | None = None


@dataclass
class AssistantReply:
    """What the bridge sends back: prose, plus whatever it decided to attach.

    `chart` and `suggestions` are optional because the agent chooses when they
    help; a plain-text answer is always valid.
    """

    answer: str
    chart: dict | None = None
    suggestions: list[str] | None = None

    def to_payload(self) -> dict:
        return {
            "answer": self.answer,
            "chart": self.chart,
            "suggestions": self.suggestions or [],
        }


class AssistantQueue:
    """Hand-off between a Cloud Run request and the bridge on the Hermes host.

    That host has no inbound port open, so it cannot be called: it polls for
    work instead. The waiting HTTP request parks here until the bridge posts an
    answer back, which keeps the app's flow unchanged (it just waits a moment).
    """

    def __init__(
        self,
        *,
        job_ttl_seconds: float = 180.0,
        abandoned_after_seconds: float = 20.0,
    ) -> None:
        self._lock = threading.Lock()
        self._jobs: dict[str, PendingQuestion] = {}
        self._answers: dict[str, tuple[float, AssistantReply]] = {}
        self._chunks: dict[str, tuple[float, str]] = {}
        self._job_ttl_seconds = job_ttl_seconds
        self._abandoned_after_seconds = abandoned_after_seconds

    def enqueue(
        self,
        *,
        question: str,
        range_name: str,
        timezone: str,
        dashboard: dict,
    ) -> str:
        with self._lock:
            self._prune_locked()
            job = PendingQuestion(
                job_id=uuid.uuid4().hex,
                question=question,
                range_name=range_name,
                timezone=timezone,
                dashboard=dashboard,
                created_at=time.time(),
            )
            self._jobs[job.job_id] = job
            return job.job_id

    def claim(self) -> dict | None:
        """Oldest unanswered question that is not already being worked on.

        A claim older than the abandon window can be retaken: a bridge that died
        mid-answer must not block the question forever.
        """
        now = time.time()
        with self._lock:
            self._prune_locked(now)
            for job in self._jobs.values():
                fresh_claim = (
                    job.claimed_at is not None
                    and now - job.claimed_at < self._abandoned_after_seconds
                )
                if fresh_claim:
                    continue
                job.claimed_at = now
                return {
                    "jobId": job.job_id,
                    "question": job.question,
                    "range": job.range_name,
                    "timezone": job.timezone,
                    "dashboard": job.dashboard,
                }
        return None

    def answer(
        self,
        job_id: str,
        answer: str,
        *,
        chart: dict | None = None,
        suggestions: list[str] | None = None,
    ) -> bool:
        with self._lock:
            if job_id not in self._jobs:
                return False
            self._answers[job_id] = (
                time.time() + self._job_ttl_seconds,
                AssistantReply(answer=answer, chart=chart, suggestions=suggestions),
            )
            self._jobs.pop(job_id, None)
            return True

    def wait(self, job_id: str, timeout: float) -> AssistantReply | None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._lock:
                stored = self._answers.get(job_id)
            if stored is not None:
                return stored[1]
            time.sleep(0.25)
        return None

    def pending_count(self) -> int:
        with self._lock:
            return len(self._jobs)

    # -- streaming -----------------------------------------------------------
    # The bridge forwards the answer as the agent writes it, so the app can show
    # it arriving instead of a spinner. Chunks are appended, never replaced.

    def append_chunk(self, job_id: str, text: str) -> bool:
        if not text:
            return False
        with self._lock:
            if job_id not in self._jobs:
                return False
            expires_at, current = self._chunks.get(
                job_id, (time.time() + self._job_ttl_seconds, "")
            )
            self._chunks[job_id] = (expires_at, current + text)
            return True

    def stream_state(self, job_id: str) -> dict:
        """What the waiting HTTP request needs: is it claimed, and how much text."""
        with self._lock:
            job = self._jobs.get(job_id)
            stored_answer = self._answers.get(job_id)
            chunk = self._chunks.get(job_id)
        return {
            "claimed": job is not None and job.claimed_at is not None,
            "partial": "" if chunk is None else chunk[1],
            "reply": None if stored_answer is None else stored_answer[1],
            "gone": job is None and stored_answer is None,
        }

    def _prune_locked(self, now: float | None = None) -> None:
        moment = time.time() if now is None else now
        expired = [
            job_id
            for job_id, job in self._jobs.items()
            if moment - job.created_at > self._job_ttl_seconds
        ]
        for job_id in expired:
            self._jobs.pop(job_id, None)
            self._chunks.pop(job_id, None)
        for job_id, (expires_at, _) in list(self._answers.items()):
            if expires_at < moment:
                self._answers.pop(job_id, None)
                self._chunks.pop(job_id, None)
