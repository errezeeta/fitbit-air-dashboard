package com.javier.fitbitair.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One event of the assistant's streamed answer. */
sealed interface AssistantStreamEvent {
    /** What the agent is doing right now, for the progress line. */
    data class Stage(val id: String, val label: String) : AssistantStreamEvent

    /** A piece of the answer, ready to append to the bubble. */
    data class Delta(val text: String) : AssistantStreamEvent

    /** The turn is over: the full answer with whatever it attached. */
    data class Done(val answer: AssistantAnswer) : AssistantStreamEvent

    data class Failure(val message: String) : AssistantStreamEvent
}

object AssistantStreamParser {
    /** Null for anything unusable, so a stray line never breaks the stream. */
    fun parse(dataLine: String): AssistantStreamEvent? {
        val node = runCatching { JsonParser.parseString(dataLine).asJsonObject }.getOrNull() ?: return null
        node.text("delta")?.let { delta -> return AssistantStreamEvent.Delta(delta) }
        node.text("stage")?.let { stage ->
            return AssistantStreamEvent.Stage(stage, node.text("label") ?: stage)
        }
        node.text("error")?.let { message -> return AssistantStreamEvent.Failure(message) }
        node["done"]?.takeIf { it.isJsonObject }?.asJsonObject?.let { payload ->
            return AssistantStreamEvent.Done(AssistantAnswerParser.parse(payload))
        }
        return null
    }

    private fun JsonObject.text(name: String): String? =
        (get(name) as? JsonElement)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
