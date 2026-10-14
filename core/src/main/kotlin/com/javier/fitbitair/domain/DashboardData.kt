package com.javier.fitbitair.domain

data class SleepStage(
    val kind: SleepStageKind,
    val minutes: Int,
)

enum class SleepStageKind(val label: String) {
    LIGHT("Ligero"),
    DEEP("Profundo"),
    REM("REM"),
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
    val label: String,
    val steps: Int,
)

data class DailyRestingHeartRate(
    val label: String,
    val beatsPerMinute: Int,
)

data class DashboardData(
    val sleep: SleepSummary,
    val stepsToday: Int,
    val restingHeartRate: Int,
    val previousRestingHeartRate: Int?,
    val stepsTrend: List<DailySteps>,
    val sleepTrend: List<SleepSummary>,
    val heartRateTrend: List<DailyRestingHeartRate>,
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
