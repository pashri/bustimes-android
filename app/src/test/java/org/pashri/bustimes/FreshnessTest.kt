package org.pashri.bustimes

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.Freshness

class FreshnessTest {

    private val fixedNow = OffsetDateTime.parse("2026-09-10T13:40:00+01:00")
        .toInstant().toEpochMilli()

    private fun agedBy(seconds: Long): Long? = Freshness.ageSeconds(
        datetime = OffsetDateTime.parse("2026-09-10T13:40:00+01:00")
            .minusSeconds(seconds).toString(),
        nowMillis = fixedNow,
    )

    @Test
    fun `an offset timestamp is parsed to the right instant`() {
        // The same wall-clock reading in two zones is two different instants,
        // and the feed's offset is the only thing that says which.
        val british = Freshness.fixedAtMillis("2026-09-10T13:36:25+01:00")
        val utc = Freshness.fixedAtMillis("2026-09-10T13:36:25Z")

        assertEquals(3_600_000L, utc!! - british!!)
    }

    @Test
    fun `age is measured in whole seconds`() {
        assertEquals(90L, agedBy(90))
    }

    @Test
    fun `a position from the future is treated as brand new`() {
        // Means the clocks disagree, not that the bus has yet to report.
        assertEquals(0L, agedBy(-30))
    }

    @Test
    fun `an unread clock means an unknown age rather than an ancient one`() {
        // The first frame has no clock yet. Measuring against zero would put
        // every bus 56 years out of date and wash out the whole map.
        assertNull(Freshness.ageSeconds("2026-09-10T13:36:25+01:00", nowMillis = 0L))
    }

    @Test
    fun `a missing or malformed timestamp has no age`() {
        assertNull(Freshness.ageSeconds(null, fixedNow))
        assertNull(Freshness.ageSeconds("", fixedNow))
        assertNull(Freshness.ageSeconds("yesterday afternoon", fixedNow))
        assertNull(Freshness.ageSeconds("2026-09-10T13:36:25", fixedNow))
    }

    @Test
    fun `the agreed opacity ramp, minute by minute`() {
        // The table this feature was designed around: a first ramp from full
        // opacity at 60s down to the midpoint at 120s, then a second ramp down
        // to the floor at 600s, so the fade starts where the label already
        // stops saying "just now" instead of holding full opacity to 120s.
        val byMinute = (0..15).map { minute -> Freshness.opacityForAge(minute * 60L) }

        assertEquals(1.0000f, byMinute[0], 0.005f)
        assertEquals(1.0000f, byMinute[1], 0.005f)
        assertEquals(0.7000f, byMinute[2], 0.005f)
        assertEquals(0.6562f, byMinute[3], 0.005f)
        assertEquals(0.6125f, byMinute[4], 0.005f)
        assertEquals(0.5687f, byMinute[5], 0.005f)
        assertEquals(0.5250f, byMinute[6], 0.005f)
        assertEquals(0.4812f, byMinute[7], 0.005f)
        assertEquals(0.4375f, byMinute[8], 0.005f)
        assertEquals(0.3937f, byMinute[9], 0.005f)
        assertEquals(0.3500f, byMinute[10], 0.005f)
        assertEquals(0.3500f, byMinute[15], 0.005f)
    }

    @Test
    fun `opacity never falls below the floor however old the fix is`() {
        // A bus faded to nothing cannot be found or tapped, which is worse
        // than one whose position is old.
        assertEquals(Freshness.STALE_OPACITY_FLOOR, Freshness.opacityForAge(86_400L), 0.0001f)
    }

    @Test
    fun `an unknown age is drawn as current`() {
        // A feed that omits its timestamp must not wash out the map.
        assertEquals(1f, Freshness.opacityForAge(null), 0.0001f)
    }

    @Test
    fun `the ramp is continuous at both ends`() {
        assertEquals(1f, Freshness.opacityForAge(Freshness.RAMP_START_SECONDS), 0.0001f)
        assertEquals(
            Freshness.RAMP_MID_OPACITY,
            Freshness.opacityForAge(Freshness.FRESH_LIMIT_SECONDS),
            0.0001f,
        )
        assertEquals(
            Freshness.STALE_OPACITY_FLOOR,
            Freshness.opacityForAge(Freshness.STALE_LIMIT_SECONDS),
            0.0001f,
        )
    }

    @Test
    fun `the arrow survives a healthy Cambridge fix and not a Whippet one`() {
        // Stagecoach East's median age is 46s and Whippet's is 335s, so this
        // is the real-world consequence of where the threshold sits.
        assertFalse(Freshness.isArrowStale(agedBy(46)))
        assertTrue(Freshness.isArrowStale(agedBy(335)))
    }

    @Test
    fun `the arrow is kept when the age cannot be known`() {
        assertFalse(Freshness.isArrowStale(null))
    }

    @Test
    fun `staleness and the arrow have their own thresholds`() {
        // 150s is past the fade line but not the arrow line: the bus dims and
        // its lateness goes neutral while it still shows a direction.
        assertTrue(Freshness.isStale(agedBy(150)))
        assertFalse(Freshness.isArrowStale(agedBy(150)))
    }

    @Test
    fun `a fresh position is not stale`() {
        assertFalse(Freshness.isStale(agedBy(119)))
        assertTrue(Freshness.isStale(agedBy(120)))
    }

    @Test
    fun `ages are put into words`() {
        assertEquals("updated just now", Freshness.describeAge(0))
        assertEquals("updated just now", Freshness.describeAge(59))
        assertEquals("updated 1 min ago", Freshness.describeAge(60))
        assertEquals("updated 5 min ago", Freshness.describeAge(335))
        assertEquals("last update unknown", Freshness.describeAge(null))
    }
}
