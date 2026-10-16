package com.javier.fitbitair.data

import com.javier.fitbitair.domain.DailyDistance
import com.javier.fitbitair.domain.DailyRestingHeartRate
import com.javier.fitbitair.domain.DailySleep
import com.javier.fitbitair.domain.DailySteps
import com.javier.fitbitair.domain.DashboardData
import com.javier.fitbitair.domain.DashboardState
import com.javier.fitbitair.domain.HealthRepository
import com.javier.fitbitair.domain.SleepStage
import com.javier.fitbitair.domain.SleepStageKind
import com.javier.fitbitair.domain.SleepSummary
import com.javier.fitbitair.domain.TrendRange
import java.time.LocalDate

/** Clearly marked local sample data; replaced by Google Health API at the integration stage. */
class SampleHealthRepository : HealthRepository {
    override suspend fun loadDashboard(range: TrendRange): DashboardState {
        val labels = if (range == TrendRange.WEEK) {
            listOf("L", "M", "X", "J", "V", "S", "D")
        } else {
            listOf("1", "4", "7", "10", "13", "16", "19", "22", "25", "28", "30")
        }
        val steps = listOf(5_120, 7_840, 6_930, 9_240, 7_280, 8_660, 6_842)
        val distances = listOf(3_600, 5_400, 4_900, 6_800, 5_100, 6_200, 4_800)
        val sleepMinutes = listOf(401, 438, 392, 455, 424, 449, 462)
        val restingHeartRates = listOf(61, 60, 59, 60, 58, 59, 58)
        val index = labels.indices
        val selected = if (range == TrendRange.WEEK) index.toList() else {
            listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 5, 6)
        }

        val sleep = SleepSummary(
            asleepMinutes = 462,
            inBedMinutes = 488,
            bedtime = "23:08",
            wakeTime = "07:16",
            stages = listOf(
                SleepStage(SleepStageKind.LIGHT, 230),
                SleepStage(SleepStageKind.DEEP, 96),
                SleepStage(SleepStageKind.REM, 122),
                SleepStage(SleepStageKind.AWAKE, 14),
            ),
        )
        val data = DashboardData(
            sleep = sleep,
            stepsToday = 6_842,
            restingHeartRate = 58,
            previousRestingHeartRate = 60,
            stepsTrend = labels.mapIndexed { i, label -> DailySteps(label, steps[selected[i]]) },
            sleepTrend = labels.mapIndexed { i, label ->
                DailySleep(
                    date = LocalDate.now().minusDays((labels.size - 1 - i).toLong()),
                    label = label,
                    summary = sleep.copy(asleepMinutes = sleepMinutes[selected[i]]),
                )
            },
            heartRateTrend = labels.mapIndexed { i, label ->
                DailyRestingHeartRate(label, restingHeartRates[selected[i]])
            },
            distanceTrend = labels.mapIndexed { i, label ->
                DailyDistance(label, distances[selected[i]], LocalDate.now().minusDays((labels.size - 1 - i).toLong()))
            },
            distanceTodayMeters = distances[selected.last()],
        )
        return DashboardState.Ready(data)
    }
}
