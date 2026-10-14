package com.javier.fitbitair.domain

import kotlin.math.roundToInt

/** Fraction of the daily target completed, clamped to the visible progress range. */
fun stepProgress(steps: Int, goal: Int): Float {
    if (goal <= 0) return 0f
    return (steps.coerceAtLeast(0).toFloat() / goal).coerceIn(0f, 1f)
}

/**
 * App-calculated sleep efficiency, distinct from a Fitbit-branded score.
 * Returns null when the source durations cannot produce a valid ratio.
 */
fun sleepEfficiencyPercent(asleepMinutes: Int, inBedMinutes: Int): Int? {
    if (asleepMinutes <= 0 || inBedMinutes <= 0 || asleepMinutes > inBedMinutes) return null
    return (asleepMinutes.toDouble() / inBedMinutes * 100.0).roundToInt()
}

/** Returns current minus previous, or null when no previous value is available. */
fun trendDelta(current: Int, previous: Int?): Int? = previous?.let { current - it }
