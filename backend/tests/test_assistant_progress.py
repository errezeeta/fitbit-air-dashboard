import importlib.util
import sys
import unittest
from pathlib import Path


class ThinkingStageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[2]
        if str(root) not in sys.path:
            sys.path.insert(0, str(root))
        spec = importlib.util.spec_from_file_location(
            "hermes_bridge_thinking_test", root / "bridge" / "hermes_bridge.py"
        )
        if spec is None or spec.loader is None:
            raise RuntimeError("Could not load bridge for stage-policy test")
        cls.bridge = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.bridge)

    def test_progress_moves_through_three_distinct_wait_phases(self):
        detail = "7 días · pasos, sueño, pulso"

        phases = [
            self.bridge.thinking_stage(seconds, detail)["label"]
            for seconds in (0.0, 2.5, 6.0)
        ]

        self.assertEqual(
            ["Preparando el análisis", "Analizando el resumen", "Dando forma a la respuesta"],
            phases,
        )
        self.assertEqual(3, len(set(phases)))

    def test_thinking_phases_always_keep_the_actual_data_context(self):
        detail = "30 días · pasos, distancia, pulso"

        for seconds in (0.0, 3.0, 8.0):
            with self.subTest(seconds=seconds):
                stage = self.bridge.thinking_stage(seconds, detail)
                self.assertEqual("pensando", stage["stage"])
                self.assertEqual(detail, stage["detail"])

    def test_negative_elapsed_time_uses_the_first_phase(self):
        stage = self.bridge.thinking_stage(-1.0, "7 días · pasos")

        self.assertEqual("Preparando el análisis", stage["label"])


if __name__ == "__main__":
    unittest.main()
