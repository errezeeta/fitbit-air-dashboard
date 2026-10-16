package com.javier.fitbitair.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendStatsTest {
    private fun series(vararg values: Int) = values.mapIndexed { index, value ->
        SeriesPoint(LocalDate.of(2026, 10, 10).plusDays(index.toLong()), value)
    }

    @Test
    fun emptySeriesIsAllZeroesWithoutInventingABestDay() {
        val stats = TrendStats.of(emptyList())

        assertEquals(0L, stats.total)
        assertEquals(0.0, stats.average, 0.001)
        assertNull(stats.best)
        assertNull(stats.worst)
        assertEquals(0, stats.days)
    }

    @Test
    fun statsFindTotalAverageAndTheBestAndWorstDays() {
        val stats = TrendStats.of(series(1000, 8000, 4000))

        assertEquals(13000L, stats.total)
        assertEquals(13000.0 / 3, stats.average, 0.001)
        assertEquals(8000, stats.best?.value)
        assertEquals(LocalDate.of(2026, 10, 11), stats.best?.date)
        assertEquals(1000, stats.worst?.value)
        assertEquals(3, stats.days)
    }

    @Test
    fun deltaComparesTheLastValueAgainstThePeriodAverage() {
        val stats = TrendStats.of(series(1000, 1000, 4000))

        // average 2000, last 4000 -> +100 %
        assertEquals(100.0, stats.deltaPercentOf(4000)!!, 0.001)
        assertEquals(-50.0, stats.deltaPercentOf(1000)!!, 0.001)
        assertNull(stats.deltaPercentOf(null))
    }

    @Test
    fun goalHelpersCountDaysAndTheLongestRun() {
        val steps = listOf(3000, 9000, 9500, 2000, 8000, 8100, 8200)

        assertEquals(5, TrendStats.daysOverGoal(steps, 8000))
        assertEquals(3, TrendStats.longestStreakOverGoal(steps, 8000))
        assertEquals(0, TrendStats.daysOverGoal(steps, 0))
        assertEquals(0, TrendStats.longestStreakOverGoal(steps, 0))
    }

    @Test
    fun activeDayPercentIgnoresEmptyPeriods() {
        assertEquals(0, TrendStats.activeDayPercent(emptyList()))
        assertEquals(50, TrendStats.activeDayPercent(listOf(0, 0, 100, 500)))
        assertEquals(100, TrendStats.activeDayPercent(listOf(1, 2, 3)))
    }

    @Test
    fun variabilityNeedsAtLeastTwoNightsAndIsRelativeToTheMean() {
        assertNull(TrendStats.variabilityPercent(listOf(400)))

        // 420 ± 0 -> 0 %
        assertEquals(0, TrendStats.variabilityPercent(listOf(420, 420, 420)))

        // 420/480/450: spread is small but real
        val spread = TrendStats.variabilityPercent(listOf(420, 480, 450))!!
        assertEquals(true, spread in 1..15)
    }

    @Test
    fun dashboardSeriesAreDatedSoTheDaySheetCanFindThem() {
        val data = DashboardData(
            sleep = SleepSummary(0, 0, "", "", emptyList()),
            stepsToday = 0,
            restingHeartRate = 0,
            previousRestingHeartRate = null,
            stepsTrend = listOf(
                DailySteps("L", 4200, LocalDate.of(2026, 10, 12)),
                DailySteps("M", 9100, LocalDate.of(2026, 10, 13)),
            ),
            sleepTrend = listOf(
                DailySleep(
                    LocalDate.of(2026, 10, 13),
                    "M",
                    SleepSummary(450, 480, "23:00", "07:00", emptyList()),
                ),
            ),
            heartRateTrend = listOf(DailyRestingHeartRate("M", 57, LocalDate.of(2026, 10, 13))),
            distanceTodayMeters = 4100,
            distanceTrend = listOf(DailyDistance("M", 4100, LocalDate.of(2026, 10, 13))),
        )

        val snapshot = data.snapshotFor(LocalDate.of(2026, 10, 13))

        assertEquals(9100, snapshot.steps)
        assertEquals(4100, snapshot.distanceMeters)
        assertEquals(450, snapshot.sleep?.asleepMinutes)
        assertEquals(57, snapshot.restingHeartRate)
        assertEquals(true, snapshot.hasAnything)

        val empty = data.snapshotFor(LocalDate.of(2026, 10, 1))
        assertEquals(false, empty.hasAnything)
    }
}
