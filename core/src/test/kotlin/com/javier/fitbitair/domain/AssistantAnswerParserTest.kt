package com.javier.fitbitair.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAnswerParserTest {
    private fun parse(body: String) = AssistantAnswerParser.parse(
        JsonParser.parseString(body).asJsonObject,
    )

    @Test
    fun aProseOnlyAnswerHasNoChartAndNoChips() {
        val answer = parse("""{"answer":"Dormiste 7 h 30 min.","chart":null,"suggestions":[]}""")

        assertEquals("Dormiste 7 h 30 min.", answer.text)
        assertNull(answer.chart)
        assertTrue(answer.suggestions.isEmpty())
    }

    @Test
    fun aBarChartKeepsItsPointsUnitGoalAndDates() {
        val answer = parse(
            """
            {"answer":"Vas bien.",
             "chart":{"kind":"bars","title":"Pasos","unit":"pasos",
                      "points":[{"label":"L","value":8420,"date":"2026-10-12"},
                                {"label":"M","value":9100,"date":"2026-10-13"}],
                      "goal":8000},
             "suggestions":["¿Y el mes pasado?"]}
            """.trimIndent(),
        )

        val chart = answer.chart!!
        assertTrue(chart.isBars)
        assertEquals("Pasos", chart.title)
        assertEquals(2, chart.points.size)
        assertEquals(8420.0, chart.points.first().value, 0.001)
        assertEquals(java.time.LocalDate.of(2026, 10, 12), chart.points.first().date)
        assertEquals(8000.0, chart.goal!!, 0.001)
        assertEquals(listOf("¿Y el mes pasado?"), answer.suggestions)
    }

    @Test
    fun aLineChartIsRecognised() {
        val answer = parse(
            """{"answer":"ok","chart":{"kind":"line","points":[{"label":"L","value":1},{"label":"M","value":2}]}}""",
        )

        assertEquals(false, answer.chart!!.isBars)
    }

    @Test
    fun anUnknownKindDropsTheChartButKeepsTheText() {
        val answer = parse(
            """{"answer":"Mira esto.","chart":{"kind":"pie","points":[{"label":"L","value":1},{"label":"M","value":2}]}}""",
        )

        assertEquals("Mira esto.", answer.text)
        assertNull(answer.chart)
    }

    @Test
    fun aSinglePointOrNonNumericValuesDropTheChart() {
        assertNull(parse("""{"answer":"a","chart":{"kind":"bars","points":[{"label":"L","value":1}]}}""").chart)
        assertNull(
            parse("""{"answer":"a","chart":{"kind":"bars","points":[{"label":"L","value":"muchos"},{"label":"M","value":"pocos"}]}}""").chart,
        )
    }

    @Test
    fun valuesSentAsStringsAreRejectedSoNoBubbleShowsInventedNumbers() {
        val answer = parse(
            """{"answer":"a","chart":{"kind":"bars","points":[{"label":"L","value":"8420"},{"label":"M","value":9100}]}}""",
        )

        assertNull(answer.chart)
    }

    @Test
    fun aMissingAnswerFieldFallsBackToEmptyText() {
        assertEquals("", parse("""{"chart":null}""").text)
    }

    @Test
    fun chipsAreCappedAndBlankOnesDropped() {
        val answer = parse(
            """{"answer":"a","suggestions":["uno","","dos","tres","cuatro","cinco"]}""",
        )

        assertEquals(4, answer.suggestions.size)
        assertEquals(listOf("uno", "dos", "tres", "cuatro"), answer.suggestions)
    }

    @Test
    fun anInvalidDateBecomesNoDateInsteadOfCrashing() {
        val answer = parse(
            """{"answer":"a","chart":{"kind":"bars","points":[{"label":"L","value":5,"date":"ayer"},{"label":"M","value":6}]}}""",
        )

        assertNull(answer.chart!!.points.first().date)
    }
}
