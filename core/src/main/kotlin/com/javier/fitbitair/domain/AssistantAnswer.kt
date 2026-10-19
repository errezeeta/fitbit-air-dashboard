package com.javier.fitbitair.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate

enum class AssistantChartKind { BARS, LINE }

data class AssistantChartPoint(
    val label: String,
    val value: Double,
    val date: LocalDate?,
)

/**
 * A small chart the agent chose to attach to its answer. Values come from the
 * metrics summary the app already sent, so the chart restates real numbers.
 */
data class AssistantChart(
    val kind: AssistantChartKind,
    val title: String,
    val unit: String,
    val points: List<AssistantChartPoint>,
    val goal: Double? = null,
) {
    val isBars: Boolean get() = kind == AssistantChartKind.BARS
}

data class AssistantAnswer(
    val text: String,
    val chart: AssistantChart? = null,
    val suggestions: List<String> = emptyList(),
)

object AssistantAnswerParser {
    /** Lenient by design: anything unusable is dropped, never the answer itself. */
    fun parse(json: JsonObject): AssistantAnswer = AssistantAnswer(
        text = json.answerText(),
        chart = chart(json["chart"]),
        suggestions = suggestions(json["suggestions"]),
    )

    private fun JsonObject.answerText(): String =
        (get("answer")?.takeIf { it.isJsonPrimitive }?.asString ?: "").trim()

    private fun chart(element: JsonElement?): AssistantChart? {
        val node = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val kind = when (node.stringOrEmpty("kind")) {
            "bars" -> AssistantChartKind.BARS
            "line" -> AssistantChartKind.LINE
            else -> return null
        }
        val points = (node["points"] as? com.google.gson.JsonArray)
            ?.mapNotNull(::point)
            ?: return null
        if (points.size < 2) return null
        return AssistantChart(
            kind = kind,
            title = node.stringOrEmpty("title"),
            unit = node.stringOrEmpty("unit"),
            points = points,
            goal = node["goal"]?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isString }?.asDouble,
        )
    }

    private fun point(element: JsonElement): AssistantChartPoint? {
        val node = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val value = node["value"]
            ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isString }
            ?.asDouble
            ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return AssistantChartPoint(
            label = node.stringOrEmpty("label"),
            value = value,
            date = node.stringOrEmpty("date").takeIf { it.length == 10 }?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            },
        )
    }

    private fun suggestions(element: JsonElement?): List<String> =
        (element as? com.google.gson.JsonArray)
            ?.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
            }
            ?.filter { it.isNotEmpty() }
            ?.take(4)
            ?: emptyList()

    private fun JsonObject.stringOrEmpty(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
}
