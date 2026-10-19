import asyncio
import unittest

import httpx

from backend.app.assistant_queue import AssistantQueue
from backend.app.main import AppSettings, create_app
from backend.app.tokens import DEFAULT_AUDIENCE, DEFAULT_ISSUER, sign_token


BRIDGE_KEY = "bridge-key-from-secret-manager"


class FakeRefreshTokenStore:
    def read_refresh_token(self):
        return "server-side-google-refresh-token"


class FakeHealthClient:
    def load_dashboard(self, **kwargs):
        return {"status": "ready", "stepsToday": 6100}


def _session_token(secret: str) -> str:
    now = int(__import__("time").time())
    return sign_token(
        {
            "iss": DEFAULT_ISSUER,
            "aud": DEFAULT_AUDIENCE,
            "sub": "user-1",
            "email": "javier@example.com",
            "iat": now,
            "exp": now + 600,
            "scope": "dashboard",
        },
        secret=secret,
    )


class AssistantQueueTest(unittest.TestCase):
    def test_claim_returns_a_job_once_and_answer_unblocks_the_waiter(self):
        queue = AssistantQueue()
        job_id = queue.enqueue(
            question="¿Cómo dormí?",
            range_name="week",
            timezone="Europe/Madrid",
            dashboard={"status": "ready"},
        )

        claimed = queue.claim()
        self.assertEqual(job_id, claimed["jobId"])
        self.assertEqual("¿Cómo dormí?", claimed["question"])
        # A second poll while the job is being worked on must not steal it.
        self.assertIsNone(queue.claim())

        self.assertTrue(queue.answer(job_id, "Dormiste 7 h 30 min."))
        self.assertEqual("Dormiste 7 h 30 min.", queue.wait(job_id, timeout=2).answer)
        self.assertFalse(queue.answer("unknown-job", "nada"))

    def test_an_answer_carries_the_optional_chart_and_chips(self):
        queue = AssistantQueue()
        job_id = queue.enqueue(
            question="¿Cómo dormí?", range_name="week", timezone="UTC", dashboard={}
        )
        queue.claim()

        queue.answer(
            job_id,
            "Dormiste 7 h 30 min de media.",
            chart={
                "kind": "bars",
                "title": "Horas de sueño",
                "unit": "min",
                "points": [{"label": "L", "value": 430, "date": "2026-10-12"}],
                "goal": None,
            },
            suggestions=["¿Y la semana pasada?", "¿Cuál fue mi mejor noche?"],
        )

        reply = queue.wait(job_id, timeout=2)
        self.assertEqual("Dormiste 7 h 30 min de media.", reply.answer)
        self.assertEqual("bars", reply.chart["kind"])
        self.assertEqual(2, len(reply.suggestions))
        self.assertEqual(
            {"answer", "chart", "suggestions"},
            set(reply.to_payload()),
        )

    def test_a_plain_answer_has_no_chart_and_empty_chips(self):
        queue = AssistantQueue()
        job_id = queue.enqueue(question="hola", range_name="week", timezone="UTC", dashboard={})
        queue.claim()
        queue.answer(job_id, "Sin gráfico.")

        reply = queue.wait(job_id, timeout=2)
        self.assertIsNone(reply.chart)
        self.assertEqual([], reply.to_payload()["suggestions"])

    def test_wait_gives_up_when_nobody_answers(self):
        queue = AssistantQueue()
        job_id = queue.enqueue(
            question="hola",
            range_name="week",
            timezone="UTC",
            dashboard={},
        )
        self.assertIsNone(queue.wait(job_id, timeout=0.3))

    def test_an_abandoned_claim_can_be_retaken_by_the_next_poll(self):
        queue = AssistantQueue(abandoned_after_seconds=0)
        queue.enqueue(question="hola", range_name="week", timezone="UTC", dashboard={})

        self.assertIsNotNone(queue.claim())
        # The bridge that took it died, so the question must not stay stuck.
        self.assertIsNotNone(queue.claim())


class BridgeEndpointTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.settings = AppSettings(
            google_client_id="web-client-id",
            google_client_secret="server-only-client-secret",
            google_redirect_uri="https://fitbit-air.run.app/oauth/google/callback",
            allowed_email="javier@example.com",
            app_signing_secret="server-only-session-secret",
            google_project_id="fitbit-air-project",
            refresh_token_secret_name="fitbit-air-google-refresh-token",
            hermes_bridge_secret=BRIDGE_KEY,
            hermes_bridge_wait_seconds=5.0,
        )
        app = create_app(
            settings=self.settings,
            refresh_token_store=FakeRefreshTokenStore(),
            auth_service=None,
            health_client=FakeHealthClient(),
            hermes_client=None,
        )
        self.client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        )

    async def asyncTearDown(self):
        await self.client.aclose()

    async def test_bridge_requires_the_key(self):
        response = await self.client.post("/bridge/claim")
        self.assertEqual(401, response.status_code)

        wrong = await self.client.post(
            "/bridge/claim", headers={"X-Bridge-Key": "not-the-key"}
        )
        self.assertEqual(401, wrong.status_code)

    async def test_the_status_endpoint_reports_the_bridge_as_configured(self):
        response = await self.client.get("/api/status")
        self.assertTrue(response.json()["assistantConfigured"])

    async def test_a_queued_question_is_answered_through_the_app_endpoint(self):
        session = _session_token("server-only-session-secret")

        async def answer_worker() -> str:
            # Stand in for the bridge host: claim, then post the answer back.
            for _ in range(200):
                claim = await self.client.post(
                    "/bridge/claim", headers={"X-Bridge-Key": BRIDGE_KEY}
                )
                job = claim.json()["job"]
                if job:
                    accepted = await self.client.post(
                        "/bridge/answer",
                        headers={"X-Bridge-Key": BRIDGE_KEY},
                        json={
                            "jobId": job["jobId"],
                            "answer": "Dormiste 7 h 30 min.",
                            "chart": {
                                "kind": "bars",
                                "title": "Sueño por noche",
                                "unit": "min",
                                "points": [
                                    {"label": "L", "value": 430, "date": "2026-10-12"},
                                    {"label": "M", "value": 470, "date": "2026-10-13"},
                                ],
                                "goal": None,
                            },
                            "suggestions": ["¿Y la semana pasada?"],
                        },
                    )
                    return "answered" if accepted.json()["accepted"] else "rejected"
                await asyncio.sleep(0.02)
            return "timeout"

        worker = asyncio.create_task(answer_worker())
        response = await self.client.post(
            "/api/assistant",
            headers={"Authorization": f"Bearer {session}"},
            json={"question": "¿Cómo dormí?", "range": "week", "timezone": "Europe/Madrid"},
        )
        outcome = await worker

        self.assertEqual("answered", outcome)
        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("Dormiste 7 h 30 min.", payload["answer"])
        self.assertEqual("bars", payload["chart"]["kind"])
        self.assertEqual(2, len(payload["chart"]["points"]))
        self.assertEqual(["¿Y la semana pasada?"], payload["suggestions"])
        self.assertNotIn(BRIDGE_KEY, response.text)

    async def test_a_malformed_chart_never_reaches_the_app(self):
        session = _session_token("server-only-session-secret")

        async def answer_worker() -> None:
            for _ in range(200):
                claim = await self.client.post(
                    "/bridge/claim", headers={"X-Bridge-Key": BRIDGE_KEY}
                )
                job = claim.json()["job"]
                if job:
                    await self.client.post(
                        "/bridge/answer",
                        headers={"X-Bridge-Key": BRIDGE_KEY},
                        json={
                            "jobId": job["jobId"],
                            "answer": "Sin gráfico válido.",
                            # 40 points and a non-numeric value: rejected whole.
                            "chart": {
                                "kind": "pie",
                                "points": [{"label": "L", "value": "muchos"}] * 40,
                            },
                        },
                    )
                    return
                await asyncio.sleep(0.02)

        worker = asyncio.create_task(answer_worker())
        response = await self.client.post(
            "/api/assistant",
            headers={"Authorization": f"Bearer {session}"},
            json={"question": "¿Cómo dormí?", "range": "week", "timezone": "Europe/Madrid"},
        )
        await worker

        self.assertEqual(200, response.status_code)
        self.assertIsNone(response.json()["chart"])
        self.assertEqual("Sin gráfico válido.", response.json()["answer"])

    async def test_the_self_test_endpoint_queues_without_an_app_session(self):
        queued = await self.client.post(
            "/bridge/enqueue",
            headers={"X-Bridge-Key": BRIDGE_KEY},
            json={"question": "prueba", "range": "week", "timezone": "UTC"},
        )
        self.assertEqual(200, queued.status_code)
        self.assertTrue(queued.json()["jobId"])

        pending = await self.client.get(
            "/bridge/pending", headers={"X-Bridge-Key": BRIDGE_KEY}
        )
        self.assertEqual(1, pending.json()["pending"])


class BridgeDisabledTest(unittest.IsolatedAsyncioTestCase):
    async def test_bridge_endpoints_are_off_without_the_secret(self):
        app = create_app(
            settings=AppSettings(allowed_email="javier@example.com"),
            refresh_token_store=FakeRefreshTokenStore(),
            auth_service=None,
            health_client=None,
            hermes_client=None,
        )
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/bridge/claim", headers={"X-Bridge-Key": "anything"}
            )
            self.assertEqual(503, response.status_code)


if __name__ == "__main__":
    unittest.main()
