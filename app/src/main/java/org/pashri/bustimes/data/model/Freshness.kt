package org.pashri.bustimes.data.model

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * How old a reported vehicle position is, and how to present that.
 *
 * A position ten minutes out of date looks exactly like a live one, so a bus
 * drawn from it is read as a promise the data cannot keep. Everything here is
 * a pure function of an age in seconds, which keeps the thresholds testable
 * and lets the map and the panel agree by construction.
 *
 * The thresholds are calibrated against live `/vehicles.json` samples taken
 * on 2026-09-10. Stagecoach East, which is roughly three quarters of the
 * vehicles on screen in Cambridge, reports at a median age of 46 seconds and
 * a 90th percentile of 70 seconds, so [FRESH_LIMIT_SECONDS] sits clear of a
 * healthy feed. Elsewhere feeds are slower — Manchester's median is 143
 * seconds — so these numbers deliberately suit Cambridge and warn more often
 * on a slower operator such as Whippet.
 */
object Freshness {

    /** At or below this age, a position is presented as current. */
    const val FRESH_LIMIT_SECONDS = 120L

    /**
     * At or above this age, the direction-of-travel arrow is withdrawn.
     *
     * A three-minute-old bearing on an urban route is frequently just wrong,
     * the bus having turned since, so the arrow is removed rather than faded:
     * it is a claim to withdraw, not a detail to play down.
     */
    const val ARROW_LIMIT_SECONDS = 180L

    /** The age at which the opacity ramp bottoms out at [STALE_OPACITY_FLOOR]. */
    const val STALE_LIMIT_SECONDS = 600L

    /**
     * The faintest a bus is ever drawn.
     *
     * Never zero, and never near it: a bus that has faded out of sight cannot
     * be found or tapped, which is a worse failure than one whose position is
     * old.
     */
    const val STALE_OPACITY_FLOOR = 0.35f

    /**
     * Parses a feed timestamp to epoch millis.
     *
     * `/vehicles.json` supplies an ISO-8601 instant with an offset, as in
     * `2026-09-10T13:36:25+01:00`, which is a different shape from the bare
     * `HH:mm` times [LatenessCalculator] handles.
     *
     * @param datetime the feed's `datetime`, if any.
     * @return the instant in epoch millis, or null when absent or malformed.
     */
    fun fixedAtMillis(datetime: String?): Long? {
        val text = datetime?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (error: DateTimeParseException) {
            null
        }
    }

    /**
     * Works out how old a position is.
     *
     * [nowMillis] must come from `ClockSkew.now()` rather than the device
     * clock: a phone running a minute slow would otherwise invent a minute of
     * staleness for every bus on the map.
     *
     * A fix timestamped in the future is reported as brand new rather than
     * negative, since that means the clocks disagree and not that the bus has
     * yet to report.
     *
     * @param datetime the feed's `datetime`, if any.
     * @param nowMillis the current time, corrected towards the server's
     *   clock. Zero or less means no clock has been read yet.
     * @return the age in seconds, or null when it cannot be known.
     */
    fun ageSeconds(datetime: String?, nowMillis: Long): Long? {
        if (nowMillis <= 0) return null
        val fixedAt = fixedAtMillis(datetime) ?: return null
        return ((nowMillis - fixedAt) / MILLIS_PER_SECOND).coerceAtLeast(0)
    }

    /**
     * How faintly to draw a bus of a given age.
     *
     * Full opacity up to [FRESH_LIMIT_SECONDS], then a linear ramp down to
     * [STALE_OPACITY_FLOOR] at [STALE_LIMIT_SECONDS], clamped at both ends.
     *
     * @param seconds the age, or null when unknown.
     * @return an opacity between [STALE_OPACITY_FLOOR] and 1. An unknown age
     *   is drawn as current, so a feed that omits its timestamp cannot wash
     *   out the whole map.
     */
    fun opacityForAge(seconds: Long?): Float {
        if (seconds == null || seconds <= FRESH_LIMIT_SECONDS) return 1f
        if (seconds >= STALE_LIMIT_SECONDS) return STALE_OPACITY_FLOOR
        val span = (STALE_LIMIT_SECONDS - FRESH_LIMIT_SECONDS).toFloat()
        val travelled = (seconds - FRESH_LIMIT_SECONDS) / span
        return 1f - (1f - STALE_OPACITY_FLOOR) * travelled
    }

    /**
     * Whether a position is old enough that live figures should stop looking
     * authoritative.
     *
     * @param seconds the age, or null when unknown.
     * @return true at or past [FRESH_LIMIT_SECONDS].
     */
    fun isStale(seconds: Long?): Boolean =
        seconds != null && seconds >= FRESH_LIMIT_SECONDS

    /**
     * Whether the direction-of-travel arrow should be withdrawn.
     *
     * @param seconds the age, or null when unknown.
     * @return true at or past [ARROW_LIMIT_SECONDS].
     */
    fun isArrowStale(seconds: Long?): Boolean =
        seconds != null && seconds >= ARROW_LIMIT_SECONDS

    /**
     * Puts an age into words for the journey panel.
     *
     * Always says something, because silence would leave the user unable to
     * tell a current position from an app that does not report one — which is
     * the confusion this whole mechanism exists to remove.
     *
     * @param seconds the age, or null when unknown.
     * @return a phrase such as `updated 5 min ago`.
     */
    fun describeAge(seconds: Long?): String = when {
        seconds == null -> "last update unknown"
        seconds < SECONDS_PER_MINUTE -> "updated just now"
        else -> "updated ${seconds / SECONDS_PER_MINUTE} min ago"
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
}
