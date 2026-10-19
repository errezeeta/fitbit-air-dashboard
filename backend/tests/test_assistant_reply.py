import unittest

from backend.app.assistant_reply import (
    MAX_CHART_POINTS,
    MAX_SUGGESTIONS,
    normalize_chart,
    normalize_suggestions,
    parse_reply,
)


class ParseReplyTest(unittest.TestCase):
    def test_a_clean_envelope_keeps_the_prose_the_chart_and_the_chips(self):
        reply = parse_reply(
            '{"answer": "Dormiste 7 h 30 min.", '
            '"chart": {"kind": "bars", "title": "Sueño", "unit": "min", '
            '"points": [{"label": "L", "value": 430, "date": "2026-10-12"}, '
            '{"label": "M", "value": 470}], "goal": 450}, '
            '"suggestions": ["¿Y la semana pasada?"]}'
        )

        self.assertEqual("Dormiste 7 h 30 min.", reply.answer)
        self.assertEqual("bars", reply.chart["kind"])
        self.assertEqual(430, reply.chart["points"][0]["value"])
        self.assertEqual("2026-10-12", reply.chart["points"][0]["date"])
        self.assertIsNone(reply.chart["points"][1]["date"])
        self.assertEqual(450, reply.chart["goal"])
        self.assertEqual(["¿Y la semana pasada?"], reply.suggestions)

    def test_a_fenced_envelope_is_still_read(self):
        reply = parse_reply(
            '```json\n{"answer": "Hola", "chart": null, "suggestions": []}\n```'
        )

        self.assertEqual("Hola", reply.answer)
        self.assertIsNone(reply.chart)
        self.assertEqual([], reply.suggestions)

    def test_prose_without_an_envelope_stays_prose(self):
        reply = parse_reply("Dormiste 7 h 30 min de media esta semana.")

        self.assertEqual("Dormiste 7 h 30 min de media esta semana.", reply.answer)
        self.assertIsNone(reply.chart)
        self.assertEqual([], reply.suggestions)

    def test_broken_json_never_loses_the_answer(self):
        reply = parse_reply('{"answer": "Dormiste 7 h", "chart": {"kind":')

        self.assertTrue(reply.answer)
        self.assertIsNone(reply.chart)

    def test_an_envelope_without_prose_falls_back_to_the_raw_text(self):
        reply = parse_reply('{"answer": "", "suggestions": ["hola"]}')

        self.assertIn("answer", reply.answer)
        self.assertIsNone(reply.chart)

    def test_empty_input_is_an_empty_answer(self):
        self.assertEqual("", parse_reply("   ").answer)


class NormalizeChartTest(unittest.TestCase):
    def _points(self, count: int) -> list[dict]:
        return [{"label": "L", "value": 100 + index} for index in range(count)]

    def test_a_sane_bar_chart_survives(self):
        chart = normalize_chart(
            {"kind": "bars", "title": " Pasos ", "unit": "pasos", "points": self._points(7)}
        )

        self.assertEqual("Pasos", chart["title"])
        self.assertEqual(7, len(chart["points"]))
        self.assertIsNone(chart["goal"])

    def test_too_many_points_drops_the_chart_instead_of_truncating_it(self):
        self.assertIsNone(
            normalize_chart({"kind": "bars", "points": self._points(MAX_CHART_POINTS + 1)})
        )

    def test_an_unknown_kind_is_not_chartable(self):
        self.assertIsNone(normalize_chart({"kind": "pie", "points": self._points(3)}))

    def test_a_non_numeric_value_drops_the_chart(self):
        self.assertIsNone(
            normalize_chart({"kind": "line", "points": [{"label": "L", "value": "muchos"}] * 3})
        )

    def test_a_boolean_is_not_a_number(self):
        self.assertIsNone(
            normalize_chart({"kind": "line", "points": [{"label": "L", "value": True}] * 3})
        )

    def test_nan_and_infinity_are_rejected(self):
        for bad in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=bad):
                self.assertIsNone(
                    normalize_chart({"kind": "bars", "points": [{"label": "L", "value": bad}] * 3})
                )

    def test_one_lone_point_is_not_a_chart(self):
        self.assertIsNone(normalize_chart({"kind": "bars", "points": self._points(1)}))

    def test_a_non_object_is_not_a_chart(self):
        for bad in (None, "bars", [], 42):
            with self.subTest(candidate=bad):
                self.assertIsNone(normalize_chart(bad))

    def test_long_titles_labels_and_units_are_trimmed_to_fit_the_bubble(self):
        chart = normalize_chart(
            {
                "kind": "line",
                "title": "x" * 200,
                "unit": "y" * 200,
                "points": [{"label": "z" * 50, "value": 1.239}] * 3,
            }
        )

        self.assertLessEqual(len(chart["title"]), 60)
        self.assertLessEqual(len(chart["unit"]), 16)
        self.assertLessEqual(len(chart["points"][0]["label"]), 8)
        self.assertEqual(1.24, chart["points"][0]["value"])

    def test_a_bad_date_becomes_no_date(self):
        chart = normalize_chart(
            {"kind": "bars", "points": [{"label": "L", "value": 5, "date": "ayer"}] * 3}
        )

        self.assertIsNone(chart["points"][0]["date"])


class NormalizeSuggestionsTest(unittest.TestCase):
    def test_they_are_capped_and_trimmed(self):
        suggestions = normalize_suggestions([f" pregunta {index} " for index in range(10)])

        self.assertEqual(MAX_SUGGESTIONS, len(suggestions))
        self.assertEqual("pregunta 0", suggestions[0])

    def test_a_lone_string_becomes_one_suggestion(self):
        self.assertEqual(["¿Y el mes pasado?"], normalize_suggestions("¿Y el mes pasado?"))

    def test_junk_entries_are_dropped(self):
        self.assertEqual(
            ["vale"],
            normalize_suggestions([None, "", "   ", 7, "vale"]),
        )

    def test_a_non_list_is_no_suggestions(self):
        for bad in (None, 7, {"a": 1}):
            with self.subTest(candidate=bad):
                self.assertEqual([], normalize_suggestions(bad))


if __name__ == "__main__":
    unittest.main()
