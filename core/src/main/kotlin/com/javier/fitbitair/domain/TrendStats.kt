package com.javier.fitbitair.domain

import java.time.LocalDate

/** One point of a series, tied to the day it was recorded on. */
data class SeriesPoint(val date: LocalDate?, val value: Int)

data class SeriesStats(
    val total: Long,
    val average: Double,
    val best: SeriesPoint?,
    val worst: SeriesPoint?,
    val days: Int,
) {
    /** The last recorded value against the period average, in percent. */
    fun deltaPercentOf(lastValue: Int?): Double? {
        if (lastValue == null || average <= 0.0) return null
        return (lastValue - average) / average * 100.0
    }
}

/**
 * Period arithmetic for the detail screens. Pure functions so the numbers on
 * screen can be tested without a device.
 */
object TrendStats {
    fun of(values: List<SeriesPoint>): SeriesStats {
        if (values.isEmpty()) {
            return SeriesStats(total = 0, average = 0.0, best = null, worst = null, days = 0)
        }
        return SeriesStats(
            total = values.sumOf { it.value.toLong() },
            average = values.sumOf { it.value }.toDouble() / values.size,
            best = values.maxByOrNull { it.value },
            worst = values.minByOrNull { it.value },
            days = values.size,
        )
    }

    /** Days that reached the step goal. */
    fun daysOverGoal(steps: List<Int>, goal: Int): Int =
        if (goal <= 0) 0 else steps.count { it >= goal }

    /** Longest run of consecutive days that reached the goal. */
    fun longestStreakOverGoal(steps: List<Int>, goal: Int): Int {
        if (goal <= 0) return 0
        var longest = 0
        var current = 0
        steps.forEach { value ->
            if (value >= goal) {
                current += 1
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    /** Share of recorded days with any steps at all, as a percentage. */
    fun activeDayPercent(steps: List<Int>): Int {
        if (steps.isEmpty()) return 0
        val active = steps.count { it > 0 }
        return Math.round(active * 100f / steps.size)
    }

    /** How steady the sleep window is: spread of asleep minutes around the mean. */
    fun variabilityPercent(values: List<Int>): Int? {
        if (values.size < 2) return null
        val mean = values.average()
        if (mean <= 0.0) return null
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return Math.round((Math.sqrt(variance) / mean * 100).toFloat())
    }
}

/** Steps, distance, sleep and heart rate as one dated series each. */
fun DashboardData.stepPoints(): List<SeriesPoint> = stepsTrend.map { SeriesPoint(it.date, it.steps) }
fun DashboardData.distancePoints(): List<SeriesPoint> = distanceTrend.map { SeriesPoint(it.date, it.meters) }
fun DashboardData.sleepPoints(): List<SeriesPoint> =
    sleepTrend.map { SeriesPoint(it.date, it.summary.asleepMinutes) }

fun DashboardData.heartPoints(): List<SeriesPoint> =
    heartRateTrend.map { SeriesPoint(it.date, it.beatsPerMinute) }
