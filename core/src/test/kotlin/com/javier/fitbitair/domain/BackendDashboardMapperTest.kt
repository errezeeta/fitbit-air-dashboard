package com.javier.fitbitair.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendDashboardMapperTest {
    private val readyJson = """
        {
          "status": "ready",
          "sleep": {
            "asleepMinutes": 450,
            "inBedMinutes": 480,
            "bedtime": "23:00",
            "wakeTime": "07:00",
            "stages": [
              {"kind": "LIGHT", "minutes": 200},
              {"kind": "DEEP", "minutes": 100},
              {"kind": "REM", "minutes": 140},
              {"kind": "RESTLESS", "minutes": 20},
              {"kind": "AWAKE", "minutes": 20}
            ]
          },
          "stepsToday": 6100,
          "restingHeartRate": 58,
          "previousRestingHeartRate": 59,
          "stepsTrend": [
            {"date": "2026-10-12", "steps": 1100},
            {"date": "2026-10-13", "steps": 2200},
            {"date": "2026-10-14", "steps": 3300},
            {"date": "2026-10-15", "steps": 4400},
            {"date": "2026-10-16", "steps": 5500},
            {"date": "2026-10-17", "steps": 6600},
            {"date": "2026-10-18", "steps": 7700}
          ],
          "sleepTrend": [
            {"date": "2026-10-12", "asleepMinutes": 410, "inBedMinutes": 460, "bedtime": "23:10", "wakeTime": "06:50", "stages": []},
            {"date": "2026-10-13", "asleepMinutes": 420, "inBedMinutes": 470, "bedtime": "23:05", "wakeTime": "06:55", "stages": []},
            {"date": "2026-10-14", "asleepMinutes": 450, "inBedMinutes": 480, "bedtime": "23:00", "wakeTime": "07:00", "stages": []},
            {"date": "2026-10-15", "asleepMinutes": 430, "inBedMinutes": 470, "bedtime": "23:15", "wakeTime": "07:05", "stages": []},
            {"date": "2026-10-16", "asleepMinutes": 440, "inBedMinutes": 480, "bedtime": "23:00", "wakeTime": "07:00", "stages": []},
            {"date": "2026-10-17", "asleepMinutes": 460, "inBedMinutes": 500, "bedtime": "22:55", "wakeTime": "07:15", "stages": []},
            {"date": "2026-10-18", "asleepMinutes": 450, "inBedMinutes": 480, "bedtime": "23:00", "wakeTime": "07:00", "stages": []}
          ],
          "heartRateTrend": [
            {"date": "2026-10-12", "beatsPerMinute": 61},
            {"date": "2026-10-13", "beatsPerMinute": 60},
            {"date": "2026-10-14", "beatsPerMinute": 59},
            {"date": "2026-10-15", "beatsPerMinute": 60},
            {"date": "2026-10-16", "beatsPerMinute": 58},
            {"date": "2026-10-17", "beatsPerMinute": 59},
            {"date": "2026-10-18", "beatsPerMinute": 58}
          ],
          "distanceTodayMeters": 4123,
          "distanceTrend": [
            {"date": "2026-10-14", "meters": 4123}
          ]
        }
    """.trimIndent()

    @Test
    fun readyResponseMapsMetricsStagesCalendarLabelsAndDistance() {
        val state = BackendDashboardMapper.parse(readyJson, TrendRange.WEEK)

        assertTrue(state is DashboardState.Ready)
        val data = (state as DashboardState.Ready).data
        assertEquals(6100, data.stepsToday)
        assertEquals(58, data.restingHeartRate)
        assertEquals(59, data.previousRestingHeartRate)
        assertEquals(450, data.sleep.asleepMinutes)
        assertEquals(20, data.sleep.stages.first { it.kind == SleepStageKind.RESTLESS }.minutes)
        assertEquals(4123, data.distanceTodayMeters)
        assertEquals(
            listOf("L", "M", "X", "J", "V", "S", "D"),
            data.stepsTrend.map { it.label },
        )
        assertEquals(
            listOf("L", "M", "X", "J", "V", "S", "D"),
            data.heartRateTrend.map { it.label },
        )
        assertEquals(7, data.sleepTrend.size)
        assertEquals(410, data.sleepTrend.first().asleepMinutes)
        assertEquals(1, data.distanceTrend.size)
        assertEquals(4123, data.distanceTrend.single().meters)
    }

    @Test
    fun emptyResponseProducesEmptyDashboardState() {
        assertEquals(DashboardState.Empty, BackendDashboardMapper.parse("""{"status":"empty"}""", TrendRange.WEEK))
    }

    @Test
    fun invalidJsonProducesSafeErrorState() {
        assertTrue(BackendDashboardMapper.parse("not-json", TrendRange.WEEK) is DashboardState.Error)
    }

    @Test
    fun weekLabelsUseSpanishCalendarDay() {
        assertEquals("X", chartAxisLabel(LocalDate.parse("2026-10-14"), TrendRange.WEEK, 2, 7))
    }

    @Test
    fun monthLabelsSkipEnoughBarsToStayReadable() {
        val dates = (0 until 30).map { LocalDate.now().minusDays((29 - it).toLong()) }
        val labels = dates.mapIndexed { index, date ->
            chartAxisLabel(date, TrendRange.MONTH, index, 30)
        }
        val visible = labels.count { it.isNotEmpty() }
        assertTrue("30-day labels must crowd far less than one per bar", visible <= 10)
        assertTrue(labels.first().isNotEmpty())
        assertTrue(labels.last().isNotEmpty())
    }
}
