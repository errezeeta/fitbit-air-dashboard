package com.javier.fitbitair.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DayOfWeek
import java.time.LocalDate

/** Decodes the backend response without persisting any health measurements. */
object BackendDashboardMapper {
    fun parse(json: String, range: TrendRange): DashboardState {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return DashboardState.Error(SAFE_ERROR)
            val response = root.asJsonObject
            when (response.stringValue("status")) {
                "empty" -> DashboardState.Empty
                "ready" -> DashboardState.Ready(mapReadyDashboard(response, range))
                else -> DashboardState.Error(SAFE_ERROR)
            }
        } catch (_: Exception) {
            DashboardState.Error(SAFE_ERROR)
        }
    }

    private fun mapReadyDashboard(root: JsonObject, range: TrendRange): DashboardData {
        val stepsElements = root.arrayValue("stepsTrend")
        val sleepElements = root.arrayValue("sleepTrend")
        val heartRateElements = root.arrayValue("heartRateTrend")
        val distanceElements = root.arrayValue("distanceTrend")
        return DashboardData(
            sleep = root.objectValue("sleep")?.let(::mapSleep) ?: EMPTY_SLEEP,
            stepsToday = root.intValue("stepsToday"),
            restingHeartRate = root.intValue("restingHeartRate"),
            previousRestingHeartRate = root.nullableIntValue("previousRestingHeartRate"),
            stepsTrend = stepsElements.mapIndexedNotNull { index, element ->
                val point = element.objectOrNull() ?: return@mapIndexedNotNull null
                val date = point.stringValue("date").toLocalDateOrNull() ?: return@mapIndexedNotNull null
                DailySteps(
                    label = chartAxisLabel(date, range, index, stepsElements.size),
                    steps = point.intValue("steps"),
                    date = date,
                )
            },
            sleepTrend = sleepElements.mapNotNull { element ->
                element.objectOrNull()?.let(::mapSleep)
            },
            heartRateTrend = heartRateElements.mapIndexedNotNull { index, element ->
                val point = element.objectOrNull() ?: return@mapIndexedNotNull null
                val date = point.stringValue("date").toLocalDateOrNull() ?: return@mapIndexedNotNull null
                DailyRestingHeartRate(
                    label = chartAxisLabel(date, range, index, heartRateElements.size),
                    beatsPerMinute = point.intValue("beatsPerMinute"),
                    date = date,
                )
            },
            distanceTodayMeters = root.intValue("distanceTodayMeters"),
            distanceTrend = distanceElements.mapIndexedNotNull { index, element ->
                val point = element.objectOrNull() ?: return@mapIndexedNotNull null
                val date = point.stringValue("date").toLocalDateOrNull() ?: return@mapIndexedNotNull null
                DailyDistance(
                    label = chartAxisLabel(date, range, index, distanceElements.size),
                    meters = point.intValue("meters"),
                    date = date,
                )
            },
        )
    }

    private fun mapSleep(json: JsonObject): SleepSummary {
        val stages = json.arrayValue("stages").mapNotNull { element ->
            val stage = element.objectOrNull() ?: return@mapNotNull null
            val kind = stage.stringValue("kind").toSleepStageKind() ?: return@mapNotNull null
            val minutes = stage.intValue("minutes").coerceAtLeast(0)
            if (minutes == 0) null else SleepStage(kind, minutes)
        }
        return SleepSummary(
            asleepMinutes = json.intValue("asleepMinutes").coerceAtLeast(0),
            inBedMinutes = json.intValue("inBedMinutes").coerceAtLeast(0),
            bedtime = json.stringValue("bedtime"),
            wakeTime = json.stringValue("wakeTime"),
            stages = stages,
        )
    }

    private fun String.toSleepStageKind(): SleepStageKind? = when (uppercase()) {
        "LIGHT", "ASLEEP" -> SleepStageKind.LIGHT
        "DEEP" -> SleepStageKind.DEEP
        "REM" -> SleepStageKind.REM
        "RESTLESS" -> SleepStageKind.RESTLESS
        "AWAKE" -> SleepStageKind.AWAKE
        else -> null
    }

    private fun JsonObject.stringValue(name: String): String =
        if (has(name) && !get(name).isJsonNull) get(name).asString else ""

    private fun JsonObject.intValue(name: String): Int =
        if (has(name) && !get(name).isJsonNull) get(name).asInt else 0

    private fun JsonObject.nullableIntValue(name: String): Int? =
        if (!has(name) || get(name).isJsonNull) null else get(name).asInt

    private fun JsonObject.arrayValue(name: String): List<JsonElement> =
        if (has(name) && get(name).isJsonArray) getAsJsonArray(name).toList() else emptyList()

    private fun JsonObject.objectValue(name: String): JsonObject? =
        if (has(name) && get(name).isJsonObject) getAsJsonObject(name) else null

    private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()

    private val EMPTY_SLEEP = SleepSummary(
        asleepMinutes = 0,
        inBedMinutes = 0,
        bedtime = "",
        wakeTime = "",
        stages = emptyList(),
    )

    private const val SAFE_ERROR = "La respuesta del servidor no es válida."
}

/**
 * One dense, readable axis tick per bar. Months alternate short numeric ticks
 * with wide blanks so the numbers never crowd or wrap, which is what broke the
 * previous per-bar layout.
 */
fun chartAxisLabel(date: LocalDate, range: TrendRange, index: Int, count: Int): String {
    if (range == TrendRange.WEEK) {
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "L"
            DayOfWeek.TUESDAY -> "M"
            DayOfWeek.WEDNESDAY -> "X"
            DayOfWeek.THURSDAY -> "J"
            DayOfWeek.FRIDAY -> "V"
            DayOfWeek.SATURDAY -> "S"
            DayOfWeek.SUNDAY -> "D"
        }
    }
    val step = when {
        count <= 10 -> 2
        count <= 16 -> 3
        else -> 5
    }
    val isFirst = index == 0
    val isLast = index == count - 1
    return if (isFirst || isLast || index % step == 0) date.dayOfMonth.toString() else ""
}

/** Long, human form for the chart tooltip: "Jue 16 oct". */
fun chartTooltipDate(date: LocalDate): String = date
    .format(
        java.time.format.DateTimeFormatter.ofPattern(
            "EEE d MMM",
            java.util.Locale.forLanguageTag("es-ES"),
        ),
    )
    .replaceFirstChar { it.uppercase(java.util.Locale.forLanguageTag("es-ES")) }
