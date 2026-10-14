import json
import unittest

import httpx

from backend.app.hermes_client import HermesClient


class HermesClientTest(unittest.TestCase):
    def test_sends_only_minimal_metrics_to_stateless_chat_without_tools(self):
        requests = []

        def handler(request):
            requests.append(request)
            if request.url.path == "/v1/toolsets":
                return httpx.Response(200, json=[])
            if request.url.path == "/v1/chat/completions":
                return httpx.Response(
                    200,
                    json={"choices": [{"message": {"content": "Has dormido 7 h 30 min."}}]},
                )
            return httpx.Response(404)

        client = httpx.Client(transport=httpx.MockTransport(handler))
        hermes = HermesClient(
            api_base_url="https://hermes.example/",
            api_server_key="server-only-hermes-key",
            http_client=client,
        )

        answer = hermes.ask(
            question="¿Cómo fue mi sueño?",
            dashboard={"sleep": {"asleepMinutes": 450}, "stepsToday": 6100},
        )

        self.assertEqual("Has dormido 7 h 30 min.", answer)
        self.assertEqual(["/v1/toolsets", "/v1/chat/completions"], [r.url.path for r in requests])
        chat_request = requests[-1]
        self.assertEqual("Bearer server-only-hermes-key", chat_request.headers["Authorization"])
        body = json.loads(chat_request.content)
        self.assertNotIn("tools", body)
        self.assertNotIn("conversation", body)
        self.assertNotIn("previous_response_id", body)
        user_message = body["messages"][-1]["content"]
        self.assertIn("450", user_message)
        self.assertIn("¿Cómo fue mi sueño?", user_message)
        self.assertNotIn("server-only-hermes-key", user_message)

    def test_refuses_to_send_health_data_when_hermes_has_tools_enabled(self):
        def handler(request):
            if request.url.path == "/v1/toolsets":
                return httpx.Response(
                    200,
                    json=[{"name": "terminal", "enabled": True, "tools": ["run_command"]}],
                )
            self.fail("chat request must not be sent before toolset restriction is verified")

        client = httpx.Client(transport=httpx.MockTransport(handler))
        hermes = HermesClient(
            api_base_url="https://hermes.example",
            api_server_key="server-only-hermes-key",
            http_client=client,
        )

        with self.assertRaises(PermissionError):
            hermes.ask(question="Pregunta", dashboard={"stepsToday": 6100})


if __name__ == "__main__":
    unittest.main()
