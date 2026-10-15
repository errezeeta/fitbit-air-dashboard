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

data class DailySteps(
    /** Short axis tick: weekday initial or day of month. */
    val label: String,
    val steps: Int,
    /** Full date, used to name the day in the tap-tooltip. */
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

data class DashboardData(
    val sleep: SleepSummary,
    val stepsToday: Int,
    val restingHeartRate: Int,
    val previousRestingHeartRate: Int?,
    val stepsTrend: List<DailySteps>,
    val sleepTrend: List<SleepSummary>,
    val heartRateTrend: List<DailyRestingHeartRate>,
    val distanceTodayMeters: Int = 0,
    val distanceTrend: List<DailyDistance> = emptyList(),
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
