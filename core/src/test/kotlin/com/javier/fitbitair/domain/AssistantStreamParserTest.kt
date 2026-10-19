package com.javier.fitbitair.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStreamParserTest {
    @Test
    fun aStageEventKeepsItsLabel() {
        val event = AssistantStreamParser.parse("""{"stage":"pensando","label":"Pensando con tus métricas"}""")

        val stage = event as AssistantStreamEvent.Stage
        assertEquals("pensando", stage.id)
        assertEquals("Pensando con tus métricas", stage.label)
    }

    @Test
    fun aStageWithoutALabelFallsBackToItsName() {
        val stage = AssistantStreamParser.parse("""{"stage":"datos"}""") as AssistantStreamEvent.Stage

        assertEquals("datos", stage.label)
    }

    @Test
    fun aDeltaIsHandedOverVerbatimIncludingWhitespace() {
        val delta = AssistantStreamParser.parse("""{"delta":" y 8 420 hoy"}""") as AssistantStreamEvent.Delta

        assertEquals(" y 8 420 hoy", delta.text)
    }

    @Test
    fun aDoneEventCarriesTheAnswerItsChartAndItsChips() {
        val event = AssistantStreamParser.parse(
            """
            {"done":{"answer":"Dormiste 7 h 30 min.","chart":{"kind":"bars","title":"Sueño","unit":"min",
             "points":[{"label":"L","value":430,"date":"2026-10-12"},{"label":"M","value":470,"date":"2026-10-13"}]},
             "suggestions":["¿Y el mes pasado?"]}}
            """.trimIndent(),
        )

        val answer = (event as AssistantStreamEvent.Done).answer
        assertEquals("Dormiste 7 h 30 min.", answer.text)
        assertEquals("bars", answer.chart!!.kind.name.lowercase())
        assertEquals(2, answer.chart!!.points.size)
        assertEquals(listOf("¿Y el mes pasado?"), answer.suggestions)
    }

    @Test
    fun anErrorEventSurfacesItsMessage() {
        val failure = AssistantStreamParser.parse("""{"error":"El asistente tardó demasiado."}""")

        assertEquals("El asistente tardó demasiado.", (failure as AssistantStreamEvent.Failure).message)
    }

    @Test
    fun junkLinesAreIgnoredInsteadOfBreakingTheStream() {
        assertNull(AssistantStreamParser.parse("no soy json"))
        assertNull(AssistantStreamParser.parse("""{"algo":"desconocido"}"""))
        assertNull(AssistantStreamParser.parse(""))
        assertNull(AssistantStreamParser.parse("""{"done":"no soy un objeto"}"""))
    }

    @Test
    fun aDoneEventWithABrokenChartStillDeliversTheText() {
        val event = AssistantStreamParser.parse(
            """{"done":{"answer":"Mira esto.","chart":{"kind":"pie","points":[]}}}""",
        )

        val answer = (event as AssistantStreamEvent.Done).answer
        assertEquals("Mira esto.", answer.text)
        assertNull(answer.chart)
        assertTrue(answer.suggestions.isEmpty())
    }
}
