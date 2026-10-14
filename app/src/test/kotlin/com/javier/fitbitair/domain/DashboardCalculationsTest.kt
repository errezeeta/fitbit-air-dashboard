package com.javier.fitbitair.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardCalculationsTest {
    @Test
    fun stepProgressIsCorrectAndClamped() {
        assertEquals(0.5f, stepProgress(4_000, 8_000), 0.0001f)
        assertEquals(1f, stepProgress(12_000, 8_000), 0.0001f)
        assertEquals(0f, stepProgress(-100, 8_000), 0.0001f)
        assertEquals(0f, stepProgress(4_000, 0), 0.0001f)
    }

    @Test
    fun sleepEfficiencyRoundsToNearestWholePercent() {
        assertEquals(88, sleepEfficiencyPercent(asleepMinutes = 428, inBedMinutes = 488))
    }

    @Test
    fun sleepEfficiencyIsUnavailableForInvalidDurations() {
        assertNull(sleepEfficiencyPercent(asleepMinutes = 0, inBedMinutes = 0))
        assertNull(sleepEfficiencyPercent(asleepMinutes = 600, inBedMinutes = 500))
    }

    @Test
    fun trendDeltaUsesCurrentMinusPrevious() {
        assertEquals(4, trendDelta(current = 58, previous = 54))
        assertEquals(-2, trendDelta(current = 56, previous = 58))
        assertNull(trendDelta(current = 58, previous = null))
    }
}
