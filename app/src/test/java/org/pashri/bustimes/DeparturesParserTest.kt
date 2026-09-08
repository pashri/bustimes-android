package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.parse.DeparturesParser
import org.pashri.bustimes.data.parse.DeparturesParseException

class DeparturesParserTest {

    @Test
    fun `parses a live board with both scheduled and expected columns`() {
        val board = DeparturesParser.parse(fixture("departures_0500CCITY001.html"))

        assertTrue("board should be non-empty", board.departures.isNotEmpty())
        assertTrue("fixture has an Expected column", board.hasLive)
        assertTrue("fixture has a Scheduled column", board.hasScheduled)
    }

    @Test
    fun `reads line name and service slug from the first cell`() {
        val board = DeparturesParser.parse(fixture("departures_0500CCITY001.html"))
        val first = board.departures.first()

        assertEquals("A", first.lineName)
        assertEquals("a-trumpington-the-busway-trumpington-park-and-ri-2", first.serviceSlug)
    }

    @Test
    fun `separates destination from the vehicle sub-element`() {
        val board = DeparturesParser.parse(fixture("departures_0500CCITY001.html"))
        val tracked = board.departures.first { it.vehicle != null }

        // The vehicle lives in a nested div, so the destination must come from
        // ownText or it would read "Grantchester 19576 - AE10 BWJ".
        assertFalse(tracked.destination.contains(" - "))
        assertTrue(tracked.vehicle!!.contains("-"))
    }

    @Test
    fun `tracked departures link to a journey and untracked to a trip`() {
        val board = DeparturesParser.parse(fixture("departures_0500CCITY001.html"))

        val tracked = board.departures.first { it.expectedTime != null }
        assertNotNull("a tracked departure links to /journeys/", tracked.journeyId)
        assertNull(tracked.tripId)

        val untracked = board.departures.first { it.expectedTime == null }
        assertNotNull("an untracked departure links to /trips/", untracked.tripId)
        assertNull(untracked.journeyId)
    }

    @Test
    fun `expected time differing from aimed is preserved`() {
        val board = DeparturesParser.parse(fixture("departures_0500CCITY001.html"))
        val late = board.departures.first { it.expectedTime != null }

        assertNotNull(late.aimedTime)
        assertNotNull(late.expectedTime)
        assertEquals(late.expectedTime, late.bestTime)
        assertTrue(late.isTracked)
    }

    @Test
    fun `a board with no departures parses as empty rather than failing`() {
        val board = DeparturesParser.parse(fixture("departures_tour.html"))

        assertTrue(board.departures.isEmpty())
    }

    @Test
    fun `a blank body is an empty board`() {
        val board = DeparturesParser.parse("")

        assertTrue(board.departures.isEmpty())
        assertFalse(board.hasLive)
    }

    @Test(expected = DeparturesParseException::class)
    fun `unrecognised html throws rather than returning an empty board`() {
        // Silently returning nothing would show users a blank panel forever;
        // throwing surfaces an upstream template change immediately.
        DeparturesParser.parse("<html><body><p>Service unavailable</p></body></html>")
    }

    @Test(expected = DeparturesParseException::class)
    fun `a table with no recognisable columns throws`() {
        DeparturesParser.parse(
            """<div id="departures"><table><thead><tr><th>Nope</th></tr></thead>
               <tbody><tr><td>x</td></tr></tbody></table></div>""",
        )
    }
}
