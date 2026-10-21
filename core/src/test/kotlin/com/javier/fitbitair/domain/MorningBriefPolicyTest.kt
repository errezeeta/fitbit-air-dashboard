package com.javier.fitbitair.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefPolicyTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val wokeAt = "2026-10-21T07:00:00+02:00"

    @Test
    fun staysQuietBeforeTheWakeDelay() {
        assertFalse(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T05:15:00Z"), // 07:15 local
                lastNotifiedDay = null,
                zone = madrid,
            ),
        )
    }

    @Test
    fun firesInsideTheWindow() {
        assertTrue(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T05:30:00Z"), // 07:30 local
                lastNotifiedDay = null,
                zone = madrid,
            ),
        )
    }

    @Test
    fun aDozeDelayedWorkerStillNotifies() {
        assertTrue(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T06:05:00Z"), // 08:05 local, 15 min late
                lastNotifiedDay = null,
                zone = madrid,
            ),
        )
    }

    @Test
    fun neverTwiceOnTheSameDay() {
        assertFalse(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T05:30:00Z"),
                lastNotifiedDay = LocalDate.parse("2026-10-21").toString(),
                zone = madrid,
            ),
        )
    }

    @Test
    fun aBriefFromTomorrowIsStillAllowed() {
        // The stored day comes from the device clock, so a stale value must not
        // silence today's brief.
        assertTrue(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T05:30:00Z"),
                lastNotifiedDay = "2026-10-20",
                zone = madrid,
            ),
        )
    }

    @Test
    fun goesQuietOnceTheMorningIsOver() {
        assertFalse(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = wokeAt,
                now = Instant.parse("2026-10-21T09:30:00Z"), // 11:30 local
                lastNotifiedDay = null,
                zone = madrid,
            ),
        )
    }

    @Test
    fun anUnparsableWakeTimeNeverNotifies() {
        assertFalse(
            MorningBriefPolicy.shouldNotify(
                endTimeIso = "",
                now = Instant.parse("2026-10-21T05:30:00Z"),
                lastNotifiedDay = null,
                zone = madrid,
            ),
        )
    }
}
