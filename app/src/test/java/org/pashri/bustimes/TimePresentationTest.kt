package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.ui.selection.presentTimes

class TimePresentationTest {

    @Test
    fun `a late bus shows both times with the schedule struck through`() {
        val shown = presentTimes(aimed = "09:38", live = "10:13")

        assertEquals("09:38", shown.aimed)
        assertEquals("10:13", shown.live)
        assertTrue(shown.struckThrough)
    }

    @Test
    fun `an on-time bus shows one time and no strikethrough`() {
        val shown = presentTimes(aimed = "11:35", live = "11:35")

        // Striking through 11:35 to show 11:35 underneath it says nothing.
        assertEquals("11:35", shown.aimed)
        assertNull(shown.live)
        assertFalse(shown.struckThrough)
        assertTrue(shown.isSingleTime)
    }

    @Test
    fun `sub-minute lateness that displays identically is collapsed`() {
        // Times are published to the minute, so these render the same.
        val shown = presentTimes(aimed = "11:35:00", live = "11:35:40")

        assertNull(shown.live)
        assertFalse(shown.struckThrough)
    }

    @Test
    fun `an untracked departure shows its schedule alone`() {
        val shown = presentTimes(aimed = "10:07", live = null)

        assertEquals("10:07", shown.aimed)
        assertNull(shown.live)
        assertFalse(shown.struckThrough)
    }

    @Test
    fun `a cancelled departure is struck through even with no live time`() {
        val shown = presentTimes(aimed = "10:07", live = null, cancelled = true)

        assertTrue(shown.struckThrough)
        assertNull(shown.live)
    }

    @Test
    fun `a cancelled departure with a live time keeps both`() {
        val shown = presentTimes(aimed = "10:07", live = "10:12", cancelled = true)

        assertEquals("10:12", shown.live)
        assertTrue(shown.struckThrough)
    }

    @Test
    fun `seconds are trimmed from what is displayed`() {
        val shown = presentTimes(aimed = "08:00:00", live = "08:04:00")

        assertEquals("08:00", shown.aimed)
        assertEquals("08:04", shown.live)
    }

    @Test
    fun `a missing schedule does not crash`() {
        val shown = presentTimes(aimed = null, live = "10:12")

        assertEquals("", shown.aimed)
        assertEquals("10:12", shown.live)
    }
}
