package com.javier.fitbitair.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * When the morning brief is allowed to fire.
 *
 * Pure on purpose: the notification window is the only part of the background
 * path that can be wrong without anyone noticing, so it is decided here, where
 * it can be tested without a device.
 */
object MorningBriefPolicy {
    /** Give the user time to actually be up before saying anything. */
    val WAKE_DELAY: Duration = Duration.ofMinutes(20)

    /** A phone that was off all morning must not get a "good morning" at six. */
    val STALE_AFTER: Duration = Duration.ofHours(4)

    fun wakeInstant(endTimeIso: String): Instant? =
        runCatching { OffsetDateTime.parse(endTimeIso).toInstant() }.getOrNull()

    /**
     * @param lastNotifiedDay ISO date of the last brief already shown, if any.
     */
    fun shouldNotify(
        endTimeIso: String,
        now: Instant,
        lastNotifiedDay: String?,
        zone: ZoneId,
    ): Boolean {
        val wake = wakeInstant(endTimeIso) ?: return false
        val today = LocalDate.ofInstant(now, zone).toString()
        if (lastNotifiedDay == today) return false
        if (now.isBefore(wake.plus(WAKE_DELAY))) return false
        if (now.isAfter(wake.plus(STALE_AFTER))) return false
        return true
    }
}
