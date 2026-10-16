package com.javier.fitbitair.domain

import java.time.LocalDate

data class SleepStage(
    val kind: SleepStageKind,
    val minutes: Int,
)

enum class SleepStageKind(val label: String) {
    LIGHT("Ligero"),
    DEEP("Profundo"),
    REM("REM"),
    RESTLESS("Inquieto"),
    AWAKE("Despierto"),
}

data class SleepSummary(
    val asleepMinutes: Int,
    val inBedMinutes: Int,
    val bedtime: String,
    val wakeTime: String,
    val stages: List<SleepStage>,
)

/** One night, tied to the calendar day it belongs to (the day it ended). */
data class DailySleep(
    val date: LocalDate?,
    val label: String,
    val summary: SleepSummary,
)

data class DailySteps(
    /** Short axis tick: weekday initial or day of month. */
    val label: String,
    val steps: Int,
    /** Full date, used to name the day in the tooltip and the day sheet. */
    val date: LocalDate? = null,
)

data class DailyRestingHeartRate(
    val label: String,
    val beatsPerMinute: Int,
    val date: LocalDate? = null,
)

data class DailyDistance(
    val label: String,
    val meters: Int,
    val date: LocalDate? = null,
)

/** Which metric a detail screen is showing. */
enum class MetricKind(val title: String, val kicker: String) {
    STEPS("Pasos", "MOVIMIENTO"),
    DISTANCE("Distancia", "MOVIMIENTO"),
    SLEEP("Sueño", "DESCANSO"),
    HEART("Pulso en reposo", "SALUD CARDIOVASCULAR"),
}

data class DashboardData(
    val sleep: SleepSummary,
    val stepsToday: Int,
    val restingHeartRate: Int,
    val previousRestingHeartRate: Int?,
    val stepsTrend: List<DailySteps>,
    val sleepTrend: List<DailySleep>,
    val heartRateTrend: List<DailyRestingHeartRate>,
    val distanceTodayMeters: Int = 0,
    val distanceTrend: List<DailyDistance> = emptyList(),
)

/** Everything the day sheet needs for one calendar day. */
data class DaySnapshot(
    val date: LocalDate,
    val steps: Int?,
    val distanceMeters: Int?,
    val sleep: SleepSummary?,
    val restingHeartRate: Int?,
) {
    val hasAnything: Boolean
        get() = steps != null || distanceMeters != null || sleep != null || restingHeartRate != null
}

fun DashboardData.snapshotFor(date: LocalDate): DaySnapshot = DaySnapshot(
    date = date,
    steps = stepsTrend.firstOrNull { it.date == date }?.steps,
    distanceMeters = distanceTrend.firstOrNull { it.date == date }?.meters,
    sleep = sleepTrend.firstOrNull { it.date == date }?.summary,
    restingHeartRate = heartRateTrend.firstOrNull { it.date == date }?.beatsPerMinute,
)

enum class TrendRange(val label: String, val days: Int) {
    WEEK("7 días", 7),
    MONTH("30 días", 30),
}

sealed interface DashboardState {
    data object Loading : DashboardState
    data object Empty : DashboardState
    data class Error(val message: String) : DashboardState
    data class Ready(val data: DashboardData) : DashboardState
}
